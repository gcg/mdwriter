package dev.mdwriter.testing

import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException

/**
 * File-backed [DocumentsProvider] for [dev.mdwriter.data.storage.SafIoTest] / [dev.mdwriter.data.storage.SafTreeStoreTest]
 * (T14 Reference D). Document id = the path relative to [root], `"root"` for the root itself.
 *
 * Deliberately reproduces two real-provider traps `SafIo`/`SafTreeStore` must survive:
 * - `openDocument(id, "w", …)` opens WITHOUT truncating (`MODE_WRITE_ONLY or MODE_CREATE` only); only `"wt"` adds
 *   `MODE_TRUNCATE`. A naive writer using plain `"w"` would corrupt a shorter overwrite.
 * - `createDocument(parentId, "text/plain", "Note.md")` produces `Note.md.txt` (a provider that "corrects" the
 *   extension to match the MIME type) — this is why `SafTreeStore.create` must pass `text/markdown` for `.md`.
 */
class TestDocumentsProvider : DocumentsProvider() {
    private lateinit var root: File

    override fun onCreate(): Boolean {
        root = File(requireNotNull(context).cacheDir, "tdp")
        root.deleteRecursively()
        root.mkdirs()
        return true
    }

    // ---- path <-> documentId -----------------------------------------------------------------------------------

    private fun fileFor(documentId: String): File = if (documentId == ROOT_DOC_ID) root else File(root, documentId)

    private fun docIdFor(file: File): String {
        if (file == root) return ROOT_DOC_ID
        return file.relativeTo(root).path.replace(File.separatorChar, '/')
    }

    private fun childId(
        parentDocumentId: String,
        name: String,
    ): String = if (parentDocumentId == ROOT_DOC_ID) name else "$parentDocumentId/$name"

