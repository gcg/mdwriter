package dev.mdwriter.ui.editor

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.document.AutosaveCoordinator
import dev.mdwriter.data.document.ConflictNames
import dev.mdwriter.data.document.DocumentRepository
import dev.mdwriter.data.document.SaveState
import dev.mdwriter.data.document.WelcomeNote
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.key
import dev.mdwriter.data.settings.Position
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.testing.FakeDocumentStore
import dev.mdwriter.testing.FakeEditorBinding
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
class EditorViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var store: FakeDocumentStore
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var positionsRepo: PositionStore
    private lateinit var recovery: RecoveryStore
    private lateinit var library: LibraryRepository
    private lateinit var documents: DocumentRepository
    private lateinit var autosave: AutosaveCoordinator
    private lateinit var appScope: CoroutineScope
    private val dataStoreJobs = mutableListOf<Job>()
    private var settingsFileCounter = 0
    private var clock = 1_700_000_000_000L

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        store = FakeDocumentStore()

        val settingsJob = Job()
        dataStoreJobs += settingsJob
        settingsRepo =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.Unconfined + settingsJob),
                    produceFile = { tmp.newFile("settings-${settingsFileCounter++}.preferences_pb") },
                ),
            )

        val positionsJob = Job()
        dataStoreJobs += positionsJob
        positionsRepo =
            PositionStore(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(Dispatchers.Unconfined + positionsJob),
                    produceFile = { tmp.newFile("positions-${settingsFileCounter++}.preferences_pb") },
                ),
                clock = { clock },
            )

        // `io` defaults to a REAL Dispatchers.IO, which would let part of every save race the virtual clock.
        recovery = RecoveryStore(tmp.newFolder("recovery"), io = testDispatcher)
        library = LibraryRepository(store, settingsRepo)
        // Shares `testDispatcher`'s scheduler with `autosave`/`viewModelScope` (Main) so that advanceTimeBy/
        // runCurrent() deterministically drive the WHOLE save chain — Dispatchers.Unconfined would let part of
        // it resume on a real thread past its first suspension point, racing the test's own assertions.
        documents = DocumentRepository(library, recovery, testDispatcher)
        appScope = CoroutineScope(Job())
        autosave = AutosaveCoordinator(appScope, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dataStoreJobs.forEach { it.cancel() }
        appScope.cancel()
    }

    private fun newVm(
        handle: SavedStateHandle = SavedStateHandle(),
        clockMillis: () -> Long = { clock },
    ): EditorViewModel =
        EditorViewModel(
            documents = documents,
            library = library,
            autosave = autosave,
            settings = settingsRepo,
            positions = positionsRepo,
            recovery = recovery,
            appScope = appScope,
            main = testDispatcher,
            handle = handle,
            clock = clockMillis,
        )

    /** Keeps `vm.uiState`'s `WhileSubscribed(5_000)` sharing "hot" for the rest of the test, and returns a way to
     * read the latest value. */
    private fun TestScope.trackUiState(vm: EditorViewModel): () -> EditorUiState {
        val values = mutableListOf<EditorUiState>()
        backgroundScope.launch { vm.uiState.collect { values.add(it) } }
        return { values.last() }
    }

    /** Advances virtual time past [atLeastMs] in small steps rather than one big `advanceTimeBy`: the
     * `AutosaveCoordinator`'s `serial` (a `limitedParallelism(1)` view) re-posts its own worker through the test
     * scheduler, which a single `advanceTimeBy`/`runCurrent()` pair does not always fully chase to completion. */
    private suspend fun TestScope.drainPast(atLeastMs: Long) {
        var elapsed = 0L
        val step = 50L
        while (elapsed <= atLeastMs) {
            advanceTimeBy(step)
            runCurrent()
            elapsed += step
        }
        repeat(10) { runCurrent() }
    }

    /** Same rationale as [drainPast], for actions that don't involve a delay (e.g. `flush()`), just several
     * dispatcher hops that a single `runCurrent()` doesn't always fully chase. */
    private fun TestScope.drainNow() {
        repeat(20) { runCurrent() }
    }

    @Test
    fun firstLaunchCreatesWelcomeCaretAtEndImeHidden() =
        runTest(testDispatcher) {
            val vm = newVm()
            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.selection).isEqualTo(WelcomeNote.TEXT.length)
                assertThat(install.request.text).isEqualTo(WelcomeNote.TEXT)
                val afterOpen = awaitItem() as EditorEvent.AfterOpen
                assertThat(afterOpen.showIme).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun laterLaunchReopensLastDocAtPosition() =
        runTest(testDispatcher) {
            val ref = DocRef.InternalFile("Notes.md")
            store.write(ref, "hello world".toByteArray())
            val key = ref.key()
            settingsRepo.update { it.copy(welcomeCreated = true, lastOpenDoc = key) }
            positionsRepo.put(key, Position(caret = 3, scrollY = 40))

            val vm = newVm()
            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("hello world")
                assertThat(install.request.selection).isEqualTo(3)
                assertThat(install.request.scrollY).isEqualTo(40)
                val afterOpen = awaitItem() as EditorEvent.AfterOpen
                assertThat(afterOpen.showIme).isFalse()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun missingLastDocOpensNewest() =
        runTest(testDispatcher) {
            store.write(DocRef.InternalFile("Existing.md"), "existing content".toByteArray())
            val missingKey = DocRef.InternalFile("Gone.md").key()
            settingsRepo.update { it.copy(welcomeCreated = true, lastOpenDoc = missingKey) }

            val vm = newVm()
            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("existing content")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun noDocsAfterWelcomeCreatesUntitledWithIme() =
        runTest(testDispatcher) {
            settingsRepo.update { it.copy(welcomeCreated = true) }

            val vm = newVm()
            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("")
                val afterOpen = awaitItem() as EditorEvent.AfterOpen
                assertThat(afterOpen.showIme).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(settingsRepo.current().autoNamed).isNotEmpty()
        }

    @Test
    fun editAutosavesAfter1s() =
        runTest(testDispatcher) {
            val vm = newVm()
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            val ref = requireNotNull(latestUi().doc)

            binding.edit(binding.snapshot().text + "zqx")
            vm.onEdit(binding.version)
            drainPast(1_001)

            val bytes = store.bytesOf(ref)
            assertThat(bytes).isNotNull()
            assertThat(String(bytes!!)).contains("zqx")
        }

    @Test
    fun externalChangeWhileCleanReloadsSilently() =
        runTest(testDispatcher) {
            val vm = newVm()
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                runCurrent()

                val ref = requireNotNull(latestUi().doc)
                store.externalWrite(ref, "changed externally".toByteArray())

                vm.onStart()
                val reinstall = awaitItem() as EditorEvent.Install
                assertThat(reinstall.request.text).isEqualTo("changed externally")
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(latestUi().conflict).isNull()
        }

    @Test
    fun externalChangeWhileDirtyShowsConflict() =
        runTest(testDispatcher) {
            val vm = newVm()
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                runCurrent()

                binding.edit(binding.snapshot().text + "unsaved edit")
                vm.onEdit(binding.version)
                runCurrent()

                val ref = requireNotNull(latestUi().doc)
                store.externalWrite(ref, "changed externally".toByteArray())
                vm.onStart()
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            val conflict = latestUi().conflict
            assertThat(conflict).isInstanceOf(ConflictState.ChangedOnDisk::class.java)
            assertThat((conflict as ConflictState.ChangedOnDisk).diskText).isEqualTo("changed externally")
        }

    @Test
    fun reloadDiscardsMine() =
        runTest(testDispatcher) {
            val vm = newVm()
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                runCurrent()

                binding.edit(binding.snapshot().text + "unsaved edit")
                vm.onEdit(binding.version)
                runCurrent()

                val ref = requireNotNull(latestUi().doc)
                store.externalWrite(ref, "changed externally".toByteArray())
                vm.onStart()
                runCurrent()

                vm.resolveConflict(ConflictAction.Reload)
                val reinstall = awaitItem() as EditorEvent.Install
                assertThat(reinstall.request.text).isEqualTo("changed externally")
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(latestUi().conflict).isNull()
        }

    @Test
    fun keepMineOverwritesDisk() =
        runTest(testDispatcher) {
            val vm = newVm()
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)
            lateinit var ref: DocRef

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                runCurrent()

                binding.edit(binding.snapshot().text + "unsaved edit")
                vm.onEdit(binding.version)
                runCurrent()

                ref = requireNotNull(latestUi().doc)
                store.externalWrite(ref, "changed externally".toByteArray())
                vm.onStart()
                runCurrent()
                cancelAndIgnoreRemainingEvents()
            }

            vm.resolveConflict(ConflictAction.KeepMine)
            drainNow()

            val bytes = store.bytesOf(ref)
            assertThat(bytes).isNotNull()
            assertThat(String(bytes!!)).contains("unsaved edit")
        }

    @Test
    fun saveBothWritesConflictCopy() =
        runTest(testDispatcher) {
            clock = 1_790_344_920_000L // 2026-09-25T14:02:00Z
            val vm = newVm(clockMillis = { clock })
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                runCurrent()

                binding.edit(binding.snapshot().text + "unsaved edit")
                vm.onEdit(binding.version)
                runCurrent()

                val ref = requireNotNull(latestUi().doc)
                store.externalWrite(ref, "changed externally".toByteArray())
                vm.onStart()
                runCurrent()

                vm.resolveConflict(ConflictAction.SaveBoth)
                val message = awaitItem() as EditorEvent.Message
                val expectedName = ConflictNames.name("Welcome", "md", java.time.LocalDateTime.of(2026, 9, 25, 14, 2))
                assertThat(message.text).contains(expectedName)
                val reinstall = awaitItem() as EditorEvent.Install
                assertThat(reinstall.request.text).isEqualTo("changed externally")
                cancelAndIgnoreRemainingEvents()
            }

            val copyRef = DocRef.InternalFile("Welcome (conflict 2026-09-25 1402).md")
            val bytes = store.bytesOf(copyRef)
            assertThat(bytes).isNotNull()
            assertThat(String(bytes!!)).contains("unsaved edit")
        }

    @Test
    fun processDeathRestoresFromHandleAndRecovery() =
        runTest(testDispatcher) {
            val ref = DocRef.InternalFile("Recovered.md")
            store.write(ref, "original content".toByteArray())
            val key = ref.key()
            recovery.write(key, "recovered content twelve")

            val handle = SavedStateHandle(mapOf("docKey" to key.value, "selStart" to 5, "scrollY" to 0))
            val vm = newVm(handle = handle)
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("recovered content twelve")
                assertThat(install.request.selection).isEqualTo(5)
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            assertThat(latestUi().save).isInstanceOf(SaveState.Dirty::class.java)

            drainPast(1_001)
            assertThat(latestUi().save).isEqualTo(SaveState.Clean)
            assertThat(String(store.bytesOf(ref)!!)).isEqualTo("recovered content twelve")
        }
}
