package dev.mdwriter.ui.preview

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.parentPath
import java.io.File

/**
 * Serves a relative image next to the currently-open note, at `/doc/<path>` (T16, Scope "in": relative images).
 * Registered on `WebViewAssetLoader` under that prefix; [handle] runs on a WebView background thread (never read
 * Compose state here — [currentDoc] is a lambda reading a `@Volatile` field on [PreviewWebViewHolder]).
 *
 * [ImagePath.walk]'s own "never pop past base[0]" rule already refuses a path that tries to escape the note's
 * directory; the internal branch also re-checks the resolved file's canonical path against the library root as
 * defence in depth (a symlink, or a future change to [ImagePath], must not silently regress this).
 */
class DocumentImagePathHandler(
    private val context: Context,
    private val libraryRoot: File,
    private val currentDoc: () -> DocRef?,
) : WebViewAssetLoader.PathHandler {
    override fun handle(path: String): WebResourceResponse? {
        val tokens = ImagePath.split(path) ?: return null
        val name = tokens.lastOrNull() ?: return null
        val mime = ImagePath.imageMime(name) ?: return null
        return when (val doc = currentDoc()) {
            is DocRef.InternalFile -> {
                internal(doc, tokens)?.let { WebResourceResponse(mime, null, it.inputStream()) }
            }

            is DocRef.TreeDoc -> {
                tree(doc, tokens)?.let { uri ->
                    context.contentResolver.openInputStream(uri)?.let { WebResourceResponse(mime, null, it) }
                }
            }

            else -> {
                null
            } // External: no directory to resolve a relative image against.
        }
    }

    private fun internal(
        doc: DocRef.InternalFile,
        tokens: List<String>,
    ): File? {
        val root = libraryRoot.canonicalFile
        val base = mutableListOf(root)
        var dir = root
        for (segment in doc.parentPath.split('/')) {
            if (segment.isEmpty()) continue
            dir = File(dir, segment)
            base.add(dir)
        }
        val hit =
            ImagePath.walk(base, tokens) { parent, childName ->
                File(parent, childName).takeIf { it.isFile || it.isDirectory }
            } ?: return null
        val canonical = hit.canonicalFile
        val rootPrefix = root.canonicalPath + File.separator
        return canonical.takeIf { it.isFile && it.canonicalPath.startsWith(rootPrefix) }
    }

    private fun tree(
        doc: DocRef.TreeDoc,
        tokens: List<String>,
    ): Uri? =
        runCatching {
            val treeUri = Uri.parse(doc.treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.documentId)
            val path = DocumentsContract.findDocumentPath(context.contentResolver, docUri) ?: return@runCatching null
            val ids = path.path ?: return@runCatching null
            if (ids.isEmpty()) return@runCatching null
            val base = ids.dropLast(1)
            if (base.isEmpty()) return@runCatching null
            val resultId =
                ImagePath.walk(base, tokens) { parentId, childName ->
                    childDocumentId(treeUri, parentId, childName)
                } ?: return@runCatching null
            DocumentsContract.buildDocumentUriUsingTree(treeUri, resultId)
        }.getOrNull()

    /** One `ContentResolver.query` per path segment — the same 4-arg overload T14's `SafIo` uses (the classic
     * 5-arg overload throws `UnsupportedOperationException` against a real `DocumentsProvider`, see T14 STATUS). */
    private fun childDocumentId(
        treeUri: Uri,
        parentDocumentId: String,
        name: String,
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection =
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(childrenUri, projection, Bundle(), null)?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == name) return c.getString(idIdx)
            }
        }
        return null
    }
}
