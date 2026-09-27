package dev.mdwriter.data.storage

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.system.Os
import android.system.OsConstants
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

/**
 * Low-level SAF calls (01 §6.3, §10 rule 15). Reused by T14's [SafTreeStore] and T18's `ExternalDocStore`. Every
 * member issues exactly ONE `ContentResolver` call per logical operation — never `DocumentFile.listFiles()`
 * (platform §2.3: that is 1 + 3N IPCs). Providers may ignore `sortOrder`; callers sort client-side.
 */
class SafIo(
    private val resolver: ContentResolver,
) {
    /** One row of [PROJECTION] (a child listing row, or a single document's own `stat`). */
    data class Row(
        val documentId: String,
        val displayName: String,
        val mimeType: String?,
        val lastModified: Long?,
        val size: Long?,
        val flags: Int,
    )

    /** ONE `query` for every child of [parentDocumentId] under [tree]. Empty (not an exception) if the query itself
     * returns null (some providers do this for a folder that just vanished). */
    fun query(
        tree: Uri,
        parentDocumentId: String,
    ): List<Row> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocumentId)
        return queryRows(children)
    }

    // The 4-arg (Bundle, CancellationSignal) overload, not the classic 5-arg (selection/selectionArgs/sortOrder)
    // one: DocumentsProvider's own legacy 5-arg query() throws UnsupportedOperationException since Android O
    // ("Pre-Android-O query format not supported") — every provider we talk to is a DocumentsProvider.
    private fun queryRows(uri: Uri): List<Row> =
        try {
            resolver.query(uri, PROJECTION, null, null)?.use { c ->
                val out = ArrayList<Row>(c.count)
                while (c.moveToNext()) out += rowAt(c)
                out
            } ?: emptyList()
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        }

    /** A single document's own row (not its children). `null` if it no longer exists. */
    fun stat(uri: Uri): Row? =
        try {
            resolver.query(uri, PROJECTION, null, null)?.use { c -> if (c.moveToFirst()) rowAt(c) else null }
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        }

    private fun rowAt(c: Cursor): Row {
        val idIdx = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
        val nameIdx = c.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
        val mimeIdx = c.getColumnIndex(Document.COLUMN_MIME_TYPE)
        val modIdx = c.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
        val sizeIdx = c.getColumnIndex(Document.COLUMN_SIZE)
        val flagsIdx = c.getColumnIndex(Document.COLUMN_FLAGS)
        return Row(
            documentId = c.getString(idIdx),
            displayName = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else "",
            mimeType = if (mimeIdx >= 0) c.getString(mimeIdx) else null,
            lastModified = if (modIdx >= 0 && !c.isNull(modIdx)) c.getLong(modIdx) else null,
            size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null,
            flags = if (flagsIdx >= 0 && !c.isNull(flagsIdx)) c.getInt(flagsIdx) else 0,
        )
    }

    fun displayName(uri: Uri): String = stat(uri)?.displayName ?: throw StorageException(StorageError.NotFound)

    fun flags(uri: Uri): Int = stat(uri)?.flags ?: 0

    /** `openInputStream` capped at [maxBytes] (checked against `COLUMN_SIZE` when known; the read itself is not
     * further limited if the provider misreports size). */
    fun readAll(
        uri: Uri,
        maxBytes: Long,
    ): ByteArray {
        val row = stat(uri)
        row?.size?.let { if (it > maxBytes) throw StorageException(StorageError.TooLarge(it)) }
        return try {
            resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw StorageException(StorageError.NotFound)
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        } catch (e: FileNotFoundException) {
            throw StorageException(StorageError.NotFound, e)
        }
    }

    /** Reads only the first [maxBytes] of [uri]'s content (never the whole file) — used for excerpts. */
    fun readHead(
        uri: Uri,
        maxBytes: Int,
    ): ByteArray =
        try {
            resolver.openInputStream(uri)?.use { it.readNBytes(maxBytes) }
                ?: throw StorageException(StorageError.NotFound)
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        } catch (e: FileNotFoundException) {
            throw StorageException(StorageError.NotFound, e)
        }

    /**
     * `"wt"` (truncate) with a `"w"` fallback (some providers reject `"wt"` outright); write; `truncate` + `fsync`
     * if the fd is a regular file; then re-query `COLUMN_SIZE` to verify the write actually landed (01 §10 rule 15
     * — plain `"w"` may not truncate, silently corrupting a shorter overwrite).
     */
    fun writeWt(
        uri: Uri,
        bytes: ByteArray,
    ) {
        val pfd =
            try {
                openWt(uri)
            } catch (e: SecurityException) {
                throw StorageException(StorageError.PermissionLost, e)
            } catch (e: FileNotFoundException) {
                throw StorageException(StorageError.NotFound, e)
            }
        pfd.use { p ->
            FileOutputStream(p.fileDescriptor).use { out ->
                out.write(bytes)
                out.flush()
                val regular =
                    runCatching { OsConstants.S_ISREG(Os.fstat(p.fileDescriptor).st_mode) }.getOrDefault(false)
                if (regular) {
                    out.channel.truncate(bytes.size.toLong())
                    runCatching { Os.fsync(p.fileDescriptor) }
                }
            }
        }
        val size = stat(uri)?.size
        if (size != null && size != bytes.size.toLong()) {
            throw StorageException(
                StorageError.ProviderFailure(IOException("size mismatch: wrote ${bytes.size}, provider reports $size")),
            )
        }
    }

    private fun openWt(uri: Uri): ParcelFileDescriptor {
        val wt =
            try {
                resolver.openFileDescriptor(uri, "wt")
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: UnsupportedOperationException) {
                null
            }
        return wt ?: resolver.openFileDescriptor(uri, "w")
            ?: throw StorageException(StorageError.ProviderFailure(IOException("null pfd for $uri")))
    }

    companion object {
        val PROJECTION =
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_SIZE,
                Document.COLUMN_FLAGS,
            )

        // Document.FLAG_SUPPORTS_TRASH is API 37; minSdk is 36. A literal constant avoids lint's InlinedApi
        // (referencing the real field would need @RequiresApi plumbing all the way through a `val` initializer).
        const val FLAG_SUPPORTS_TRASH_37 = 0x10000
    }
}
