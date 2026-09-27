package dev.mdwriter.data.storage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract.Document
import android.provider.OpenableColumns
import dev.mdwriter.data.library.DocKey
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.library.key
import dev.mdwriter.data.settings.RecentList
import dev.mdwriter.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext

/**
 * [DocumentStore] over a single [DocRef.External] document opened from another app (VIEW/EDIT/SEND-stream, T18).
 * There is no folder to list ([list] is always empty) and no create/rename/move/trash — an external document is
 * someone else's file; mdwriter only reads it, writes it back in place when writable, and lets the library "Save a
 * copy" duplicate it internally.
 *
 * Reuses [SafIo] for the byte-level work ([SafIo.readAll]/[SafIo.writeWt]): those calls don't depend on a fixed
 * projection. `stat`/`displayName` use their OWN null-projection query instead of [SafIo]'s fixed
 * [SafIo.PROJECTION] — an arbitrary content provider (mail attachment, Drive, …) may not support the
 * `Document.COLUMN_*` names at all, only [OpenableColumns].
 */
class ExternalDocStore(
    private val context: Context,
    private val safIo: SafIo,
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DocumentStore {
    private fun uriOf(ref: DocRef): Uri {
        val ext = ref as? DocRef.External ?: throw IllegalArgumentException("not an external DocRef: $ref")
        return Uri.parse(ext.uri)
    }

    override suspend fun list(folder: FolderRef): List<LibraryEntry> = emptyList()

    override suspend fun read(ref: DocRef): ByteArray =
        withContext(io) {
            safIo.readAll(uriOf(ref), StorageLimits.MAX_OPEN_BYTES)
        }

    override suspend fun write(
        ref: DocRef,
        bytes: ByteArray,
    ) = withContext(io) {
        val ext = ref as? DocRef.External ?: throw IllegalArgumentException("not an external DocRef: $ref")
        if (!ext.writable) throw StorageException(StorageError.ReadOnly)
        safIo.writeWt(uriOf(ref), bytes)
    }

    override suspend fun stat(ref: DocRef): FileStat? =
        withContext(io) {
            queryRow(uriOf(ref))?.let { FileStat(it.lastModified, it.size) }
        }

    override suspend fun displayName(ref: DocRef): String =
        withContext(io) {
            val uri = uriOf(ref)
            queryRow(uri)?.displayName ?: uri.lastPathSegment ?: "Untitled.md"
        }

    override suspend fun create(
        folder: FolderRef,
        displayName: String,
    ): DocRef = throw StorageException(StorageError.ReadOnly)

    override suspend fun createFolder(
        parent: FolderRef,
        name: String,
    ): FolderRef = throw StorageException(StorageError.ReadOnly)

    override suspend fun rename(
        ref: DocRef,
        newDisplayName: String,
    ): DocRef = throw StorageException(StorageError.ReadOnly)

    override suspend fun trash(ref: DocRef): TrashToken = throw StorageException(StorageError.ReadOnly)

    override suspend fun restore(token: TrashToken): DocRef? = null

    override suspend fun move(
        ref: DocRef,
        to: FolderRef,
    ): DocRef = throw StorageException(StorageError.ReadOnly)

    override fun changes(folder: FolderRef): Flow<Unit> = emptyFlow()

    // ---- stat/displayName's own null-projection query --------------------------------------------------------

    private data class Row(
        val displayName: String?,
        val lastModified: Long?,
        val size: Long?,
    )

    /** ONE query with a NULL projection (never [SafIo.PROJECTION] — an arbitrary provider may reject/ignore an
     * unsupported column list outright). `null` if the document is gone; [SecurityException] maps to
     * [StorageError.PermissionLost]. */
    private fun queryRow(uri: Uri): Row? =
        try {
            context.contentResolver.query(uri, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                val modIdx = c.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
                Row(
                    displayName = if (nameIdx >= 0) c.getString(nameIdx) else null,
                    size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null,
                    lastModified = if (modIdx >= 0 && !c.isNull(modIdx)) c.getLong(modIdx) else null,
                )
            }
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        }

    // ---- T18: adopting a newly-opened URI / refreshing a restored one -------------------------------------------

    /**
     * Adopts [uri] from a VIEW/EDIT intent: writability is ALWAYS `checkCallingOrSelfUriPermission`, never inferred
     * from the intent's action (platform §3.3 — a VIEW intent can carry a write grant, an EDIT intent's absence of
     * one doesn't guarantee write access either). Only persists the grant when [persistable] (the intent carried
     * `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`); not every provider allows persisting, so a `SecurityException` there
     * is swallowed — the ref is still usable for this session.
     */
    suspend fun adopt(
        uri: Uri,
        persistable: Boolean,
    ): DocRef.External =
        withContext(io) {
            val writable = isWritable(uri)
            if (persistable) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, grantFlags(writable))
                    remember(DocRef.External(uri.toString(), writable).key())
                } catch (_: SecurityException) {
                    // Not every provider allows persisting a grant — the ref still works for this session.
                }
            }
            DocRef.External(uri.toString(), writable)
        }

    /** Recomputes [DocRef.External.writable] for a ref restored from settings/`SavedStateHandle`. Throws
     * [StorageException] with [StorageError.PermissionLost] if the document can no longer be `stat`ted at all. */
    suspend fun refresh(ref: DocRef.External): DocRef.External =
        withContext(io) {
            val uri = uriOf(ref)
            val reachable =
                try {
                    queryRow(uri) != null
                } catch (_: StorageException) {
                    false
                }
            if (!reachable) throw StorageException(StorageError.PermissionLost)
            DocRef.External(ref.uri, isWritable(uri))
        }

    private fun isWritable(uri: Uri): Boolean =
        context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    private fun grantFlags(writable: Boolean): Int =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)

    /** Pushes [key] to the front of [dev.mdwriter.data.settings.Settings.recentExternal] (cap 100) and releases the
     * persisted grant of whatever key was evicted. */
    private suspend fun remember(key: DocKey) {
        val (updated, evicted) = RecentList.push(settings.current().recentExternal, key.value, cap = RECENT_CAP)
        settings.update { it.copy(recentExternal = updated) }
        for (evictedKey in evicted) {
            if (!evictedKey.startsWith("x:")) continue
            val evictedUri = runCatching { Uri.parse(evictedKey.substring(2)) }.getOrNull() ?: continue
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    evictedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    private companion object {
        const val RECENT_CAP = 100
    }
}
