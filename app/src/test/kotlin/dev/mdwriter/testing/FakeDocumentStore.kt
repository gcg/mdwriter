package dev.mdwriter.testing

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.storage.DocumentStore
import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import dev.mdwriter.data.storage.TrashToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-memory [DocumentStore] fake (only [DocRef.InternalFile] refs, keyed by their `relPath`): no real filesystem,
 * so tests can force conditions a real store can't (null `lastModified`, injected write failures).
 */
class FakeDocumentStore : DocumentStore {
    private val files = LinkedHashMap<String, ByteArray>()
    private val modified = HashMap<String, Long>()
    private var clock = 1_000_000L

    /** When true, [stat] always reports `lastModified = null` (SAF-style provider that doesn't know it). */
    var nullLastModified: Boolean = false

    private var failWritesRemaining = 0

    fun failNextWrites(n: Int) {
        failWritesRemaining = n
    }

    /** Seeds/overwrites a file directly, as if something else (this fake's own "disk") had it, without going
     * through [write] (so it doesn't consume [failNextWrites]). Also used to simulate an external change. */
    fun externalWrite(
        ref: DocRef,
        bytes: ByteArray,
    ) {
        val path = pathOf(ref)
        clock += 1_000
        files[path] = bytes
        modified[path] = clock
    }

    fun contains(ref: DocRef): Boolean = files.containsKey(pathOf(ref))

    fun bytesOf(ref: DocRef): ByteArray? = files[pathOf(ref)]

    private fun pathOf(ref: DocRef): String = (ref as DocRef.InternalFile).relPath

    /** Enough of a real listing (direct children only, files + one entry per immediate subfolder) to support
     * `LibraryRepository.newestDoc()`'s depth-first walk in tests. */
    override suspend fun list(folder: FolderRef): List<LibraryEntry> {
        val prefix = if (folder.id.isEmpty()) "" else "${folder.id}/"
        val result = mutableListOf<LibraryEntry>()
        val seenFolders = mutableSetOf<String>()
        for (path in files.keys) {
            if (!path.startsWith(prefix)) continue
            val rest = path.removePrefix(prefix)
            if (rest.isEmpty()) continue
            val slash = rest.indexOf('/')
            if (slash < 0) {
                result +=
                    LibraryEntry(
                        name = rest,
                        isFolder = false,
                        doc = DocRef.InternalFile(path),
                        folder = null,
                        lastModified = modified[path],
                        size = files[path]?.size?.toLong(),
                        excerpt = null,
                    )
            } else {
                val childName = rest.substring(0, slash)
                if (seenFolders.add(childName)) {
                    result +=
                        LibraryEntry(
                            name = childName,
                            isFolder = true,
                            doc = null,
                            folder = FolderRef(folder.location, "$prefix$childName".removeSuffix("/")),
                            lastModified = null,
                            size = null,
                            excerpt = null,
                        )
                }
            }
        }
        return result
    }

    override suspend fun read(ref: DocRef): ByteArray =
        files[pathOf(ref)] ?: throw StorageException(StorageError.NotFound)

    override suspend fun write(
        ref: DocRef,
        bytes: ByteArray,
    ) {
        if (failWritesRemaining > 0) {
            failWritesRemaining--
            throw StorageException(StorageError.ProviderFailure(RuntimeException("simulated failure")))
        }
        val path = pathOf(ref)
        clock += 1_000
        files[path] = bytes
        modified[path] = clock
    }

    override suspend fun stat(ref: DocRef): FileStat? {
        val path = pathOf(ref)
        val bytes = files[path] ?: return null
        return FileStat(if (nullLastModified) null else modified[path], bytes.size.toLong())
    }

    override suspend fun displayName(ref: DocRef): String = pathOf(ref).substringAfterLast('/')

    override suspend fun create(
        folder: FolderRef,
        displayName: String,
    ): DocRef {
        val path = if (folder.id.isEmpty()) displayName else "${folder.id}/$displayName"
        if (!files.containsKey(path)) {
            files[path] = ByteArray(0)
            clock += 1_000
            modified[path] = clock
        }
        return DocRef.InternalFile(path)
    }

    override suspend fun createFolder(
        parent: FolderRef,
        name: String,
    ): FolderRef = FolderRef(parent.location, if (parent.id.isEmpty()) name else "${parent.id}/$name")

    override suspend fun rename(
        ref: DocRef,
        newDisplayName: String,
    ): DocRef {
        val old = pathOf(ref)
        val dir = old.substringBeforeLast('/', "")
        val new = if (dir.isEmpty()) newDisplayName else "$dir/$newDisplayName"
        files[new] = files.remove(old) ?: throw StorageException(StorageError.NotFound)
        modified[new] = modified.remove(old) ?: clock
        return DocRef.InternalFile(new)
    }

    override suspend fun trash(ref: DocRef): TrashToken {
        val path = pathOf(ref)
        files.remove(path)
        modified.remove(path)
        return TrashToken(ref, path.substringAfterLast('/'), path)
    }

    override suspend fun restore(token: TrashToken): DocRef? = null

    override suspend fun move(
        ref: DocRef,
        to: FolderRef,
    ): DocRef {
        val old = pathOf(ref)
        val name = old.substringAfterLast('/')
        val new = if (to.id.isEmpty()) name else "${to.id}/$name"
        files[new] = files.remove(old) ?: throw StorageException(StorageError.NotFound)
        modified[new] = modified.remove(old) ?: clock
        return DocRef.InternalFile(new)
    }

    override fun changes(folder: FolderRef): Flow<Unit> = MutableSharedFlow()
}
