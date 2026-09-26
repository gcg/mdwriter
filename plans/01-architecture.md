# 01 — Architecture & contracts

This file is the **contract** every task builds against. If a task file and this file disagree, this file wins; if you
must deviate, write down why in `plans/STATUS.md` and update this file in the same commit.

Background and evidence for every decision: `plans/research/*.md` (read `plans/research/README.md` first — it lists the
known errors in those reports).

---

## 1. Stack (pinned, verified by real builds on this Mac)

| Thing | Version / choice | Notes |
|---|---|---|
| Android Gradle Plugin | **9.3.3** | Studio Quail 3 (installed) supports ≤ 9.3. 9.4.1 only after updating Studio to Quail 4. |
| Gradle | **9.7.1** (wrapper, sha256-pinned) | Launch with JDK 17–26. Homebrew `openjdk` is 27 → **never** use it. |
| JDK | JBR **21** (`/usr/libexec/java_home -v 21`); daemon pinned via `gradle/gradle-daemon-jvm.properties` | Bytecode target Java 17. |
| Kotlin | **2.4.20**, AGP **built-in Kotlin** | Do **not** apply `org.jetbrains.kotlin.android` (build fails). |
| Compose | BOM **2026.09.00** (ui/foundation 1.12.1, material3 1.4.0, adaptive 1.3.0) | No `material-icons-*` (frozen); use Material Symbols XML drawables. |
| SDK levels | compileSdk **37**, targetSdk **37**, minSdk **36** | Android 16 + 17 only. Local platform `android-37.0` suffices. |
| Other libs | activity-compose 1.13.0, lifecycle 2.11.0, core-ktx 1.19.1, datastore-preferences 1.2.1, webkit 1.17.1, profileinstaller 1.4.1, coroutines 1.11.0, commonmark 0.30.0 (+8 exts) | Exact catalog: `plans/reference/build/gradle/libs.versions.toml`. |
| Tests | JUnit 6.1.3 (`:core:markdown`), JUnit4 + Robolectric 4.17 + Compose ui-test (v2 API) + Truth 1.4.5 + Turbine 1.2.1 + coroutines-test (`:app`) | `createComposeRule` must be imported from `androidx.compose.ui.test.junit4.v2`. |
| Code quality | Spotless 8.10.2 + ktlint 1.8.0 + compose-rules 0.6.6; Android Lint (`abortOnError=true`) | No detekt. |
| DI | **Manual** `AppContainer` (no Hilt/Koin) | |
| Navigation | **None** (one screen + drawer + overlays driven by state) | |
| AppCompat / MDC | **None** | Platform `EditText`, platform themes, Compose Material3. |
| Permissions | **None.** No `INTERNET`, no storage permissions | "No logins, no nothing" is enforced by the manifest. |

The verified build files are in `plans/reference/build/` (they passed debug + release/R8 + JVM tests + Robolectric +
lint + ktlint on 2026-09-25). Copy them; don't re-derive them.

## 2. Modules

```
mdwriter/
├─ app/              com.android.application + Compose. UI, editor engine (View-based), storage, settings.
└─ core/markdown/    Pure Kotlin/JVM (no Android types!). Highlighter, SmartEdit, TextStats, MarkdownHtml, DocTitle.
```
Also: `scripts/qa/` (T20: `gen-doc.sh`, `push-doc.sh`) and an optional `:baselineprofile` module (T21, only if cold
start > 800 ms; then `make check` covers it).
`:core:markdown` is JVM-only so its heavy test suite runs in ~1 s without Robolectric. It must never import `android.*`.

## 3. Package map

Root package: **`dev.mdwriter`** (namespace = applicationId = `dev.mdwriter`; debug appId `dev.mdwriter.debug`).

