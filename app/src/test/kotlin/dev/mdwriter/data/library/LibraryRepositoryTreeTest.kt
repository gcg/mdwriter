package dev.mdwriter.data.library

import android.Manifest
import android.content.pm.ProviderInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.InternalStore
import dev.mdwriter.data.storage.SafIo
import dev.mdwriter.data.storage.SafTreeStore
import dev.mdwriter.data.storage.StorageException
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

@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTreeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val resolver = context.contentResolver
    private val tree = TestDocumentsProvider.TREE_URI

    private lateinit var scopeJob: Job
    private lateinit var settingsStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository
    private lateinit var internalStore: InternalStore
    private lateinit var treeGrants: TreeGrants
    private lateinit var library: LibraryRepository

    @Before
    fun setUp() {
        TestDocumentsProvider.flags = TestDocumentsProvider.DEFAULT_FLAGS
        TestDocumentsProvider.nullLastModified = false
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
        internalStore = InternalStore(tmp.newFolder("library"), TrashBin(tmp.newFolder(".trash")))
        treeGrants = TreeGrants(resolver)
        val safIo = SafIo(resolver)
        val trashBin = TrashBin(tmp.getRoot().resolve(".trash"))
        library =
            LibraryRepository(
                internalStore = internalStore,
                settings = settings,
                io = Dispatchers.Unconfined,
                treeStoreFactory = { treeUriString ->
                    SafTreeStore(
                        android.net.Uri.parse(treeUriString),
                        resolver,
                        safIo,
                        trashBin,
                        io = Dispatchers.Unconfined,
                    )
                },
                treeGrants = treeGrants,
            )
    }

    @Test
    fun linkAddsLocationWithRootName() =
        runTest {
            val info = library.linkTree(tree)
            assertThat(info.state).isEqualTo(LocationState.Ready)
            assertThat(info.name).isEqualTo("root")
            assertThat(library.locations.value.map { it.id }).contains(LocationId.Tree(tree.toString()))
        }

    @Test
    fun unlinkReleasesGrantAndKeepsFiles() =
        runTest {
            library.linkTree(tree)
            val root = library.rootOf(LocationId.Tree(tree.toString()))
            val ref = library.storeFor(root.location).create(root, "a.md")
            library.storeFor(root.location).write(ref, "keep me".toByteArray())

            library.unlinkTree(LocationId.Tree(tree.toString()))

            assertThat(treeGrants.isGranted(tree)).isFalse()
            assertThat(library.locations.value.map { it.id }).doesNotContain(LocationId.Tree(tree.toString()))
            // The file itself was never touched — read it back directly via a fresh store.
            val safIo = SafIo(resolver)
            val freshStore =
                SafTreeStore(tree, resolver, safIo, TrashBin(tmp.newFolder(".trash2")), io = Dispatchers.Unconfined)
            assertThat(String(freshStore.read(ref))).isEqualTo("keep me")
        }

    @Test
    fun revalidateMarksDisconnectedWithoutGrant() =
        runTest {
            library.linkTree(tree)
            treeGrants.release(tree) // simulate the OS revoking the grant behind our back
            library.revalidate()
            val info = library.locations.value.first { it.id == LocationId.Tree(tree.toString()) }
            assertThat(info.state).isEqualTo(LocationState.Disconnected)
            // Never auto-unlinked: still present in settings.
            assertThat(settings.current().linkedTrees).contains(tree.toString())
        }

    @Test
    fun moveInternalToTreeCopiesThenTrashes() =
        runTest {
            library.linkTree(tree)
            val internalRef = internalStore.create(dev.mdwriter.data.library.FolderRef.INTERNAL_ROOT, "note.md")
            internalStore.write(internalRef, "hello world".toByteArray())
            val treeRoot = library.rootOf(LocationId.Tree(tree.toString()))

            val newRef = library.move(internalRef, treeRoot)

            assertThat(String(library.storeFor(newRef).read(newRef))).isEqualTo("hello world")
            assertThat(internalStore.stat(internalRef)).isNull()
            val trashDir = tmp.getRoot().resolve(".trash")
            assertThat(trashDir.listFiles().orEmpty()).isNotEmpty()
        }

    @Test
    fun moveFailureKeepsSource() =
        runTest {
            library.linkTree(tree)
            val internalRef = internalStore.create(dev.mdwriter.data.library.FolderRef.INTERNAL_ROOT, "note.md")
            internalStore.write(internalRef, "hello world".toByteArray())
            val treeRoot = library.rootOf(LocationId.Tree(tree.toString()))

            // Make the destination read-only AFTER create (create still works; write() will throw ReadOnly).
            TestDocumentsProvider.flags = 0
            var threw = false
            try {
                library.move(internalRef, treeRoot)
            } catch (e: StorageException) {
                threw = true
            }
            assertThat(threw).isTrue()
            // The source was never touched.
            assertThat(internalStore.stat(internalRef)).isNotNull()
            assertThat(String(internalStore.read(internalRef))).isEqualTo("hello world")
        }
}
