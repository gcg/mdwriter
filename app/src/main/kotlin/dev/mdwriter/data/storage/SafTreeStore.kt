package dev.mdwriter.data.storage

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.EntryCaps
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.markdown.DocTitle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [DocumentStore] over one linked SAF tree (T14). All I/O runs on [io]. Every [DocRef]/[FolderRef] must belong to
 * [treeUri] (checked; mismatches are a caller bug, not a storage error).
 *
 * Listing issues exactly ONE `ContentResolver.query` per folder (never `DocumentFile.listFiles()`, platform §2.3);
 * excerpts are fetched afterwards, 4 at a time, capped at [MAX_EXCERPT_FILES] files per folder, cached LRU-500.
 */
class SafTreeStore(
    val treeUri: Uri,
    private val resolver: ContentResolver,
    private val safIo: SafIo,
    private val trashBin: TrashBin,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : DocumentStore {
    private val treeUriString = treeUri.toString()

    /** documentId -> parent documentId, learned from every listing/create/rename/move; the fast path for
     * [move]'s `FLAG_SUPPORTS_MOVE` branch and for change notifications. Never authoritative on its own —
     * [parentFolder] falls back to `findDocumentPath` when a ref was never listed by this instance. */
    private val parentOf = ConcurrentHashMap<String, String>()

    private val mutations =
        MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun emitChange(folderId: String) {
        mutations.tryEmit(folderId)
    }

    private fun docUri(documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    private fun childrenUri(parentDocumentId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)

    private fun requireTreeDoc(ref: DocRef): DocRef.TreeDoc =
        (ref as? DocRef.TreeDoc)?.takeIf { it.treeUri == treeUriString }
            ?: throw IllegalArgumentException("not a TreeDoc of this tree: $ref")

    private fun requireTreeFolder(folder: FolderRef): FolderRef {
        val location = folder.location as? LocationId.Tree
        require(location != null && location.treeUri == treeUriString) { "not a Tree FolderRef of this tree: $folder" }
        return folder
    }

    // ---- excerpt cache (LRU 500, "docId|lastModified|size") --------------------------------------------------

    private val excerptMutex = Mutex()
    private val excerptCache =
        object : LinkedHashMap<String, String?>(EXCERPT_CACHE_INITIAL, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean =
                size > EXCERPT_CACHE_MAX
        }
    private val excerptSemaphore = Semaphore(4)

    private suspend fun excerptFor(row: SafIo.Row): String? {
        val key = "${row.documentId}|${row.lastModified}|${row.size}"
        excerptMutex.withLock { excerptCache[key] }?.let { return it }
        if (row.size == 0L) return null
        val text =
            excerptSemaphore.withPermit {
                runCatching {
                    val bytes = safIo.readHead(docUri(row.documentId), StorageLimits.EXCERPT_BYTES)
                    val head = NoteFiles.decodeHead(bytes, bytes.size)
                    head.takeIf { it.isNotEmpty() }?.let(DocTitle::excerpt)
                }.getOrNull()
            }
        excerptMutex.withLock { excerptCache[key] = text }
        return text
    }

    // ---- DocumentStore -----------------------------------------------------------------------------------------

    override suspend fun list(folder: FolderRef): List<LibraryEntry> =
        withContext(io) {
            requireTreeFolder(folder)
            val rows =
                safIo.query(treeUri, folder.id).filter { row ->
                    !NoteFiles.isHidden(row.displayName) &&
                        (row.mimeType == Document.MIME_TYPE_DIR || NoteFiles.isSupported(row.displayName))
                }
            for (row in rows) parentOf[row.documentId] = folder.id
            val excerptable = rows.filter { it.mimeType != Document.MIME_TYPE_DIR }.take(MAX_EXCERPT_FILES)
            val excerpts =
                coroutineScope {
                    excerptable.associate { it.documentId to async { excerptFor(it) } }
                }.mapValues { it.value.await() }
            rows.map { row -> entryFor(folder, row, excerpts[row.documentId]) }.sortedWith(NoteFiles.DEFAULT_ORDER)
        }

    private fun entryFor(
        folder: FolderRef,
        row: SafIo.Row,
        excerpt: String?,
    ): LibraryEntry {
        val isDir = row.mimeType == Document.MIME_TYPE_DIR
        return if (isDir) {
            LibraryEntry(
                name = row.displayName,
                isFolder = true,
                doc = null,
                folder = FolderRef(folder.location, row.documentId),
                lastModified = null,
                size = null,
                excerpt = null,
                caps = capsOf(row.flags, isDir = true),
            )
        } else {
            LibraryEntry(
                name = row.displayName,
                isFolder = false,
                doc = DocRef.TreeDoc(treeUriString, row.documentId),
                folder = null,
                lastModified = row.lastModified,
                size = row.size,
                excerpt = excerpt,
                caps = capsOf(row.flags, isDir = false),
            )
        }
    }

    override suspend fun read(ref: DocRef): ByteArray =
        withContext(io) {
            val doc = requireTreeDoc(ref)
            safIo.readAll(docUri(doc.documentId), StorageLimits.MAX_OPEN_BYTES)
        }

    override suspend fun write(
        ref: DocRef,
        bytes: ByteArray,
    ) {
        withContext(io) {
            val doc = requireTreeDoc(ref)
            val uri = docUri(doc.documentId)
            val flags = safIo.flags(uri)
            if (flags and Document.FLAG_SUPPORTS_WRITE == 0) throw StorageException(StorageError.ReadOnly)
            safIo.writeWt(uri, bytes)
            emitChange(parentOf[doc.documentId] ?: parentIdOf(doc.documentId).orEmpty())
        }
    }

    override suspend fun stat(ref: DocRef): FileStat? =
        withContext(io) {
            val doc = requireTreeDoc(ref)
            val row = safIo.stat(docUri(doc.documentId)) ?: return@withContext null
            FileStat(row.lastModified, row.size)
        }

    override suspend fun displayName(ref: DocRef): String =
        withContext(io) {
            val doc = requireTreeDoc(ref)
            safIo.displayName(docUri(doc.documentId))
        }

    override suspend fun create(
        folder: FolderRef,
        displayName: String,
    ): DocRef =
        withContext(io) {
            requireTreeFolder(folder)
            val newUri = createDocument(folder.id, NoteFiles.mimeFor(displayName), displayName)
            val newId = DocumentsContract.getDocumentId(newUri)
            parentOf[newId] = folder.id
            emitChange(folder.id)
            DocRef.TreeDoc(treeUriString, newId)
        }

    override suspend fun createFolder(
        parent: FolderRef,
        name: String,
    ): FolderRef =
        withContext(io) {
            requireTreeFolder(parent)
            val newUri = createDocument(parent.id, Document.MIME_TYPE_DIR, name)
            val newId = DocumentsContract.getDocumentId(newUri)
            parentOf[newId] = parent.id
            emitChange(parent.id)
            FolderRef(parent.location, newId)
        }

    private fun createDocument(
        parentDocumentId: String,
        mime: String,
        name: String,
    ): Uri =
        try {
            DocumentsContract.createDocument(resolver, docUri(parentDocumentId), mime, name)
                ?: throw StorageException(StorageError.ProviderFailure(IOException("createDocument returned null")))
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        } catch (e: java.io.FileNotFoundException) {
            throw StorageException(StorageError.NotFound, e)
        }

    override suspend fun rename(
        ref: DocRef,
        newDisplayName: String,
    ): DocRef =
        withContext(io) {
            val doc = requireTreeDoc(ref)
            val oldUri = docUri(doc.documentId)
            val parentId = parentOf[doc.documentId] ?: parentIdOf(doc.documentId)
            val renamedUri =
                try {
                    DocumentsContract.renameDocument(resolver, oldUri, newDisplayName) ?: oldUri
                } catch (e: SecurityException) {
                    throw StorageException(StorageError.PermissionLost, e)
                }
            val newId = DocumentsContract.getDocumentId(renamedUri)
            parentOf.remove(doc.documentId)
            if (parentId != null) {
                parentOf[newId] = parentId
                emitChange(parentId)
            }
            DocRef.TreeDoc(treeUriString, newId)
        }

    override suspend fun move(
        ref: DocRef,
        to: FolderRef,
    ): DocRef =
        withContext(io) {
            val doc = requireTreeDoc(ref)
            requireTreeFolder(to)
            val srcUri = docUri(doc.documentId)
            val flags = safIo.flags(srcUri)
            val srcParentId = parentOf[doc.documentId] ?: parentIdOf(doc.documentId)
            if (flags and Document.FLAG_SUPPORTS_MOVE != 0 && srcParentId != null) {
                val moved =
                    runCatching {
                        DocumentsContract.moveDocument(resolver, srcUri, docUri(srcParentId), docUri(to.id))
                    }.getOrNull()
                if (moved != null) {
                    val newId = DocumentsContract.getDocumentId(moved)
                    parentOf.remove(doc.documentId)
                    parentOf[newId] = to.id
                    emitChange(srcParentId)
                    emitChange(to.id)
                    return@withContext DocRef.TreeDoc(treeUriString, newId)
                }
            }
            // Fallback: copy + trash (provider doesn't support moveDocument, or the fast path failed).
            val bytes = safIo.readAll(srcUri, StorageLimits.MAX_OPEN_BYTES)
            val name = safIo.displayName(srcUri)
            val existingNames = safIo.query(treeUri, to.id).map { it.displayName }
            val uniqueName = NoteFiles.uniqueName(name, existingNames)
            val newRef = create(to, uniqueName) as DocRef.TreeDoc
            write(newRef, bytes)
            trash(ref)
            newRef
        }

    override suspend fun trash(ref: DocRef): TrashToken =
        withContext(io) {
            val doc = requireTreeDoc(ref)
            val uri = docUri(doc.documentId)
            val name = runCatching { safIo.displayName(uri) }.getOrDefault(doc.documentId)
            val bytes = runCatching { safIo.readAll(uri, StorageLimits.MAX_OPEN_BYTES) }.getOrNull()
            if (bytes != null) {
                trashBin.copyIn(
                    name,
                    bytes,
                    mapOf(
                        "source" to "tree",
                        "treeUri" to treeUriString,
                        "documentId" to doc.documentId,
                        "deletedAt" to clock().toString(),
                        "originalRelPath" to "",
                    ),
                )
            }
            val flags = safIo.flags(uri)
            // trashDocument returns the (possibly renamed) Uri of the now-trashed document, not a boolean.
            val trashedViaApi =
                Build.VERSION.SDK_INT >= 37 && flags and SafIo.FLAG_SUPPORTS_TRASH_37 != 0 &&
                    runCatching { DocumentsContract.trashDocument(resolver, uri) }.getOrNull() != null
            if (!trashedViaApi && !DocumentsContract.deleteDocument(resolver, uri)) {
                throw StorageException(StorageError.ProviderFailure(IOException("delete failed for $uri")))
            }
            val parentId = parentOf.remove(doc.documentId)
            if (parentId != null) emitChange(parentId)
            TrashToken(ref, name, trashId = "")
        }

    /** SAF's own trash revokes our grant on the trashed subtree (platform §2.3) — a store-level restore is never
     * possible. Undo relies entirely on the app-side safety copy [trash] wrote via [trashBin]. */
    override suspend fun restore(token: TrashToken): DocRef? = null

    override fun changes(folder: FolderRef): Flow<Unit> =
        merge(
            mutations.filter { it == folder.id }.map { },
            callbackFlow {
                val children = childrenUri(folder.id)
                val cursor = runCatching { resolver.query(children, SafIo.PROJECTION, null, null) }.getOrNull()
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                cursor?.registerContentObserver(observer)
                resolver.registerContentObserver(children, true, observer)
                awaitClose {
                    cursor?.unregisterContentObserver(observer)
                    resolver.unregisterContentObserver(observer)
                    cursor?.close()
                }
            }.conflate(),
        )

    // ---- extras used by LibraryRepository ---------------------------------------------------------------------

    /** Cached parent lookup, falling back to `findDocumentPath` (an extra IO round-trip) when this instance never
     * listed [documentId] itself. */
    suspend fun parentFolder(documentId: String): FolderRef? =
        withContext(io) {
            val id = parentOf[documentId] ?: parentIdOf(documentId)
            id?.let { FolderRef(LocationId.Tree(treeUriString), it) }
        }

    /** Caps of a folder document itself (for gating the "new note"/"new folder" affordances on the CURRENT
     * folder, which isn't otherwise represented as a [LibraryEntry]). */
    suspend fun folderCaps(documentId: String): EntryCaps =
        withContext(io) {
            val flags = safIo.stat(docUri(documentId))?.flags ?: 0
            capsOf(flags, isDir = true)
        }

    private fun parentIdOf(documentId: String): String? =
        runCatching {
            val path = DocumentsContract.findDocumentPath(resolver, docUri(documentId))
            path?.path?.let { ids -> if (ids.size >= 2) ids[ids.size - 2] else null }
        }.getOrNull()

    private fun capsOf(
        flags: Int,
        isDir: Boolean,
    ): EntryCaps =
        EntryCaps(
            write = flags and Document.FLAG_SUPPORTS_WRITE != 0,
            rename = flags and Document.FLAG_SUPPORTS_RENAME != 0,
            delete = flags and Document.FLAG_SUPPORTS_DELETE != 0 || flags and SafIo.FLAG_SUPPORTS_TRASH_37 != 0,
            createChildren = isDir && flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0,
        )

    private companion object {
        const val EXCERPT_CACHE_INITIAL = 32
        const val EXCERPT_CACHE_MAX = 500
        const val MAX_EXCERPT_FILES = 300
    }
}