```
core/markdown/src/main/kotlin/dev/mdwriter/markdown/
  MdModel.kt              MdKind (37 kinds), MdSpan, BlockType, LineInfo, HighlightDelta, HeadingItem
  MarkdownHighlighter.kt  incremental line-state scanner  (reference: plans/reference/markdown/)
  InlineScanner.kt        internal CommonMark inline parser
  SmartEdit.kt            pure editing commands -> TextEdit
  TextStats.kt            words/chars/sentences/tasks/reading time
  MarkdownHtml.kt         commonmark-java HTML for Preview/Export
  DocTitle.kt             title-from-content, THE excerpt rule, filename sanitizing (pure)   [T04]
  MarkupStrip.kt          internal: strips inline markup (clearFormatting + DocTitle)        [T04]

app/src/main/kotlin/dev/mdwriter/
  MdWriterApp.kt          Application: creates AppContainer; StrictMode in debug (T18 minimal VmPolicy; T20 extends it
                          via Builder(StrictMode.getVmPolicy()), never replaces it)
  AppContainer.kt         manual DI graph (see §5)
  MainActivity.kt         ComponentActivity: edge-to-edge, setContent, intents, activity-level key shortcuts
  util/                   Log.kt (debug-gated), AppDispatchers.kt, PerfLog.kt + StrictModeUtil.kt [T20], PerfStats.kt [T21]
  debug/ (src/main, dev.mdwriter.debug, BuildConfig.DEBUG-gated) SampleDocs, FrameWorkLogger [T05];
                          src/debug: EditorPerfActivity + debug manifest [T07]
  editor/                 THE EDITOR ENGINE (Android View layer, no Compose inside)
    MarkdownEditText.kt   EditText subclass (the widget)
    EditorScrollView.kt   ScrollView host: scrolling, scroll room, viewport math, IME caret keeping
    EditorController.kt   the facade the UI talks to (see §6)
    MdEditable.kt         SpannableStringBuilder that silences shifted-span broadcasts (+ factory, kill switch)
    Restyler.kt           TextWatcher -> highlighter.update -> DirtyRange -> Choreographer reconcile
    DirtyRange.kt         offset-range accumulator (pure, unit tested)
    CaretDrawable.kt      2dp caret that spans the full line pitch
    MdUndoManager.kt      own undo/redo (platform undo disabled)
    SmartInput.kt         InputConnection wrapper + key handling -> SmartEdit (Enter/Backspace/Tab)
    SelectionUi.kt        HideSystemSelectionToolbar + selection state/anchor publisher
    FocusModeKind.kt      enum FocusModeKind { Off, Sentence, Paragraph }                 [T11]
    FocusMode.kt          focus-range computation (sentence/paragraph) + overlay drawing helper [T15]
    UndoHistory.kt        pure undo merge/group logic; FormatCommands.kt ToolbarAction -> TextEdit   [T08]
    TextSearch.kt, FindSession.kt   find/replace engine                                   [T17]
    spans/                MdStyleSpan.kt (marker interface + kind ids), Spans.kt (span classes),
                          FontSet.kt + EditorStyle.kt (created by T05), SpanFactory.kt (T06)
  ui/
    theme/                Theme.kt (MdWriterTheme), WriterColors.kt, Fonts.kt, Tokens.kt, SystemBars.kt
    root/                 MdWriterRoot.kt (adaptive layout, drawer, overlays, back ordering), AppCommands.kt (AppCommand +
                          AppShortcuts) [T13], KeyboardShortcuts.kt (ShortcutCatalog) [T20]
    editor/               EditorScreen.kt, EditorHost.kt (AndroidView), EditorViewModel.kt, EditorUiState.kt,
                          EditorChrome.kt (glyph buttons, stats line), OverflowMenu.kt, ConflictBanner.kt,
                          ChromeVisibility.kt [T13], StatsPipeline.kt [T15], ReadOnlyPill.kt [T18], EditorAccessibility.kt [T20]
    toolbar/              FormatToolbar.kt (selection pill), ToolbarAction.kt (T08; plain Kotlin, no Compose — the ONE
                          ui package the engine may import, for EditorController.perform), MoreMenu.kt
    gesture/              EditorSwipeNav.kt (Modifier.editorSwipeNav), SwipeTuning.kt, SwipeClassifier.kt [T13]
    library/              LibraryContent.kt, LibraryViewModel.kt, FileRow.kt, RelativeDate.kt, dialogs, LibraryPane.kt [T13],
                          ExportAllNotesAction.kt [T18]
    preview/              PreviewOverlay.kt, PreviewWebView.kt, DocumentImagePathHandler.kt, ImagePath.kt,
                          PreviewLinkPolicy.kt, PreviewTheme.kt, PreviewRenderer.kt, PreviewSync.kt [T16]
    settings/             SettingsSheet.kt, AboutSheet.kt
    find/                 FindBar.kt
  data/
    storage/              DocumentStore.kt, InternalStore.kt, SafTreeStore.kt, AtomicWriter.kt, TextCodec.kt,
                          RecoveryStore.kt, StorageError.kt, NoteFiles.kt [T10], Hashes.kt [T10], TrashBin.kt [T10],
                          ExternalDocStore.kt [T18]; T14 adds SafIo.kt, TreeGrants.kt, LocationInfo.kt, LinkFolderLauncher.kt
    library/              Location.kt (LocationId, DocRef, FolderRef, DocKey), LibraryEntry.kt, LibraryRepository.kt,
                          Excerpt.kt (one-line delegate to DocTitle.excerpt) [T12]
    document/             DocumentRepository.kt, AutosaveCoordinator.kt, LoadedDocument.kt, SaveResult.kt,
                          ConflictNames.kt, WelcomeNote.kt [T11]
    settings/             Settings.kt, SettingsRepository.kt, PositionStore.kt
    export/               ExportAllNotes.kt
  intents/                IntentRouter.kt, ShareOut.kt, IntentHandler.kt, SharedNote.kt, RecentList.kt [T18]
```

