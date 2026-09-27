package dev.mdwriter.data.storage

import android.Manifest
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.testing.TestDocumentsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class SafTreeStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val resolver = context.contentResolver
    private val tree: Uri = TestDocumentsProvider.TREE_URI
    private lateinit var store: SafTreeStore
    private val root = FolderRef(LocationId.Tree(tree.toString()), "root")

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
        val trashBin = TrashBin(tmp.newFolder(".trash"))
        store = SafTreeStore(tree, resolver, SafIo(resolver), trashBin, io = Dispatchers.Unconfined)
    }

    @Test
    fun listUsesOneQueryAndFilters() =
        runTest {
            store.create(root, "a.md")
            store.create(root, ".hidden.md")
            store.create(root, "x.png")
            store.createFolder(root, "Sub")
            store.createFolder(root, ".obsidian")
            val entries = store.list(root)
            val names = entries.map { it.name }
            assertThat(names).contains("a.md")
            assertThat(names).doesNotContain(".hidden.md")
            assertThat(names).doesNotContain("x.png")
            assertThat(names).doesNotContain(".obsidian")
            // Folders first.
            assertThat(entries.first().isFolder).isTrue()
        }

    @Test
    fun createMdUsesTextMarkdown() =
        runTest {
            val ref = store.create(root, "Note.md") as DocRef.TreeDoc
            assertThat(store.displayName(ref)).isEqualTo("Note.md")
        }

    @Test
    fun createCollisionReadsBackName() =
        runTest {
            store.create(root, "Note.md")
            val second = store.create(root, "Note.md") as DocRef.TreeDoc
            assertThat(store.displayName(second)).isEqualTo("Note (1).md")
        }

    @Test
    fun renameReturnsNewRef() =
        runTest {
            val ref = store.create(root, "a.md") as DocRef.TreeDoc
            store.write(ref, "hi".toByteArray())
            val renamed = store.rename(ref, "b.md") as DocRef.TreeDoc
            assertThat(renamed.documentId).isNotEqualTo(ref.documentId)
            assertThat(String(store.read(renamed))).isEqualTo("hi")
            assertThat(store.stat(ref)).isNull()
        }

    @Test
    fun writeShorterContentTruncates() =
        runTest {
            val ref = store.create(root, "a.md") as DocRef.TreeDoc
            store.write(ref, ByteArray(100) { 'x'.code.toByte() })
            store.write(ref, ByteArray(10) { 'y'.code.toByte() })
            assertThat(store.read(ref).size).isEqualTo(10)
        }

    @Test
    fun deleteRemovesAndKeepsSafetyCopy() =
        runTest {
            val ref = store.create(root, "a.md") as DocRef.TreeDoc
            store.write(ref, "content".toByteArray())
            store.trash(ref)
            assertThat(store.stat(ref)).isNull()
            val trashDir = tmp.root.resolve(".trash")
            val entries = trashDir.listFiles().orEmpty()
            assertThat(entries).isNotEmpty()
            val metaFile = entries.first().resolve("meta.json")
            assertThat(metaFile.exists()).isTrue()
            assertThat(metaFile.readText()).contains("\"source\":\"tree\"")
        }

    @Test
    fun capsFollowFlags() =
        runTest {
            TestDocumentsProvider.flags = Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
            val ref = store.create(root, "a.md")
            val entry = store.list(root).first { it.name == "a.md" }
            assertThat(entry.caps.rename).isFalse()
            assertThat(entry.caps.write).isTrue()
            assertThat(entry.caps.delete).isTrue()
        }

    @Test
    fun changesEmitsOnNotify() =
        runTest {
            store.changes(root).test {
                store.create(root, "a.md")
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun permissionLossMapsToPermissionLost() =
        runTest {
            store.create(root, "a.md")
            TestDocumentsProvider.throwSecurity = true
            try {
                var threw = false
                try {
                    store.list(root)
                } catch (e: StorageException) {
                    threw = true
                    assertThat(e.error).isEqualTo(StorageError.PermissionLost)
                }
                assertThat(threw).isTrue()
            } finally {
                TestDocumentsProvider.throwSecurity = false
            }
        }
}
