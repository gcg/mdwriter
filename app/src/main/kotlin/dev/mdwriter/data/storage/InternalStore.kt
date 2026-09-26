package dev.mdwriter.data.storage

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.library.childPath
import dev.mdwriter.data.library.fileName
import dev.mdwriter.data.library.parentPath
import dev.mdwriter.markdown.DocTitle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Files

/**
 * [DocumentStore] over `filesDir/library`. All I/O runs on [io]; every [DocRef]/[FolderRef] that is not
 * [LocationId.Internal] is rejected with [IllegalArgumentException].
 */
class InternalStore(
    val root: File, // filesDir/library (T11 passes it; created if missing)
    private val trash: TrashBin, // filesDir/.trash
    private val writer: AtomicWriter = AtomicWriter(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : DocumentStore {
    init {
        if (!root.isDirectory) root.mkdirs()
    }

    private val mutations =
        MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun changes(folder: FolderRef): Flow<Unit> = mutations.filter { it == folder.id }.map { }

    private fun emitChange(folderId: String) {
        mutations.tryEmit(folderId)
    }

    // ---- path safety -------------------------------------------------------------------------------------------

    private fun fileFor(relPath: String): File {
        if (relPath.isEmpty()) return root
        require(!relPath.startsWith("/") && !relPath.startsWith("\\")) { "leading separator: $relPath" }
        for (seg in relPath.split('/')) {
            require(seg.isNotEmpty()) { "empty path segment: $relPath" }
            require(seg != "." && seg != "..") { "illegal path segment: $relPath" }
            require(!seg.contains('\\')) { "illegal separator in segment: $relPath" }
        }
        val candidate = File(root, relPath)
        val rootCanonical = root.canonicalFile.path
        val candidateCanonical = candidate.canonicalFile.path
        require(candidateCanonical == rootCanonical || candidateCanonical.startsWith(rootCanonical + File.separator)) {
            "path traversal rejected: $relPath"
        }
        return candidate
    }

    private fun requireInternalFile(ref: DocRef): DocRef.InternalFile =
        ref as? DocRef.InternalFile ?: throw IllegalArgumentException("not an internal DocRef: $ref")

    private fun requireInternalFolder(folder: FolderRef): FolderRef {
        require(folder.location == LocationId.Internal) { "not an internal FolderRef: $folder" }
        return folder
    }

    // ---- DocumentStore -------------------------------------------------------------------------------------------

    override suspend fun list(folder: FolderRef): List<LibraryEntry> =
        withContext(io) {
            requireInternalFolder(folder)
            val dir = fileFor(folder.id)
            val children = dir.listFiles() ?: throw StorageException(StorageError.NotFound)
            children
                .asSequence()
                .filter { !NoteFiles.isHidden(it.name) }
                .mapNotNull { f -> entryFor(folder, f) }
                .sortedWith(NoteFiles.DEFAULT_ORDER)
                .toList()
        }

    private fun entryFor(
        folder: FolderRef,
        f: File,
    ): LibraryEntry? =
        when {
            f.isDirectory -> {
                LibraryEntry(
                    name = f.name,
                    isFolder = true,
                    doc = null,
                    folder = FolderRef(LocationId.Internal, folder.childPath(f.name)),
                    lastModified = null,
                    size = null,
                    excerpt = null,
                )
            }

            f.isFile && NoteFiles.isSupported(f.name) -> {
                LibraryEntry(
                    name = f.name,
                    isFolder = false,
                    doc = DocRef.InternalFile(folder.childPath(f.name)),
                    folder = null,
                    lastModified = f.lastModified(),
                    size = f.length(),
                    excerpt = readExcerpt(f),
                )
            }

            else -> {
                null
            }
        }

    private fun readExcerpt(file: File): String? {
        val len = file.length()
        if (len == 0L) return null
        val toRead = minOf(StorageLimits.EXCERPT_BYTES.toLong(), len).toInt()
        val bytes = FileInputStream(file).use { it.readNBytes(toRead) }
        val text = NoteFiles.decodeHead(bytes, bytes.size)
        if (text.isEmpty()) return null
        return DocTitle.excerpt(text)
    }

    override suspend fun read(ref: DocRef): ByteArray =
        withContext(io) {
            val file = fileFor(requireInternalFile(ref).relPath)
            if (!file.isFile) throw StorageException(StorageError.NotFound)
            val len = file.length()
            if (len > StorageLimits.MAX_OPEN_BYTES) throw StorageException(StorageError.TooLarge(len))
            file.readBytes()
        }

    override suspend fun write(
        ref: DocRef,
        bytes: ByteArray,
    ) {
        withContext(io) {
            val internalRef = requireInternalFile(ref)
            val file = fileFor(internalRef.relPath)
            writer.write(file, bytes)
            emitChange(internalRef.parentPath)
        }
    }

    override suspend fun stat(ref: DocRef): FileStat? =
        withContext(io) {
            val file = fileFor(requireInternalFile(ref).relPath)
            if (!file.isFile) null else FileStat(file.lastModified(), file.length())
        }

    override suspend fun displayName(ref: DocRef): String =
        withContext(io) {
            requireInternalFile(ref).fileName
        }

    override suspend fun create(
        folder: FolderRef,
        displayName: String,
    ): DocRef =
        withContext(io) {
            requireInternalFolder(folder)
            val dir = fileFor(folder.id)
            ensureDir(dir)
            var name = NoteFiles.uniqueName(displayName, dir.list()?.toList().orEmpty())
            while (!File(dir, name).createNewFile()) {
                name = NoteFiles.uniqueName(displayName, dir.list()?.toList().orEmpty())
            }
            emitChange(folder.id)
            DocRef.InternalFile(folder.childPath(name))
        }

    override suspend fun createFolder(
        parent: FolderRef,
        name: String,
    ): FolderRef =
        withContext(io) {
            requireInternalFolder(parent)
            val dir = fileFor(parent.id)
            ensureDir(dir)
            var finalName = NoteFiles.uniqueName(name, dir.list()?.toList().orEmpty())
            while (!File(dir, finalName).mkdir()) {
                finalName = NoteFiles.uniqueName(name, dir.list()?.toList().orEmpty())
            }
            emitChange(parent.id)
            FolderRef(LocationId.Internal, parent.childPath(finalName))
        }

    override suspend fun rename(
        ref: DocRef,
        newDisplayName: String,
    ): DocRef =
        withContext(io) {
            require(!newDisplayName.contains('/') && newDisplayName.isNotBlank()) { "invalid name: $newDisplayName" }
            val internalRef = requireInternalFile(ref)
            val file = fileFor(internalRef.relPath)
            if (!file.exists()) throw StorageException(StorageError.NotFound)
            val dir = file.parentFile ?: throw IOException("no parent for $file")
            val siblings = dir.list()?.toList().orEmpty()
            val caseOnlyRename = newDisplayName.equals(file.name, ignoreCase = true) && newDisplayName != file.name
            val finalName =
                if (caseOnlyRename) {
                    newDisplayName
                } else {
                    NoteFiles.uniqueName(newDisplayName, siblings.filterNot { it.equals(file.name, ignoreCase = true) })
                }
            val target = File(dir, finalName)
            Files.move(file.toPath(), target.toPath())
            val newRelPath = if (internalRef.parentPath.isEmpty()) finalName else "${internalRef.parentPath}/$finalName"
            emitChange(internalRef.parentPath)
            DocRef.InternalFile(newRelPath)
        }

    override suspend fun move(
        ref: DocRef,
        to: FolderRef,
    ): DocRef =
        withContext(io) {
            val internalRef = requireInternalFile(ref)
            requireInternalFolder(to)
            val file = fileFor(internalRef.relPath)
            if (!file.exists()) throw StorageException(StorageError.NotFound)
            val targetDir = fileFor(to.id)
            ensureDir(targetDir)
            val finalName = NoteFiles.uniqueName(file.name, targetDir.list()?.toList().orEmpty())
            val target = File(targetDir, finalName)
            Files.move(file.toPath(), target.toPath())
            emitChange(internalRef.parentPath)
            emitChange(to.id)
            DocRef.InternalFile(to.childPath(finalName))
        }

    override suspend fun trash(ref: DocRef): TrashToken =
        withContext(io) {
            val internalRef = requireInternalFile(ref)
            val file = fileFor(internalRef.relPath)
            if (!file.exists()) throw StorageException(StorageError.NotFound)
            val name = file.name
            val id =
                trash.moveIn(
                    file,
                    mapOf(
                        "source" to "internal",
                        "originalRelPath" to internalRef.relPath,
                        "displayName" to name,
                        "deletedAt" to clock().toString(),
                    ),
                )
            emitChange(internalRef.parentPath)
            TrashToken(ref, name, id)
        }

    override suspend fun restore(token: TrashToken): DocRef? =
        withContext(io) {
            val entry = trash.get(token.trashId) ?: return@withContext null
            val originalRelPath = entry.meta["originalRelPath"] ?: return@withContext null
            val parentRel = originalRelPath.substringBeforeLast('/', missingDelimiterValue = "")
            val targetDir = fileFor(parentRel)
            ensureDir(targetDir)
            val desiredName = originalRelPath.substringAfterLast('/')
            val finalName = NoteFiles.uniqueName(desiredName, targetDir.list()?.toList().orEmpty())
            val target = File(targetDir, finalName)
            Files.move(entry.payload.toPath(), target.toPath())
            trash.remove(token.trashId)
            val newRelPath = if (parentRel.isEmpty()) finalName else "$parentRel/$finalName"
            emitChange(parentRel)
            DocRef.InternalFile(newRelPath)
        }

    private fun ensureDir(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw StorageException(StorageError.ProviderFailure(IOException("cannot create directory: $dir")))
        }
    }

    // ---- extras used by T11 --------------------------------------------------------------------------------------

    /** Purges trash entries older than [maxAgeMillis] (default 30 days) and stray temp files left by a crash. */
    suspend fun purgeTrash(maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000): Int =
        withContext(io) {
            val trashDeleted = trash.purgeOlderThan(clock() - maxAgeMillis)
            val tmpCutoff = clock() - 60 * 60 * 1000L // 1 hour
            var tmpDeleted = 0
            root.walkTopDown().forEach { f ->
                if (f.isFile && f.name.startsWith(".") && f.name.endsWith(".tmp") && f.lastModified() < tmpCutoff) {
                    if (f.delete()) tmpDeleted++
                }
            }
            trashDeleted + tmpDeleted
        }

    /** Recursive, skips hidden files/folders. */
    suspend fun newestDocument(): DocRef.InternalFile? =
        withContext(io) {
            var best: File? = null
            root
                .walkTopDown()
                .onEnter { dir -> dir == root || !NoteFiles.isHidden(dir.name) }
                .filter { it.isFile && !NoteFiles.isHidden(it.name) && NoteFiles.isSupported(it.name) }
                .forEach { f ->
                    val current = best
                    if (current == null || f.lastModified() > current.lastModified()) best = f
                }
            best?.let { f ->
                DocRef.InternalFile(f.relativeTo(root).path.replace(File.separatorChar, '/'))
            }
        }
}