## 4. Key decisions (short form — rationale in research reports)

1. **Editor widget = platform `EditText` subclass (`MarkdownEditText`) hosted in Compose via `AndroidView`.**
   Compose `BasicTextField` re-lays out the whole document per keystroke (832 ms at 100k chars with styling, measured).
   EditText + our tricks: 2.1 / 5.4 / 6.8 ms per keystroke at 20k / 100k / 300k chars (emulator, measured).
2. **Styling = our own `android.text` span classes** (not framework `ParcelableSpan`s), applied incrementally by a
   **diff ("reconcile")** of wanted vs existing spans on dirty lines only, inside a `Choreographer` frame callback.
3. **`MdEditable`** (a `SpannableStringBuilder` subclass installed via `setEditableFactory`) hides the post-edit
   "span shifted" broadcasts from SpanWatchers unless they touch the selection/composing region. Without it every
   styled paragraph after the cursor is re-laid-out on each keystroke (162 ms at 100k).
4. **Scrolling: the EditText is `wrap_content` inside `EditorScrollView` (a platform `ScrollView`, `fillViewport=true`).**
   The EditText never scrolls itself. Reason: TextView clips its padding bands while it is self-scrolled
   (fact-check A15), so the "scroll room" needed for comfortable end-of-document writing and typewriter mode can only
   be padding if the EditText is not the scroller. Top/bottom scroll room = EditText padding, derived from the
   **window** height (not the IME-reduced height), so it never changes when the keyboard opens.
5. **Markdown parsing = hand-written incremental highlighter** (`MarkdownHighlighter`, reference code verified:
   641/652 CommonMark spec examples agree with commonmark-java; incremental == full on the seeded fuzz suite (seed 7 × 3,000 edits);
   `update()` ≈ 0.01 ms). commonmark-java is used only for Preview/Export HTML and as a test oracle.
6. **Preview = commonmark-java HTML in a locked-down `WebView`** (JS off, no file/content access, no network — the app
   has no INTERNET permission anyway), fonts + CSS served by `WebViewAssetLoader`.
7. **Storage = internal `filesDir/library/` by default** + optional user-picked SAF folder ("Use a folder…").
   Atomic writes internally; `"wt"` + recovery copy for SAF. Autosave, no Save button.
8. **Swipe gesture = custom detector in `PointerEventPass.Initial`** over the editor (not an edge swipe; edges are
   the system Back gesture). Start→end opens the library; end→start opens Preview.
9. **Selection toolbar = one custom Compose pill** anchored above the selection; the system floating toolbar is
   suppressed with a menu-clearing `ActionMode.Callback` (keeps handles; verified on emulator).
10. **Signing = dedicated release key in `~/.config/mdwriter/`** created by `make keystore` (auto on first
    `make install`). The debug key is machine-specific; losing it forces an uninstall = losing notes.

## 5. Dependency graph (manual DI)

```kotlin
class AppContainer(app: Application) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)   // saves outlive ViewModels
    val dispatchers = AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main)
    val settings: SettingsRepository            // DataStore Preferences "settings"
    val positions: PositionStore                // caret/scroll per document (DataStore "positions", LRU 200)
    val internalStore: InternalStore            // filesDir/library
    val recovery: RecoveryStore                 // noBackupFilesDir/recovery
    val library: LibraryRepository              // routes Internal / Tree locations (+ SafTreeStore instances)
    val documents: DocumentRepository           // load/save/stat with TextCodec + recovery + conflict detection
    val autosave: AutosaveCoordinator           // debounce policy only; persists through an AutosaveTarget (T11)
    // T14 adds: safIo, treeGrants.  T18 adds: externalStore, intentHandler, shareOut, exporter.
}
// Blocking I/O always runs on dispatchers.io inside the repositories (applicationScope itself stays on Default).
```
ViewModels are created with the lifecycle `viewModel { … }` initializer DSL, reading `(application as MdWriterApp).container`.
No ViewModel holds a `View`, `Context` or `Activity`.

## 6. Contracts between layers

