package dev.mdwriter.data.document

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.key
import dev.mdwriter.data.storage.DecodeResult
import dev.mdwriter.data.storage.DocumentStore
import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.data.storage.Hashes
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import dev.mdwriter.data.storage.StorageLimits
import dev.mdwriter.data.storage.TextCodec
import dev.mdwriter.data.storage.TextFormat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Load/save/stat with [TextCodec] + recovery + conflict detection (01 §6.3). All members run on [io].
 *
 * Deviation from 01 §6.3's sketch (recorded in STATUS T11): [save] additionally takes `format` (the round-trip
 * byte-for-byte contract needs it — [DocumentRepository] has no other way to know the original BOM/line-ending),
 * and [LoadedDocument] carries fields beyond the sketch's minimal shape.
 */
class DocumentRepository(
    private val library: LibraryRepository,
    private val recovery: RecoveryStore,
    private val io: CoroutineDispatcher,
) {
    /** sha1 of the bytes we last read or wrote, keyed by [DocKey][dev.mdwriter.data.library.DocKey] — the
     * null-`lastModified` fallback (SAF providers that don't report mtime; in-memory only, so it degenerates to
     * size-only after process death, per 01 §6.3). */
    private val knownHash = ConcurrentHashMap<String, String>()

    suspend fun load(
        ref: DocRef,
        useRecovery: Boolean = true,
    ): LoadedDocument =
        withContext(io) {
            val store = library.storeFor(ref)
            val key = ref.key()
            val stat = store.stat(ref) ?: throw StorageException(StorageError.NotFound)
            stat.size?.let { if (it > StorageLimits.MAX_OPEN_BYTES) throw StorageException(StorageError.TooLarge(it)) }
            val bytes = store.read(ref)
            val dec = TextCodec.decode(bytes) as? DecodeResult.Text ?: throw StorageException(StorageError.Encoding)
            knownHash[key.value] = Hashes.sha1Hex(bytes)
            val readOnly = (ref is DocRef.External && !ref.writable) || bytes.size > StorageLimits.READ_ONLY_BYTES
            val base =
                LoadedDocument(
                    ref = ref,
                    text = dec.text,
                    baseline = stat,
                    format = dec.format,
                    readOnly = readOnly,
                    displayName = store.displayName(ref),
                    large = bytes.size > StorageLimits.LARGE_BYTES,
                    recovered = false,
                    diskTextIfConflict = null,
                )
            val rec = if (useRecovery && !readOnly) recovery.read(key) else null
            when {
                // 01 §8: never silently drop a recovery copy.
                rec == null -> {
                    base
                }

                rec.text == dec.text -> {
                    base.also { recovery.delete(key) }
                }

                stat.lastModified == null || rec.savedAt >= stat.lastModified -> {
                    base.copy(
                        text = rec.text,
                        recovered = true,
                    )
                }

                else -> {
                    base.copy(text = rec.text, recovered = true, diskTextIfConflict = dec.text)
                }
            }
        }

    suspend fun save(
        ref: DocRef,
        text: String,
        baseline: FileStat,
        format: TextFormat,
    ): SaveResult =
        withContext(io) {
            val store = library.storeFor(ref)
            val key = ref.key()
            try {
                recovery.write(key, text) // FIRST (platform §2.8) — deleted only after a successful write below.
                val now = store.stat(ref) ?: return@withContext SaveResult.Failed(StorageError.NotFound)
                if (changedOnDisk(store, ref, baseline, now)) return@withContext SaveResult.Conflict(now)
                val bytes = TextCodec.encode(text, format)
                store.write(ref, bytes)
                knownHash[key.value] = Hashes.sha1Hex(bytes)
                recovery.delete(key)
                SaveResult.Saved(store.stat(ref) ?: FileStat(null, bytes.size.toLong()))
            } catch (e: StorageException) {
                SaveResult.Failed(e.error)
            } catch (e: IOException) {
                SaveResult.Failed(StorageError.ProviderFailure(e))
            }
        }

    suspend fun checkExternal(
        ref: DocRef,
        baseline: FileStat,
    ): ExternalCheck =
        withContext(io) {
            val store =
                try {
                    library.storeFor(ref)
                } catch (_: StorageException) {
                    return@withContext ExternalCheck.Gone
                }
            val now =
                try {
                    store.stat(ref)
                } catch (e: StorageException) {
                    if (e.error == StorageError.PermissionLost) null else throw e
                }
            if (now == null) return@withContext ExternalCheck.Gone
            if (!changedOnDisk(store, ref, baseline, now)) return@withContext ExternalCheck.Unchanged
            ExternalCheck.Changed(load(ref, useRecovery = false))
        }

    /** mtime+size when both known; else size, then a content hash vs. the last bytes we saw (SAF, platform §2.9). */
    private suspend fun changedOnDisk(
        store: DocumentStore,
        ref: DocRef,
        baseline: FileStat,
        now: FileStat,
    ): Boolean {
        if (now.lastModified != null && baseline.lastModified != null) {
            return now.lastModified != baseline.lastModified || now.size != baseline.size
        }
        if (now.size != baseline.size) return true
        val known = knownHash[ref.key().value] ?: return false
        return Hashes.sha1Hex(store.read(ref)) != known
    }
}
