package dev.mdwriter.data.library

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dev.mdwriter.data.storage.SafIo
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException

/**
 * Persisted SAF grants for linked tree folders (01 §10 rule 5, platform §2.13: grants do not survive a
 * backup/restore, and an app can hold at most 512 total). [LibraryRepository] only ever calls [release] on an
 * explicit unlink — never automatically, even when [isGranted] turns up false on its own (that means Disconnected,
 * not "gone forever").
 */
class TreeGrants(
    private val resolver: ContentResolver,
) {
    private val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    /** Throws [StorageException] (`PermissionLost`) if the grant cannot be taken. */
    fun take(tree: Uri) {
        try {
            resolver.takePersistableUriPermission(tree, rw)
        } catch (e: SecurityException) {
            throw StorageException(StorageError.PermissionLost, e)
        }
    }

    fun release(tree: Uri) {
        runCatching { resolver.releasePersistableUriPermission(tree, rw) }
    }

    fun isGranted(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }

    /** The tree's own root document's display name (one query). Throws if it cannot be read (no grant / gone). */
    fun rootName(tree: Uri): String {
        val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return SafIo(resolver).displayName(rootUri)
    }
}
