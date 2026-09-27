package dev.mdwriter.data.storage

import android.Manifest
import android.content.pm.ProviderInfo
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.testing.TestDocumentsProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class SafIoTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val resolver = context.contentResolver
    private lateinit var safIo: SafIo
    private val tree = TestDocumentsProvider.TREE_URI

    @Before
    fun setUp() {
        TestDocumentsProvider.flags = TestDocumentsProvider.DEFAULT_FLAGS
        TestDocumentsProvider.nullLastModified = false
        Robolectric
            .buildContentProvider(TestDocumentsProvider::class.java)
            .create(
                ProviderInfo().apply {
                    authority = TestDocumentsProvider.AUTHORITY
                    exported = true
                    grantUriPermissions = true
                    readPermission = Manifest.permission.MANAGE_DOCUMENTS
                    writePermission = Manifest.permission.MANAGE_DOCUMENTS
                },
            )
        safIo = SafIo(resolver)
    }

    private fun rootDocUri() =
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    private fun create(name: String): android.net.Uri {
        val newUri =
            requireNotNull(DocumentsContract.createDocument(resolver, rootDocUri(), NoteFiles.mimeFor(name), name))
        return newUri
    }

    @Test
    fun writeWtThenReadRoundTrips() {
        val uri = create("a.md")
        safIo.writeWt(uri, "hello".toByteArray())
        assertThat(String(safIo.readAll(uri, StorageLimits.MAX_OPEN_BYTES))).isEqualTo("hello")
    }

    @Test
    fun writeShorterContentTruncates() {
        val uri = create("a.md")
        safIo.writeWt(uri, ByteArray(100) { 'x'.code.toByte() })
        safIo.writeWt(uri, ByteArray(10) { 'y'.code.toByte() })
        val bytes = safIo.readAll(uri, StorageLimits.MAX_OPEN_BYTES)
        assertThat(bytes.size).isEqualTo(10)
        assertThat(bytes.all { it == 'y'.code.toByte() }).isTrue()
    }

    @Test
    fun displayNameReadsBack() {
        val uri = create("Note.md")
        assertThat(safIo.displayName(uri)).isEqualTo("Note.md")
    }

    @Test
    fun flagsReflectProviderRow() {
        val uri = create("a.md")
        assertThat(safIo.flags(uri) and android.provider.DocumentsContract.Document.FLAG_SUPPORTS_WRITE)
            .isNotEqualTo(0)
    }

    @Test
    fun readHeadNeverReadsPastMax() {
        val uri = create("a.md")
        safIo.writeWt(uri, ByteArray(5000) { 'z'.code.toByte() })
        val head = safIo.readHead(uri, 2048)
        assertThat(head.size).isEqualTo(2048)
    }

    @Test
    fun statOfMissingRowIsNull() {
        val uri = create("gone.md")
        safIo.writeWt(uri, "x".toByteArray())
        DocumentsContract.deleteDocument(resolver, uri)
        assertThat(safIo.stat(uri)).isNull()
    }
}
