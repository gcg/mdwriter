package dev.mdwriter.ui.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.key
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.settings.SortOrder
import dev.mdwriter.testing.FakeDocumentSession
import dev.mdwriter.testing.FakeDocumentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var store: FakeDocumentStore
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var positionsRepo: PositionStore
    private lateinit var library: LibraryRepository
    private lateinit var session: FakeDocumentSession
    private lateinit var appScope: CoroutineScope
    private lateinit var vm: LibraryViewModel
    private var fileCounter = 0

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        store = FakeDocumentStore()
        val scope = CoroutineScope(Dispatchers.Unconfined + Job())
        settingsRepo =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { tmp.newFile("settings-${fileCounter++}.preferences_pb") },
                ),
            )
        positionsRepo =
            PositionStore(
                PreferenceDataStoreFactory.create(
                    scope = scope,
                    produceFile = { tmp.newFile("positions-${fileCounter++}.preferences_pb") },
                ),
            )
        library = LibraryRepository(store, settingsRepo, testDispatcher)
        session = FakeDocumentSession()
        // Shares testDispatcher's scheduler so `commitPendingNow()`'s `appScope.launch` is deterministically driven
        // by `runCurrent()`/`advanceTimeBy` (same trap T11's STATUS documents for AutosaveCoordinator/appScope).
        appScope = CoroutineScope(Job() + testDispatcher)
        vm = LibraryViewModel(library, settingsRepo, positionsRepo, session, appScope, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        appScope.cancel()
    }

    /** Keeps `vm.uiState`'s `WhileSubscribed(5_000)` sharing "hot" for the rest of the test. */
    private fun TestScope.trackUiState(): () -> LibraryUiState {
        val values = mutableListOf<LibraryUiState>()
        backgroundScope.launch { vm.uiState.collect { values.add(it) } }
        return { values.last() }
    }

    private fun content(state: () -> LibraryUiState) = state() as LibraryUiState.Content

    @Test
    fun entriesSortedFoldersFirstThenByEachOrder() =
        runTest(testDispatcher) {
            store.write(DocRef.InternalFile("B.md"), "b".toByteArray())
            store.write(DocRef.InternalFile("A.md"), "a".toByteArray()) // written later -> newer
            // FakeDocumentStore derives folders from file paths (T11 note); seed one file so each folder appears.
            store.write(DocRef.InternalFile("Zeta/x.md"), "x".toByteArray())
            store.write(DocRef.InternalFile("Alpha/x.md"), "x".toByteArray())

            val state = trackUiState()
            runCurrent()
            var c = content(state)
            assertThat(c.folders.map { it.name }).containsExactly("Alpha", "Zeta").inOrder()
            assertThat(c.files.map { it.entry.name }).containsExactly("A.md", "B.md").inOrder()

            vm.setSort(SortOrder.ModifiedOldestFirst)
            runCurrent()
            c = content(state)
            assertThat(c.files.map { it.entry.name }).containsExactly("B.md", "A.md").inOrder()

            vm.setSort(SortOrder.NameAToZ)
            runCurrent()
            c = content(state)
            assertThat(c.files.map { it.entry.name }).containsExactly("A.md", "B.md").inOrder()

            vm.setSort(SortOrder.NameZToA)
            runCurrent()
            c = content(state)
            assertThat(c.files.map { it.entry.name }).containsExactly("B.md", "A.md").inOrder()
        }

    @Test
    fun deleteHidesAtOnceAndCommitsAfterWindow() =
        runTest(testDispatcher) {
            val ref = DocRef.InternalFile("Note.md")
            store.write(ref, "hello".toByteArray())
            val state = trackUiState()
            runCurrent()
            val entry = content(state).files.single().entry

            vm.delete(entry)
            runCurrent()
            assertThat(content(state).files).isEmpty()
            assertThat(store.contains(ref)).isTrue()

            advanceTimeBy(4_999)
            runCurrent()
            assertThat(store.contains(ref)).isTrue()

            advanceTimeBy(2)
            runCurrent()
            assertThat(store.contains(ref)).isFalse()
        }

    @Test
    fun undoDeleteRestoresAndNeverTrashes() =
        runTest(testDispatcher) {
            val ref = DocRef.InternalFile("Note.md")
            store.write(ref, "hello".toByteArray())
            val state = trackUiState()
            runCurrent()
            val entry = content(state).files.single().entry

            vm.delete(entry)
            runCurrent()
            vm.undoDelete()
            runCurrent()
            assertThat(content(state).files.map { it.entry.name }).containsExactly("Note.md")

            advanceTimeBy(6_000)
            runCurrent()
            assertThat(store.contains(ref)).isTrue()
        }

    @Test
    fun secondDeleteCommitsTheFirstImmediately() =
        runTest(testDispatcher) {
            val ref1 = DocRef.InternalFile("One.md")
            val ref2 = DocRef.InternalFile("Two.md")
            store.write(ref1, "1".toByteArray())
            store.write(ref2, "2".toByteArray())
            val state = trackUiState()
            runCurrent()
            val entry1 = content(state).files.first { it.entry.name == "One.md" }.entry
            val entry2 = content(state).files.first { it.entry.name == "Two.md" }.entry

            vm.delete(entry1)
            runCurrent()
            vm.delete(entry2)
            runCurrent()

            assertThat(store.contains(ref1)).isFalse()
            assertThat(store.contains(ref2)).isTrue() // still pending
        }

    @Test
    fun onStopCommitsPendingDelete() =
        runTest(testDispatcher) {
            val ref = DocRef.InternalFile("Note.md")
            store.write(ref, "hello".toByteArray())
            val state = trackUiState()
            runCurrent()
            val entry = content(state).files.single().entry

            vm.delete(entry)
            runCurrent()
            vm.onStop()
            runCurrent()
            assertThat(store.contains(ref)).isFalse()
        }

    @Test
    fun deletingOpenDocSwitchesToNewestOther() =
        runTest(testDispatcher) {
            val older = DocRef.InternalFile("Older.md")
            store.write(older, "old".toByteArray())
            val newer = DocRef.InternalFile("Newer.md")
            store.write(newer, "new".toByteArray())
            val state = trackUiState()
            runCurrent()
            val olderEntry = content(state).files.first { it.entry.name == "Older.md" }.entry

            session.setCurrent(older)
            vm.delete(olderEntry)
            runCurrent()

            val call = session.openCalls.last()
            assertThat(call.ref).isEqualTo(newer)
            assertThat(call.showIme).isFalse()
            assertThat(call.leaveCurrent).isFalse()
        }

    @Test
    fun deletingTheOnlyOpenDocCreatesFreshUntitled() =
        runTest(testDispatcher) {
            val only = DocRef.InternalFile("Only.md")
            store.write(only, "content".toByteArray())
            val state = trackUiState()
            runCurrent()
            val entry = content(state).files.single().entry

            session.setCurrent(only)
            vm.delete(entry)
            runCurrent()

            val call = session.openCalls.last()
            assertThat((call.ref as DocRef.InternalFile).relPath).isEqualTo("Untitled.md")
            assertThat(call.showIme).isFalse()
            assertThat(call.leaveCurrent).isFalse()
        }

    @Test
    fun newNoteNumbersAndOpensWithImeThenClosesDrawer() =
        runTest(testDispatcher) {
            vm.events.test {
                vm.newNote()
                assertThat(awaitItem()).isEqualTo(LibraryEvent.CloseDrawer)
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            assertThat(store.contains(DocRef.InternalFile("Untitled.md"))).isTrue()
            assertThat(session.openCalls.last().showIme).isTrue()
            assertThat(settingsRepo.current().autoNamed).contains(DocRef.InternalFile("Untitled.md").key().value)

            vm.newNote()
            runCurrent()
            assertThat(store.contains(DocRef.InternalFile("Untitled 2.md"))).isTrue()
        }

    @Test
    fun renameOfOpenDocFlushesThenRepointsAndClearsAutoNamed() =
        runTest(testDispatcher) {
            val ref = DocRef.InternalFile("Untitled.md")
            store.write(ref, "content".toByteArray())
            settingsRepo.update { it.copy(autoNamed = setOf(ref.key().value)) }
            val state = trackUiState()
            runCurrent()
            val entry = content(state).files.single().entry
            session.setCurrent(ref)

            vm.rename(entry, "Groceries")
            runCurrent()

            assertThat(session.flushCalls).isEqualTo(1)
            assertThat(session.currentRefChangedCalls).hasSize(1)
            assertThat(session.currentRefChangedCalls.single().first).isEqualTo(ref)
            assertThat(settingsRepo.current().autoNamed).isEmpty()
            assertThat(store.contains(DocRef.InternalFile("Groceries.md"))).isTrue()
        }

    @Test
    fun searchMatchesNameAndContentNameFirst() =
        runTest(testDispatcher) {
            store.write(DocRef.InternalFile("Rainy day.md"), "unrelated body text".toByteArray())
            store.write(DocRef.InternalFile("Weather.md"), "It had been raining all day".toByteArray())
            val state = trackUiState()
            runCurrent()

            vm.startSearch()
            vm.setQuery("rain")
            advanceTimeBy(300)
            runCurrent()

            val c = content(state)
            assertThat(c.searching).isTrue()
            assertThat(c.files.map { it.entry.name }).containsExactly("Rainy day.md", "Weather.md").inOrder()
        }

    @Test
    fun crumbsPushGoToAndUp() =
        runTest(testDispatcher) {
            store.write(DocRef.InternalFile("Drafts/x.md"), "x".toByteArray())
            val state = trackUiState()
            runCurrent()
            val folderEntry = content(state).folders.single()

            vm.openFolder(folderEntry)
            runCurrent()
            assertThat(content(state).crumbs).hasSize(2)

            vm.up()
            runCurrent()
            assertThat(content(state).crumbs).hasSize(1)

            vm.openFolder(folderEntry)
            runCurrent()
            vm.goTo(0)
            runCurrent()
            assertThat(content(state).crumbs).hasSize(1)
        }
}