### 6.1 `:core:markdown` (from `plans/reference/markdown/`, package renamed to `dev.mdwriter.markdown`)
```kotlin
class MarkdownHighlighter(enableHighlight: Boolean = true, enableFrontMatter: Boolean = true) {
    fun fullScan(text: CharSequence): HighlightDelta
    fun update(text: CharSequence, changeStart: Int, removedLen: Int, addedLen: Int): HighlightDelta  // per edit
    fun update(text: CharSequence): HighlightDelta        // diff fallback (O(n)); use only defensively
    fun spans(): List<MdSpan>                               // all, sorted (tests / stats)
    fun spansForLines(fromLine: Int, endLine: Int): List<MdSpan>  // endLine EXCLUSIVE; absolute offsets
    fun lineInfo(line: Int): LineInfo; fun lineInfoAt(offset: Int): LineInfo; fun lineIndexOf(offset: Int): Int
    fun lineStart(line: Int): Int; fun lineEnd(line: Int): Int /* excl. '\n' */; val lineCount: Int
    fun headings(): List<HeadingItem>; fun isFenceUnclosed(line: Int): Boolean
    fun setText(text: CharSequence): HighlightDelta          // alias of fullScan
}
data class HighlightDelta(val firstLine: Int, val endLine: Int /*excl*/, val startOffset: Int, val endOffset: Int, val full: Boolean)
```
- **Not thread-safe; keeps a reference to the text.** After a document is installed, it is used on the **main thread
  only**. The initial `fullScan` for a new document may run on `Dispatchers.Default` before the text is handed to the
  view (hand-off is explicit; no concurrent use).
- The app constructs it with `enableHighlight = settings.highlightSyntax` (default **false**; `==mark==` is an iA
  extension) and `enableFrontMatter = true`.
- Offsets are UTF-16 indices; `\n` is the only line break (TextCodec normalizes CRLF/CR on load). No span crosses `\n`.
- `SmartEdit` functions take `(text: String, selStart, selEnd …)` and return `TextEdit(start, end, replacement,
  selStart, selEnd)` (null = do the default thing). Apply with `edit.minimize(text)`. T04 adds
  `enum class ListKind { BULLET, ORDERED, TASK }`, `SmartEdit.toggleList(text, a, b, kind)`, `toggleCodeBlock(text, a, b)`,
  `codeToggle(text, a, b)` (inline backticks, or a fenced block when multi-line / on a fence) and
  `clearFormatting(text, a, b, enableHighlight = false)`, all `: TextEdit`.
- Toolbar mapping (T08 `FormatCommands`): Bold/Italic/Strike/Highlight = `toggleWrap` `"**"`/`"*"`/`"~~"`/`"=="`;
  Code = `codeToggle`; CodeBlock = `toggleCodeBlock`; lists = `toggleList(ListKind.*)`; Clear =
  `clearFormatting(enableHighlight = highlightEnabled)`; Quote = `toggleQuote`; Heading = `cycleHeading`/`setHeading`;
  Link = `insertLink`.
- `TextStats.compute(text, from, to, spans): Stats`; reading time uses 238 wpm.
- `MarkdownHtml(allowRawHtml = true).renderPage(markdown, themeClass, cssVars, title)`; `themeClass` is `"light"`,
  `"dark"` or `"dark black"` (02 §8). The page has a viewport meta, a CSP (`script-src 'none'`; font/img/style-src only
  `https://appassets.androidplatform.net`) and links `/assets/preview/preview.css`. `headingCount(markdown): Int` (T16).
- `DocTitle.fromContent(text): String?`, `DocTitle.excerpt(text): String?` (THE excerpt rule — T10/T12/T14 call it, never
  re-implement) and `DocTitle.sanitizeFileName(name): String`, which never returns "" (falls back to `FALLBACK_NAME` =
  "Untitled"), so validate blank user input BEFORE sanitizing. Constants `FALLBACK_NAME`, `MAX_NAME_LENGTH`, `EXCERPT_MAX` (T04).

