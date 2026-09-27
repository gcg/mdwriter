package dev.mdwriter.data.document

import android.Manifest
import android.content.pm.ProviderInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.InternalStore
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.SafIo
import dev.mdwriter.data.storage.SafTreeStore
import dev.mdwriter.data.storage.TextCodec
import dev.mdwriter.data.storage.TextFormat
import dev.mdwriter.data.storage.TrashBin
import dev.mdwriter.testing.TestDocumentsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Acceptance 4: with a null-`lastModified` provider (`nullLastModified = true`), [DocumentRepository]'s conflict
 * detection falls back to size, then a content hash of the last bytes it saw (01 §6.3 / T11). */
@RunWith(AndroidJUnit4::class)
class DocumentRepositorySafTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val resolver = context.contentResolver
    private val tree = TestDocumentsProvider.TREE_URI

    private lateinit var scopeJob: Job
    private lateinit var settingsStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private lateinit var treeStore: SafTreeStore
    private lateinit var library: LibraryRepository
    private lateinit var documents: DocumentRepository
    private lateinit var recovery: RecoveryStore

    private val root = FolderRef(LocationId.Tree(TestDocumentsProvider.TREE_URI.toString()), "root")

    @Before
    fun setUp() {
        TestDocumentsProvider.flags = TestDocumentsProvider.DEFAULT_FLAGS
        TestDocumentsProvider.nullLastModified = true
        TestDocumentsProvider.throwSecurity = false
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
        scopeJob = Job()
        settingsStore =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.Unconfined + scopeJob),
                produceFile = { tmp.newFile("settings.preferences_pb") },
            )
        settings = SettingsRepository(settingsStore)
        val internalStore = InternalStore(tmp.newFolder("library"), TrashBin(tmp.newFolder(".trash")))
        val safIo = SafIo(resolver)
        treeStore =
            SafTreeStore(tree, resolver, safIo, TrashBin(tmp.newFolder(".trash-tree")), io = Dispatchers.Unconfined)
        library =
            LibraryRepository(
                internalStore = internalStore,
                settings = settings,
                io = Dispatchers.Unconfined,
                treeStoreFactory = { treeStore },
            )
        recovery = RecoveryStore(tmp.newFolder("recovery"), io = Dispatchers.Unconfined)
        documents = DocumentRepository(library, recovery, Dispatchers.Unconfined)
    }

    private suspend fun createDoc(text: String): DocRef.TreeDoc {
        val ref = treeStore.create(root, "a.md") as DocRef.TreeDoc
        treeStore.write(ref, TextCodec.encode(text, TextFormat.DEFAULT))
        return ref
    }

    @Test
    fun sameSizeExternalChangeIsConflict() =
        runTest {
            val ref = createDoc("AAAA")
            val loaded = documents.load(ref)
            treeStore.write(ref, TextCodec.encode("BBBB", TextFormat.DEFAULT)) // same size, different content
            val result = documents.save(ref, "new text from editor", loaded.baseline, loaded.format)
            assertThat(result).isInstanceOf(SaveResult.Conflict::class.java)
        }

    @Test
    fun differentSizeExternalChangeIsConflict() =
        runTest {
            val ref = createDoc("AAAA")
            val loaded = documents.load(ref)
            treeStore.write(ref, TextCodec.encode("A much longer replacement", TextFormat.DEFAULT))
            val result = documents.save(ref, "new text from editor", loaded.baseline, loaded.format)
            assertThat(result).isInstanceOf(SaveResult.Conflict::class.java)
        }

    @Test
    fun noExternalChangeSaves() =
        runTest {
            val ref = createDoc("AAAA")
            val loaded = documents.load(ref)
            val result = documents.save(ref, "AAAA edited by the user", loaded.baseline, loaded.format)
            assertThat(result).isInstanceOf(SaveResult.Saved::class.java)
            assertThat(String(treeStore.read(ref))).contains("AAAA edited by the user")
        }
}
