package dev.mdwriter.data.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.document.DocumentRepository
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.AtomicWriter
import dev.mdwriter.data.storage.InternalStore
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.TrashBin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AutoNamerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var internalStore: InternalStore
    private lateinit var library: LibraryRepository
    private lateinit var documents: DocumentRepository
    private lateinit var settings: SettingsRepository
    private lateinit var positions: PositionStore
    private lateinit var autoNamer: AutoNamer
    private lateinit var scopeJob: Job

    @Before
    fun setUp() {
        scopeJob = Job()
        val scope = CoroutineScope(Dispatchers.Unconfined + scopeJob)
        val root = tmp.newFolder("library")
        internalStore =
            InternalStore(root, TrashBin(File(root.parentFile, ".trash")), AtomicWriter(), Dispatchers.Unconfined)
        settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { tmp.newFile("settings.preferences_pb") },
                ),
            )
        positions =
            PositionStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { tmp.newFile("positions.preferences_pb") },
                ),
            )
        library = LibraryRepository(internalStore, settings, Dispatchers.Unconfined)
        documents = DocumentRepository(library, RecoveryStore(tmp.newFolder("recovery")), Dispatchers.Unconfined)
        autoNamer = AutoNamer(library, documents, settings, positions)
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
    }

    private suspend fun createFile(
        name: String,
        text: String,
    ): DocRef.InternalFile {
        val ref = internalStore.create(FolderRef.INTERNAL_ROOT, name) as DocRef.InternalFile
        if (text.isNotEmpty()) internalStore.write(ref, text.toByteArray())
        return ref
    }

    @Test
    fun notAutoNamedIsKept() =
        runTest {
            val ref = createFile("Untitled.md", "# Groceries\n- bread")
            val outcome = autoNamer.onLeave(ref, LeaveReason.Switch)
            assertThat(outcome).isEqualTo(LeaveOutcome.Kept)
        }

    @Test
    fun renamesFromFirstLineAndMovesKeyAndPosition() =
        runTest {
            val ref = createFile("Untitled.md", "# Groceries\n- bread")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }
            positions.put(
                ref.key(),
                dev.mdwriter.data.settings
                    .Position(3, 10),
            )

            val outcome = autoNamer.onLeave(ref, LeaveReason.Switch)

            val renamed = outcome as LeaveOutcome.Renamed
            assertThat(internalStore.displayName(renamed.newRef)).isEqualTo("Groceries.md")
            assertThat(settings.current().autoNamed).containsExactly(renamed.newRef.key().value)
            assertThat(positions.get(ref.key())).isNull()
            assertThat(positions.get(renamed.newRef.key())).isNotNull()
        }

    @Test
    fun collidingNameGetsNumbered() =
        runTest {
            createFile("Groceries.md", "existing")
            val ref = createFile("Untitled.md", "# Groceries\n- bread")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }

            val outcome = autoNamer.onLeave(ref, LeaveReason.Switch) as LeaveOutcome.Renamed
            assertThat(internalStore.displayName(outcome.newRef)).isEqualTo("Groceries 2.md")
        }

    @Test
    fun sameTitleAgainIsKeptNoChurn() =
        runTest {
            val ref = createFile("Groceries 2.md", "# Groceries\n- bread")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }

            val outcome = autoNamer.onLeave(ref, LeaveReason.Switch)
            assertThat(outcome).isEqualTo(LeaveOutcome.Kept)
        }

    @Test
    fun blankAndSwitchDeletes() =
        runTest {
            val ref = createFile("Untitled.md", "")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }

            val outcome = autoNamer.onLeave(ref, LeaveReason.Switch)

            assertThat(outcome).isEqualTo(LeaveOutcome.Deleted)
            assertThat(settings.current().autoNamed).isEmpty()
            val listing = internalStore.list(FolderRef.INTERNAL_ROOT)
            assertThat(listing.any { it.doc?.key() == ref.key() }).isFalse()
        }

    @Test
    fun blankAndDrawerOpenedIsKept() =
        runTest {
            val ref = createFile("Untitled.md", "   \n  ")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }
            assertThat(autoNamer.onLeave(ref, LeaveReason.DrawerOpened)).isEqualTo(LeaveOutcome.Kept)
        }

    @Test
    fun blankAndStoppedIsKept() =
        runTest {
            val ref = createFile("Untitled.md", "")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }
            assertThat(autoNamer.onLeave(ref, LeaveReason.Stopped)).isEqualTo(LeaveOutcome.Kept)
        }

    @Test
    fun externalRefIsKept() =
        runTest {
            val ref = DocRef.External("content://doc", writable = true)
            assertThat(autoNamer.onLeave(ref, LeaveReason.Switch)).isEqualTo(LeaveOutcome.Kept)
        }

    @Test
    fun sanitizesForbiddenCharacters() =
        runTest {
            val ref = createFile("Untitled.md", "My: Note/Draft?\nbody")
            settings.update { it.copy(autoNamed = setOf(ref.key().value)) }

            val outcome = autoNamer.onLeave(ref, LeaveReason.Switch) as LeaveOutcome.Renamed
            assertThat(internalStore.displayName(outcome.newRef)).isEqualTo("My NoteDraft.md")
        }
}