### 6.2 Editor engine (`dev.mdwriter.editor`) — the only thing the UI touches is `EditorController`
```kotlin
class EditorController(context: Context, initialStyle: EditorStyle) {
    val scrollView: EditorScrollView          // create once; hosted by AndroidView(factory = { controller.scrollView })
    val editText: MarkdownEditText
    val style: EditorStyle                    // current style (T05)
    val selection: StateFlow<SelectionState>  // T09: (visible, anchor: RectF in scrollView coords, start, end); start/end valid when hidden
    val edits: SharedFlow<EditEvent>          // T07: one per text change (typing, IME, undo, toolbar): (version: Long)
    val version: Long                         // T07: +1 on every text change AND on install (install emits NO EditEvent)
    val scrollChanges: SharedFlow<ScrollChange>   // T13: ScrollChange(y, dy), from EditorScrollView.onScrollChanged
    val highlightEnabled: Boolean; val isReadOnly: Boolean   // T08

    suspend fun install(doc: InstallRequest)  // text + selection + scrollY; builds styled text off-main, setText on main
    fun snapshot(): String                    // editable.toString() (main thread)
    fun statsInput(): StatsInput              // T15: (text, spans, selStart, selEnd, version) snapshot for stats
    fun apply(edit: TextEdit)                 // T08: one undo step, batch edit, selection from the edit
    fun perform(action: ToolbarAction)        // T08: formatting / clipboard actions (uses SmartEdit)
    fun toggleTaskAt(offset: Int)             // T08
    fun canPaste(): Boolean; fun refreshAccessibilityActions()   // T09
    fun setStyle(style: EditorStyle)          // theme/font/size/line-length/highlightSyntax change (T05; T07 restyle; T19 in-place policy)
    fun setHighlightSyntax(enabled: Boolean)  // T19: same code path as setStyle(style.copy(highlightSyntax = enabled))
    var focusMode: FocusModeKind              // T15 (enum from T11, editor/FocusModeKind.kt): Off | Sentence | Paragraph
    var typewriter: Boolean                   // T15
    fun undo(); fun redo(); val canUndo: StateFlow<Boolean>; val canRedo: StateFlow<Boolean>   // T08
    fun collapseSelection(); fun hideIme(); fun showIme(); fun requestFocus(); fun hasFocus(): Boolean   // T05
    fun caret(): Int; fun scrollY(): Int      // T05
    // T17 (search runs on Dispatchers.Default): FindResult(count, index, truncated), FindResult.NONE
    val findResult: StateFlow<FindResult>; suspend fun find(query: String, matchCase: Boolean): FindResult
    fun findNext(); fun findPrevious(); fun replaceCurrent(replacement: String): Boolean
    suspend fun replaceAll(replacement: String): Int; fun clearFind(selectFocused: Boolean = false); fun selectedTextForFind(): String?
    fun release()
}
data class InstallRequest(val text: String, val selection: Int, val scrollY: Int, val readOnly: Boolean)
```
- `EditorController` is created with `remember { }` inside the composition (the activity is not recreated for the
  config changes we handle). If the activity *is* recreated (font scale / locale), the ViewModel re-issues the last
  `InstallRequest` with the current text taken from the recovery copy / disk (see §8).
- The highlighter, restyler, undo manager and `MdEditable` are private to the engine (`highlighter` and `isRestyleIdle`
  are `internal`, for tests). A task that adds a member listed above creates it; later tasks only use it.
- `install()` bumps `version` but emits no `EditEvent`; that version is the autosave baseline (T11). It also clears
  undo, records `readOnly`, and (T17) clears the find session.
- Read-only: T05/T06 only set `showSoftInputOnFocus = !readOnly`. **T08 owns enforcement** (IC wrapper drops commits,
  printable/Enter/Del keys consumed, cut/paste/undo/redo/apply/perform no-op; select + copy still work).
- `hideIme/showIme` use the platform `editText.windowInsetsController` (minSdk 36; no Window needed).
- Never scroll the EditText (`MarkdownEditText.scrollTo` is pinned to 0,0); scroll `scrollView`. Caret keeping goes
  through `bringPointIntoView` (T15 overrides it for typewriter). One `setPadding` call site (T05; T15 adds a branch).
- Accessibility actions: `ViewCompat.addAccessibilityAction` on the EditText only, never `setAccessibilityDelegate`.
- Shortcuts: editor ones (Ctrl+Z, Shift+Z, Y, B, I, K, Shift+C, Shift+X, 0–6) in `MarkdownEditText.onKeyShortcut` (T08),
  which returns `super` for anything else; app ones are `AppCommand`s in `ui/root/AppCommands.kt`, dispatched from
  `MainActivity.onKeyShortcut` into the `commands` flow (T13: Ctrl+N, Ctrl+O/L; T16: Ctrl+R; T17: Ctrl+F). T20's
  `ShortcutCatalog` lists both.

