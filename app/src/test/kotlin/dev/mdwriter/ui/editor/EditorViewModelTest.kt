package dev.mdwriter.ui.editor

import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.document.AutosaveCoordinator
import dev.mdwriter.data.document.ConflictNames
import dev.mdwriter.data.document.DocumentRepository
import dev.mdwriter.data.document.SaveState
import dev.mdwriter.data.document.WelcomeNote
import dev.mdwriter.data.library.AutoNamer
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.key
import dev.mdwriter.data.settings.Position
import dev.mdwriter.data.settings.PositionStore
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.ExternalDocStore
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.SafIo
import dev.mdwriter.intents.IntentHandler
import dev.mdwriter.intents.RoutedIntent
import dev.mdwriter.intents.ShareOut
import dev.mdwriter.testing.FakeDocumentStore
import dev.mdwriter.testing.FakeEditorBinding
import dev.mdwriter.testing.TestDocumentsProvider
import dev.mdwriter.ui.preview.PreviewRenderer
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
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class EditorViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var store: FakeDocumentStore
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var positionsRepo: PositionStore
    private lateinit var recovery: RecoveryStore
    private lateinit var library: LibraryRepository
    private lateinit var documents: DocumentRepository
    private lateinit var autosave: AutosaveCoordinator
    private lateinit var autoNamer: AutoNamer
    private lateinit var appScope: CoroutineScope
    private lateinit var externalStore: ExternalDocStore
    private lateinit var intentHandler: IntentHandler
    private lateinit var shareOut: ShareOut
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

        externalStore = ExternalDocStore(context, SafIo(context.contentResolver), settingsRepo, io = testDispatcher)
        // `io` defaults to a REAL Dispatchers.IO, which would let part of every save race the virtual clock.
        recovery = RecoveryStore(tmp.newFolder("recovery"), io = testDispatcher)
        library = LibraryRepository(store, settingsRepo, externalStore = externalStore)
        // Shares `testDispatcher`'s scheduler with `autosave`/`viewModelScope` (Main) so that advanceTimeBy/
        // runCurrent() deterministically drive the WHOLE save chain — Dispatchers.Unconfined would let part of
        // it resume on a real thread past its first suspension point, racing the test's own assertions.
        documents = DocumentRepository(library, recovery, testDispatcher)
        appScope = CoroutineScope(Job())
        autosave = AutosaveCoordinator(appScope, testDispatcher)
        autoNamer = AutoNamer(library, documents, settingsRepo, positionsRepo)
        intentHandler = IntentHandler(context, library, externalStore, settingsRepo)
        shareOut = ShareOut(context, library, io = testDispatcher)
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
            autoNamer = autoNamer,
            appScope = appScope,
            main = testDispatcher,
            handle = handle,
            clock = clockMillis,
            previewRenderer = PreviewRenderer(testDispatcher),
            intentHandler = intentHandler,
            externalStore = externalStore,
            shareOut = shareOut,
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

    // ---- T18 --------------------------------------------------------------------------------------------------

    @Test
    fun shareTextCreatesNoteOpensWithIme() =
        runTest(testDispatcher) {
            settingsRepo.update { it.copy(welcomeCreated = true) }
            val vm = newVm()
            vm.events.test {
                vm.startWith(RoutedIntent.ShareText("Idea", "from adb"))
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("# Idea\n\nfrom adb\n")
                val afterOpen = awaitItem() as EditorEvent.AfterOpen
                assertThat(afterOpen.showIme).isTrue()
                cancelAndIgnoreRemainingEvents()
            }
            assertThat(store.contains(DocRef.InternalFile("Idea.md"))).isTrue()
        }

    @Test
    fun newIntentFlushesCurrentFirst() =
        runTest(testDispatcher) {
            settingsRepo.update { it.copy(welcomeCreated = true) }
            store.write(DocRef.InternalFile("Existing.md"), "existing content".toByteArray())
            val vm = newVm()
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start()
                val install = awaitItem() as EditorEvent.Install
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                runCurrent()

                // Dirty, but nowhere near the 1s idle autosave — only handle()'s own flush() should save it.
                binding.edit(binding.snapshot().text + " unsaved")
                vm.onEdit(binding.version)
                runCurrent()

                vm.handle(RoutedIntent.ShareText("Idea", "new note"))
                val newInstall = awaitItem() as EditorEvent.Install
                assertThat(newInstall.request.text).isEqualTo("# Idea\n\nnew note\n")
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            assertThat(String(store.bytesOf(DocRef.InternalFile("Existing.md"))!!)).contains("unsaved")
        }

    @Test
    fun readOnlyExternalSaveCopyOpensLibraryCopy() =
        runTest(testDispatcher) {
            settingsRepo.update { it.copy(welcomeCreated = true) }
            TestDocumentsProvider.flags = TestDocumentsProvider.DEFAULT_FLAGS
            TestDocumentsProvider.throwSecurity = false
            org.robolectric.Robolectric
                .buildContentProvider(TestDocumentsProvider::class.java)
                .create(
                    android.content.pm.ProviderInfo().apply {
                        authority = TestDocumentsProvider.AUTHORITY
                        exported = true
                        grantUriPermissions = true
                        readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
                        writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
                    },
                )
            val resolver = context.contentResolver
            val rootDocUri =
                DocumentsContract.buildDocumentUri(TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_DOC_ID)
            val uri =
                requireNotNull(DocumentsContract.createDocument(resolver, rootDocUri, "text/markdown", "External.md"))
            requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write("external content".toByteArray()) }

            val ref = DocRef.External(uri.toString(), writable = false)
            val vm = newVm()
            val latestUi = trackUiState(vm)
            val binding = FakeEditorBinding()
            vm.bindEditor(binding)

            vm.events.test {
                vm.start(initial = ref, initialShowIme = false)
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("external content")
                binding.install(install.request.text, install.request.selection, install.request.scrollY)
                vm.onInstalled(binding.version)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()
            assertThat(latestUi().readOnly).isTrue()

            vm.events.test {
                vm.saveCopyToLibrary()
                val install = awaitItem() as EditorEvent.Install
                assertThat(install.request.text).isEqualTo("external content")
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()

            assertThat(store.contains(DocRef.InternalFile("External.md"))).isTrue()
            assertThat(String(store.bytesOf(DocRef.InternalFile("External.md"))!!)).isEqualTo("external content")
            assertThat(latestUi().doc).isEqualTo(DocRef.InternalFile("External.md"))
            assertThat(latestUi().readOnly).isFalse()
        }
}