    // ---- queries ------------------------------------------------------------------------------------------------

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_PROJECTION)
        cursor.newRow().apply {
            add(Root.COLUMN_ROOT_ID, "root")
            add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
            add(Root.COLUMN_FLAGS, 0)
            add(Root.COLUMN_TITLE, "Test")
            add(Root.COLUMN_ICON, 0)
        }
        return cursor
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        if (throwSecurity) throw SecurityException("permission revoked (test)")
        val cursor = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        val file = fileFor(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        addRow(cursor, documentId, file)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (throwSecurity) throw SecurityException("permission revoked (test)")
        val parent = fileFor(parentDocumentId)
        val cursor = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        parent.listFiles()?.sortedBy { it.name }?.forEach { child ->
            addRow(cursor, childId(parentDocumentId, child.name), child)
        }
        val resolver: ContentResolver? = context?.contentResolver
        if (resolver != null) {
            cursor.setNotificationUri(
                resolver,
                DocumentsContract.buildChildDocumentsUriUsingTree(TREE_URI, parentDocumentId),
            )
        }
        return cursor
    }

    private fun addRow(
        cursor: MatrixCursor,
        documentId: String,
        file: File,
    ) {
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, if (documentId == ROOT_DOC_ID) "root" else file.name)
            add(Document.COLUMN_MIME_TYPE, if (file.isDirectory) Document.MIME_TYPE_DIR else mimeOf(file.name))
            add(Document.COLUMN_LAST_MODIFIED, if (nullLastModified) null else file.lastModified())
            add(Document.COLUMN_SIZE, if (file.isDirectory) null else file.length())
            add(Document.COLUMN_FLAGS, if (file.isDirectory) flags or Document.FLAG_DIR_SUPPORTS_CREATE else flags)
        }
    }

    private fun mimeOf(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "md", "markdown" -> "text/markdown"
            "txt" -> "text/plain"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }

    // ---- content ------------------------------------------------------------------------------------------------

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val file = fileFor(documentId)
        val truncate = mode.contains("t")
        val write = mode.contains("w")
        if (write && !file.exists()) file.createNewFile()
        if (!write && !file.exists()) throw FileNotFoundException(documentId)
        var pfdMode = ParcelFileDescriptor.MODE_READ_ONLY
        if (write) {
            pfdMode = ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE
            // The trap: plain "w" does NOT truncate on this provider, only "wt" does.
            if (truncate) pfdMode = pfdMode or ParcelFileDescriptor.MODE_TRUNCATE
        }
        return ParcelFileDescriptor.open(file, pfdMode)
    }

    // ---- mutations ------------------------------------------------------------------------------------------------

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = fileFor(parentDocumentId)
        parent.mkdirs()
        // The trap: this provider "corrects" a wrong MIME/extension pairing for text/plain, appending ".txt".
        val name =
            if (mimeType == "text/plain" && !displayName.endsWith(".txt") && displayName.contains('.')) {
                "$displayName.txt"
            } else {
                displayName
            }
        val existing = parent.list()?.toSet().orEmpty()
        var finalName = name
        if (finalName.lowercase() in existing.map { it.lowercase() }) {
            val dot = finalName.lastIndexOf('.')
            var n = 1
            while (true) {
                val candidate =
                    if (dot > 0) "${finalName.substring(0, dot)} ($n)${finalName.substring(dot)}" else "$finalName ($n)"
                if (candidate.lowercase() !in existing.map { it.lowercase() }) {
                    finalName = candidate
                    break
                }
                n++
            }
        }
        val target = File(parent, finalName)
        if (mimeType == Document.MIME_TYPE_DIR) target.mkdirs() else target.createNewFile()
        notifyChildren(parentDocumentId)
        return childId(parentDocumentId, finalName)
    }

    override fun deleteDocument(documentId: String) {
        val file = fileFor(documentId)
        val parentId = parentIdOf(documentId)
        if (!file.deleteRecursively()) throw FileNotFoundException(documentId)
        notifyChildren(parentId)
    }

    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        val file = fileFor(documentId)
        val parent = file.parentFile ?: throw FileNotFoundException(documentId)
        val target = File(parent, displayName)
        if (!file.renameTo(target)) throw FileNotFoundException(documentId)
        notifyChildren(parentIdOf(documentId))
        return docIdFor(target)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val source = fileFor(sourceDocumentId)
        val targetParent = fileFor(targetParentDocumentId)
        targetParent.mkdirs()
        val target = File(targetParent, source.name)
        if (!source.renameTo(target)) throw FileNotFoundException(sourceDocumentId)
        notifyChildren(sourceParentDocumentId)
        notifyChildren(targetParentDocumentId)
        return docIdFor(target)
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean = fileFor(documentId).canonicalPath.startsWith(fileFor(parentDocumentId).canonicalPath)

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path {
        val ids = mutableListOf<String>()
        var current: File? = fileFor(childDocumentId)
        while (current != null) {
            ids.add(0, docIdFor(current))
            if (current == root) break
            current = current.parentFile
        }
        return DocumentsContract.Path(null, ids)
    }

    private fun parentIdOf(documentId: String): String {
        val file = fileFor(documentId)
        val parent = file.parentFile ?: return ROOT_DOC_ID
        return docIdFor(parent)
    }

    private fun notifyChildren(parentDocumentId: String) {
        context?.contentResolver?.notifyChange(
            DocumentsContract.buildChildDocumentsUriUsingTree(TREE_URI, parentDocumentId),
            null,
        )
    }

    companion object {
        const val AUTHORITY = "dev.mdwriter.test.documents"
        const val ROOT_DOC_ID = "root"
        val TREE_URI: Uri = DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_DOC_ID)

        private val DEFAULT_PROJECTION =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_SIZE,
                Document.COLUMN_FLAGS,
            )
        private val ROOT_PROJECTION =
            arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_DOCUMENT_ID,
                Root.COLUMN_FLAGS,
                Root.COLUMN_TITLE,
                Root.COLUMN_ICON,
            )

        const val DEFAULT_FLAGS =
            Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_RENAME or Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_MOVE

        /** Per-test override of every row's [Document.COLUMN_FLAGS] (dirs get [Document.FLAG_DIR_SUPPORTS_CREATE]
         * added on top automatically). Reset in `@Before`. */
        var flags: Int = DEFAULT_FLAGS

        /** When true, every row reports a null `COLUMN_LAST_MODIFIED` (a provider that doesn't know mtime). */
        var nullLastModified: Boolean = false

        /** When true, every query throws [SecurityException] — simulates a revoked grant. */
        var throwSecurity: Boolean = false
    }
}