### 6.3 Data layer (`dev.mdwriter.data`)
```kotlin
sealed interface LocationId { data object Internal : LocationId; data class Tree(val treeUri: String) : LocationId }
sealed interface DocRef {
    data class InternalFile(val relPath: String) : DocRef                       // '/'-separated, relative to filesDir/library
    data class TreeDoc(val treeUri: String, val documentId: String) : DocRef    // SAF linked folder
    data class External(val uri: String, val writable: Boolean) : DocRef        // opened from another app (VIEW/EDIT)
}
data class FolderRef(val location: LocationId, val id: String)  // Internal: rel path ("" = root); Tree: documentId
@JvmInline value class DocKey(val value: String)               // stable string id: "i:<relPath>" | "t:<tree>|<docId>" | "x:<uri>"
fun DocRef.key(): DocKey

data class FileStat(val lastModified: Long?, val size: Long?)
data class LibraryEntry(val name: String, val isFolder: Boolean, val doc: DocRef?, val folder: FolderRef?,
                        val lastModified: Long?, val size: Long?, val excerpt: String?,
                        val caps: EntryCaps = EntryCaps.ALL)   // caps: T10 addition

interface DocumentStore {                       // implemented by InternalStore and SafTreeStore
    suspend fun list(folder: FolderRef): List<LibraryEntry>
    suspend fun read(ref: DocRef): ByteArray
    suspend fun write(ref: DocRef, bytes: ByteArray)         // atomic (internal) / "wt" + verify (SAF)
    suspend fun stat(ref: DocRef): FileStat?                 // null = gone
    suspend fun displayName(ref: DocRef): String             // T10 addition: name incl. extension
    suspend fun create(folder: FolderRef, displayName: String): DocRef
    suspend fun createFolder(parent: FolderRef, name: String): FolderRef
    suspend fun rename(ref: DocRef, newDisplayName: String): DocRef   // ALWAYS use the returned ref afterwards
    suspend fun trash(ref: DocRef): TrashToken
    suspend fun restore(token: TrashToken): DocRef?          // null if the store cannot restore
    suspend fun move(ref: DocRef, to: FolderRef): DocRef
    fun changes(folder: FolderRef): Flow<Unit>               // best-effort change notifications
}
data class TrashToken(val original: DocRef, val displayName: String, val trashId: String)   // T10 addition
sealed interface StorageError { NotFound; PermissionLost; ReadOnly; TooLarge(bytes); Encoding; ProviderFailure(cause) }
class StorageException(val error: StorageError, cause: Throwable? = null) : java.io.IOException  // T10 addition: stores throw this
```
- All store I/O runs on `Dispatchers.IO`. Writes to one document are serialized with a per-document `Mutex`.
- `DocumentRepository.load(ref): LoadedDocument(text, baseline: FileStat, format: TextFormat, readOnly)` and
  `save(ref, text, baseline, format): SaveResult` (`Saved(newBaseline)` | `Conflict(onDisk)` | `Failed(error)`).
  T11 extends: `load(ref, useRecovery = true)`; `LoadedDocument` also has `ref`, `displayName`, `large`, `recovered`,
  `diskTextIfConflict`. Null-mtime providers: in-memory sha1 map (size-only after process death).
- T10 additions: `DocumentStore.displayName(ref): String`, `LibraryEntry.caps: EntryCaps`, `TrashToken(original,
  displayName, trashId)`, `TrashBin.copyIn(displayName, bytes, meta)` (T14 uses meta `source=tree`; the 30-day purge
  must accept it). Implementations: InternalStore (T10), SafTreeStore (T14), ExternalDocStore (T18).
  `TextFormat` remembers BOM + dominant line ending so saving round-trips the file byte-for-byte when unchanged.
- **Autosave** (`AutosaveCoordinator`, pure Kotlin, virtual-time tested): 1 s idle debounce (2 s for SAF), plus a
  10 s max latency while typing continuously; `flush()` on `ON_STOP`, document switch, `onNewIntent`, drawer open.
  Runs in `applicationScope` with `NonCancellable` so a finishing activity never drops the last save. The coordinator
  never sees documents: it calls an `AutosaveTarget` (snapshot/persist) implemented by `EditorViewModel` (T11);
  `begin/onEdit/flush/resume/end` run on a `limitedParallelism(1)` dispatcher; `idleMsFor(TreeDoc)` = 2 s. Never writes
  if the version did not change. On failure: keep dirty, keep the recovery copy, retry with backoff 1/2/5/10 s.

### 6.4 UI state
```kotlin
data class EditorUiState(
    val doc: DocRef?, val title: String, val loading: Boolean, val readOnly: Boolean,
    val save: SaveState,                         // Clean | Dirty | Saving | Error(message)
    val conflict: ConflictState?,                // "Changed on disk — Reload · Keep mine · Save both"
    val stats: Stats?,                           // null when word count is off
    val statsSelection: Boolean = false,         // T15: stats are for the selection
    val chromeVisible: Boolean,                  // glyph buttons (fade while typing)
    val drawerOpen: Boolean, val previewOpen: Boolean, val findOpen: Boolean, val settingsOpen: Boolean,
)
```
- `ConflictState` = sealed `{ ChangedOnDisk(diskText, diskBaseline); Gone }`, `ConflictAction { Reload, KeepMine, SaveBoth,
  SaveAsNew, Close }` (T11). `settingsOpen` = `sheet != RootSheet.None`, `RootSheet { None, Settings, About }` (T19).
  Large-document / refusal notices go out as `EditorEvent.Message` (T11). `Settings` = T11 §A (reuses T02's `ThemeMode`
  and `WriterFont`; never redeclare them).
- Root: `MdWriterRoot(container, commands: SharedFlow<AppCommand>, launch: RoutedIntent, newIntents: Flow<RoutedIntent>)`
  (T11 creates it with `container`, T13 adds `commands`, T18 adds the intent params). `RootBackHandlers` is composed last
  (T13); drawer, preview and find are mutually exclusive (opening one closes the others); selection-collapse wins.
- Extension points: `OverflowActions` (T13) fields `onFind` (T17), `onShare` (T18), `onPreview` (T16), `focus`/
  `typewriter`/`wordCount` (T15), `onSettings` (T19); `EditorChrome` `stats` slot (T15); `PreviewSlot()` (T16);
  `PreviewOverlay(onShare)` (T18); library `rowMenuExtras` (T18 Share); `rememberExportAllNotes(exporter, session)` (T18),
  used by T19's Settings row and the "On this device" long-press.

Single `StateFlow<EditorUiState>` via `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`; collected with
`collectAsStateWithLifecycle()`. One-shot events (install requests, snackbars) go through a `Channel`.

## 7. Threading model

| Work | Thread |
|---|---|
| Keystroke → `highlighter.update` (≈0.01 ms) → mark dirty | main, inside `TextWatcher.onTextChanged` |
| Span reconcile | main, `Choreographer` frame callback, ≤ 4 ms budget per frame, visible lines first |
| Open document: decode + `fullScan` + build styled `SpannableStringBuilder` | `Dispatchers.Default` |
| `setText(styled)` | main (unavoidable DynamicLayout build: ~0.1–0.3 s at 300k chars) |
| Snapshot for save | main (`toString()`, O(n) copy) |
| Encode + write | `Dispatchers.IO` in `applicationScope` |
| Stats | debounce 400 ms, snapshot on main, compute on `Default` |
| Preview HTML | `Default`; WebView load on main |
| SAF listing | `IO`, one `ContentResolver.query` per folder with a full projection |

## 8. State & process death

- Source of truth for text = **disk + recovery copy**, never the Bundle. `MarkdownEditText.isSaveEnabled = false`
  (EditText would otherwise freeze the whole document into the Bundle → `TransactionTooLargeException`).
- `SavedStateHandle` stores only `docKey`, selection, scrollY, and which overlay is open.
- `PositionStore` remembers caret + scroll per document so reopening a note returns to where you were.
- On restore: load from disk; if `recovery/<hash(docKey)>.md` is newer/different, prefer it, mark dirty, save.
- `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|keyboard|keyboardHidden|navigation|uiMode"`
  keeps the activity (and the editor's undo stack/IME/scroll) alive across rotation, resizing, hardware keyboards
  and dark-mode switches. Font scale / locale changes still recreate → the restore path above must work (tested in T20).
- `launchMode="singleTask"`; new intents arrive via `addOnNewIntentListener`.

## 9. Performance budgets (measured on the emulator in T21 — debug app after `cmd package compile -m speed -f`, JIT
numbers recorded too; T21 writes a protocol for the user to repeat on their phone)

| Metric | Budget |
|---|---|
| Per-keystroke main-thread work (edit + restyle + frame UI) at 100k chars | median ≤ 8 ms, p90 ≤ 12 ms |
| Same at 300k chars | median ≤ 12 ms |
| `highlighter.update` p95 at 100 KB | < 1 ms |
| Open a 100k-char document to first styled frame | ≤ 1 s warm (emulator research measured 0.6–1.2 s incl. the unavoidable DynamicLayout build); chrome/placeholder must show within 100 ms |
| Cold start to editable last document (release, baseline profile installed) | ≤ 800 ms |
| Documents > 1 MB | open with a "Large document" notice; > 5 MB: read-only plain; binary (NUL in first 8 KB): refuse |

## 10. HARD RULES (each one is a real bug found during research — do not "simplify" them away)

1. Never apply `org.jetbrains.kotlin.android`; never add AppCompat/MDC; never add the `INTERNET` permission.
2. Never call `EditText.setPadding` (or anything that nulls the layout: `setTextSize`, `setLineSpacing`,
   `setTypeface`) in response to the IME showing/hiding or on scroll. Only on width change or a settings change.
   Apply IME/system-bar insets to the **Compose container**, not the EditText.
3. Never wrap span reconcile in `beginBatchEdit()/endBatchEdit()` (endBatchEdit scrolls to the caret). Do use batch
   edits around *text* edits we make (toolbar, undo, smart Enter, task toggle).
4. A span covering the whole document must **not** implement `UpdateLayout` (it would force two full reflows per
   keystroke). Per-line paragraph spans (`LeadingMarginSpan` for quotes/lists/heading hang) **must** implement
   `UpdateLayout`, or DynamicLayout will not reflow when they are added/removed.
5. Only ever remove spans that implement our `MdStyleSpan` marker interface. Never touch composing, `SuggestionSpan`,
   `SpellCheckSpan`, selection or watcher spans. Never call `setText`/`clearSpans` while editing (only on install).
6. Our span classes are custom subclasses of `CharacterStyle` / `MetricAffectingSpan` / `LeadingMarginSpan` /
   `LineBackgroundSpan` — **not** framework `ParcelableSpan`s (those leak into the clipboard and cost bookkeeping).
   `TaskSpan` must not be a `NoCopySpan` (the SSB copy constructor drops those).
7. Span flags: `SPAN_EXCLUSIVE_EXCLUSIVE`. Never `SPAN_PARAGRAPH`. ONE exception: T06's document-wide `HangRoomSpan`
   (gutter `LeadingMarginSpan`) is `SPAN_INCLUSIVE_INCLUSIVE` so text typed at 0 / at the end stays inside it; it is set
   once on install, never reconciled, not `UpdateLayout`, not an `MdStyleSpan`.
8. `MarkdownEditText.isSaveEnabled = false`; no view ID on it.
9. The Android marker interface is called **`MdStyleSpan`** (the `:core:markdown` data class is `MdSpan`; don't clash).
10. `customSelectionActionModeCallback` must return **true** from `onCreateActionMode` and clear the menu in **both**
    `onCreateActionMode` and `onPrepareActionMode`. Returning false kills the selection; returning null from
    `startActionMode` hides the handles.
11. Clipboard: copy/cut put a plain `String`; paste maps to `android.R.id.pasteAsPlainText`.
12. Never use `systemGestureExclusion` to steal the Back edge.
13. Never uninstall the app automatically, never create the user's real signing key from an agent session, and never
    install onto a physical device from an agent session (see README "Rules for agents"). Only exception: T21/T22 may
    remove the throwaway-signed release app from the **emulator** with `make uninstall CONFIRM=yes`. Agent release
    builds share `KEYSTORE_DIR=/tmp/mdwriter-agent-key`.
14. `.gitignore` comments go on their own line (`# ...` after a pattern is part of the pattern).
15. SAF writes use mode `"wt"` (plain `"w"` may not truncate → corrupted files). `createDocument` with MIME
    `text/markdown` for `.md` (with `text/plain` you get `Note.md.txt`).

## 11. Testing strategy

| Layer | Where | How | Command |
|---|---|---|---|
| Markdown engine | `:core:markdown` | JUnit 6: curated cases, typing sequences, fuzz (seed 7 × 3,000), CommonMark spec differential vs commonmark-java (≥ 641/652, 11 pinned ids), pathological timing | `make test` |
| Pure app logic | `:app` `src/test` | JUnit4 + Truth: `DirtyRange`, `AutosaveCoordinator` (virtual time), `TextCodec`, `AtomicWriter`, `InternalStore` (TemporaryFolder), focus range math, swipe classifier | `make test` |
| Android glue | `:app` `src/test` + Robolectric 4.17 | SafTreeStore vs a test `DocumentsProvider`, IntentRouter, SettingsRepository, EditorViewModel with fakes + Turbine | `make test` |
| Editor on device | `:app` `src/androidTest` | incremental-layout == full-reflow equality test, restyle correctness, selection toolbar, swipe, IME/caret visibility | `make test-device DEVICE=emulator-5554` |
| Look & feel | emulator | screenshots via `adb exec-out screencap -p` checked by the agent (Read the PNG) | manual in each UI task |

Prefer fakes over mocks (no mockk).

## 12. Build & install (see README for commands)

`make install` = device preflight → `./gradlew :app:installRelease` (installs APK **and** the baseline-profile `.dm`)
→ launch. Release versionCode = minutes since 2026-01-01 (monotonic, configuration-cache-safe). Debug builds are a
separate app (`dev.mdwriter.debug`) with separate notes.
