# T18 — Open from other apps, share in/out, export all notes

**Goal** Open `.md`/`.txt` files from Files, Drive, mail and so on. Writable ones autosave in place; read-only ones
show "Read-only · Save a copy to Library". Text shared from any app becomes a new note. The current note or a library
row can be shared out as a real `.md` file. "Export all notes…" zips the internal library to a place the user picks,
which is the backup path for release installs (research/build.md §9).

**Depends on**
- T14 (and through it T10–T13): `SafIo` (`writeWt`, `statRow`, `displayName`), `TestDocumentsProvider`;
  `LibraryRepository.storeFor(ref)`/`locations`/`rootOf`/`createUnique`; `DocumentSession` (`open`, `flush`, T12);
  T11's `start()`, `EditorEvent.Message`, `Settings.recentExternal`; T12's `rowMenuExtras`; T13's `OverflowMenu`
  share icon; T16's preview (share glyph, if any).
- T10: `DocumentStore` (the header comment already names `ExternalDocStore (T18)`), `TextCodec`, `NoteFiles`,
  `StorageLimits`, `StorageException`.

**Read first**
- `plans/01-architecture.md` §3 (`intents/`, `data/export/`), §6.3, §8 (singleTask, `addOnNewIntentListener`), §10 rule 15.
- `plans/02-design-spec.md` §7 (row menu: Share), §9 (overflow icon row: share), §10 ("Export all notes…" row owned by T19).
- `plans/research/platform.md` §3.1–§3.5, §10.7. `plans/research/build.md` §9.
- The STATUS entries of T11–T14 and T16. Grep before editing:
  `grep -rn "onShare\|rowMenuExtras\|SnackbarHostState\|fun start" app/src/main/kotlin/dev/mdwriter/ui`.

## Scope — In / Out
In:
- Manifest intent filters + `FileProvider`, `IntentRouter`, `IntentHandler`, `ExternalDocStore`.
- Recent-external LRU bookkeeping, the read-only pill + "Save a copy", share-in (text and stream).
- `ShareOut` wired to the overflow, the library rows and the preview.
- `ExportAllNotes` + its temporary entry point.
- Debug StrictMode `detectImplicitUriPermissionGrant`.

Out:
- The Settings > Files row for export → **T19** (it calls `rememberExportAllNotes`).
- The rest of StrictMode, config-change hardening → **T20**.
- A "Recent files" UI list → not in v1 (`recentExternal` is grant bookkeeping only).
- Exporting linked SAF folders → not in v1 (they are already user-visible files).
- `ACTION_SEND_MULTIPLE` → not in v1.

## Files to create / modify (all under `app/src/`)
- `main/AndroidManifest.xml` (modify): 3 intent filters (no `BROWSABLE`) + `<provider>` FileProvider.
- `main/res/xml/file_paths.xml` (new): `<paths><cache-path name="exports" path="exports/"/></paths>`.
- `main/kotlin/dev/mdwriter/intents/IntentRouter.kt`: `RoutedIntent` + `IntentRouter.parse(intent)`.
- `main/kotlin/dev/mdwriter/intents/IntentHandler.kt`: `RoutedIntent` → `IntentOutcome` (creates/imports/adopts).
- `main/kotlin/dev/mdwriter/intents/SharedNote.kt`: pure `compose(subject, text)` → `SharedNote(baseName, body)`.
- `main/kotlin/dev/mdwriter/intents/ShareOut.kt`: copies to `cacheDir/exports/<uuid>/<name>`; builds the chooser intent.
- `main/kotlin/dev/mdwriter/data/storage/ExternalDocStore.kt`: `DocumentStore` for `DocRef.External` + `adopt`/`refresh`.
- `main/kotlin/dev/mdwriter/data/settings/RecentList.kt`: pure LRU `push(list, item, cap)`.
- `main/kotlin/dev/mdwriter/data/export/ExportAllNotes.kt`: `writeZip` (pure) + `start(uri)` + `status`.
- `main/kotlin/dev/mdwriter/ui/editor/ReadOnlyPill.kt`: the pill composable.
- `main/kotlin/dev/mdwriter/ui/library/ExportAllNotesAction.kt`: `rememberExportAllNotes(...)` + `ExportProgress` bar.
- `main/kotlin/dev/mdwriter/MainActivity.kt` (modify): parse the launch intent + `addOnNewIntentListener`.
- `main/kotlin/dev/mdwriter/MdWriterApp.kt` (modify): debug StrictMode VM policy (API 37 guard).
- `main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt` (modify): `launch: RoutedIntent`, `newIntents: Flow<RoutedIntent>`.
- `main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt` (modify): `startWith`, `handle`, `saveCopyToLibrary`, External restore.
- `main/kotlin/dev/mdwriter/ui/editor/EditorScreen.kt`, `OverflowMenu.kt` (modify): pill + share wiring.
- `main/kotlin/dev/mdwriter/ui/library/LibraryContent.kt` (modify): "Share" in `rowMenuExtras`; the export entry point.
- `main/kotlin/dev/mdwriter/data/library/LibraryRepository.kt` (modify): `storeFor(External)` → `externalStore`.
- `main/kotlin/dev/mdwriter/AppContainer.kt` (modify): `externalStore`, `intentHandler`, `shareOut`, `exporter`.
- Tests:
  - `test/kotlin/dev/mdwriter/intents/IntentRouterTest.kt` (Robolectric)
  - `test/kotlin/dev/mdwriter/intents/SharedNoteTest.kt`
  - `test/kotlin/dev/mdwriter/intents/ShareOutTest.kt` (Robolectric)
  - `test/kotlin/dev/mdwriter/data/storage/ExternalDocStoreTest.kt` (Robolectric + `TestDocumentsProvider`)
  - `test/kotlin/dev/mdwriter/data/settings/RecentListTest.kt`
  - `test/kotlin/dev/mdwriter/data/export/ExportAllNotesTest.kt`
  - extend `EditorViewModelTest`

## Steps
1. Read the STATUS entries (T11–T14, T16). Run `make test` and confirm it is green.
2. Manifest (Reference A) + `file_paths.xml`. Build, then check the filters with `pm query-activities` (Verification).
3. `IntentRouter` (Reference B) + `IntentRouterTest`.
4. `SharedNote` + `RecentList` + their JVM tests.
   - `SharedNote.compose(subject, text)`:
     - Normalize to LF (`TextCodec.normalizeToLf`).
     - If `subject` is non-blank AND the body does not already start with `#` AND the trimmed body != subject →
       body = `"# $subject\n\n$text"`.
     - Ensure one trailing `\n`.
     - `baseName = DocTitle.sanitizeFileName(DocTitle.fromContent(body) ?: "Shared note")`.
   - `RecentList.push(list, item, cap = 100): Pair<List<String>, List<String>>` returns (newList with item first and
     de-duplicated, evicted).
5. `ExternalDocStore(context, safIo, settings, io)` (Reference C) + test.
   - Supported: `read` (capped, `openInputStream`), `write`, `stat`, `displayName`.
     - `write` → `SafIo.writeWt`, only when `ref.writable`, else `StorageException(ReadOnly)`.
     - `stat`: query with a null projection. `OpenableColumns.SIZE`, plus `Document.COLUMN_LAST_MODIFIED` if that
       column exists. A `SecurityException` → `PermissionLost`.
     - `displayName`: `OpenableColumns.DISPLAY_NAME`, fallback `uri.lastPathSegment ?: "Untitled.md"`.
   - `list` → `emptyList()`; `changes` → `emptyFlow()`.
   - `create`/`createFolder`/`rename`/`move`/`trash` → `StorageException(ReadOnly)`; `restore` → null.
   - Route `DocRef.External` to it in `LibraryRepository.storeFor`.
6. `IntentHandler.resolve(r): IntentOutcome` (Reference D), where
   `sealed interface IntentOutcome { data class Open(val ref: DocRef, val showIme: Boolean); data class Message(val text: String); data object Nothing }`.
7. `EditorViewModel` changes:
   - `start()` gains `(initial: DocRef? = null, initialShowIme: Boolean = false)`. When `initial` is non-null, the
     first-launch Welcome file is still created (not opened), and `initial` replaces steps 3–5.
   - `fun startWith(launch: RoutedIntent)` = resolve, then `start(initial…)`. A `Message` outcome → `start()` + message.
   - `fun handle(r: RoutedIntent)` (new intents): `flush()` FIRST (01 §6.3), then resolve, then
     `open(ref, showIme, leaveCurrent = true)` or the message.
   - Restore path: when `lastOpenDoc`/`handle["docKey"]` is `x:` → `externalStore.refresh(ref)` recomputes `writable`.
     A failure falls back to the next startup rule, with `Message("Access to ‘<name>’ ended — open it again from the other app")`.
   - `fun saveCopyToLibrary()`:
     1. Take the snapshot text.
     2. `library.createUnique(rootOf(Internal), baseName, ext-or-"md")`.
     3. Write with the loaded `TextFormat`.
     4. `open(copy, showIme = false, leaveCurrent = false)` at the same caret.
     5. `Message("Saved a copy to Library")`.
8. `MainActivity` + `MdWriterRoot` (Reference E). Replace T11's `LaunchedEffect(Unit) { vm.start() }` with
   `vm.startWith(launch)` and collect `newIntents` → `vm.handle`.
9. `ReadOnlyPill`:
   - Top-centre, under the status-bar inset, 32 dp tall, fully rounded.
   - `surface` background, 1 dp hairline border, 13 sp.
   - Text: "Read-only · " in `textSecondary`, then "Save a copy to Library" in `accent` (the whole pill is the tap
     target, ≥ 48 dp touch height via `minimumInteractiveComponentSize`).
   - Visible iff `uiState.readOnly && uiState.doc is DocRef.External`.
   - It sits in the same top slot as `ConflictBanner`; the banner wins if both apply.
10. `ShareOut` (Reference F) + `ShareOutTest`. Wiring (`LocalContext.current` is the Activity; launch with
    `rememberCoroutineScope`):
    - Overflow share icon → `session.flush()` → `shareOut.intentFor(current)` → `context.startActivity(it)`.
    - Library row menu `rowMenuExtras`: "Share" (`ic_share`) for file rows only, placed before Delete. If the row is
      the open doc, flush first.
    - Preview: if T16 exposes an `onShare` callback, pass the same lambda. Otherwise record "no preview share glyph"
      in STATUS.
    - Errors → snackbar "Couldn't share: <userMessage>".
11. `ExportAllNotes` (Reference G) + `ExportAllNotesTest`.
    - `rememberExportAllNotes(exporter, session)` returns `() -> Unit`:
      1. `session.flush()`.
      2. Launch `ActivityResultContracts.CreateDocument("application/zip")` with `exporter.suggestedName()`.
      3. The result URI → `exporter.start(uri)`.
    - `ExportProgress(status)`: a 2 dp `LinearProgressIndicator` (`accent`) under the drawer header while `Running`.
    - Snackbars (the drawer's existing `SnackbarHostState`): Done → "Exported 40 notes"; Failed → "Export failed — <msg>".
    - **Entry point until T19 (decided):** long-press the "On this device" row in the drawer's Locations section. A
      menu with one item, "Export all notes…", appears. This uses the same menu component as T14's linked-folder
      long-press. It stays after T19 (it is harmless).
12. Debug StrictMode (Reference H).
13. `make format && make check`, then do the manual checks and write the STATUS entry.

## Reference code
**A. Manifest additions (from platform §3.2; `BROWSABLE` removed, as decided there)**
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW"/><action android:name="android.intent.action.EDIT"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:scheme="content"/>
    <data android:mimeType="text/markdown"/><data android:mimeType="text/x-markdown"/><data android:mimeType="text/plain"/>
</intent-filter>
<intent-filter>  <!-- fallback: sender says octet-stream or */*; pathSuffix needs scheme+host -->
    <action android:name="android.intent.action.VIEW"/><action android:name="android.intent.action.EDIT"/>
    <category android:name="android.intent.category.DEFAULT"/>
    <data android:scheme="content" android:host="*" android:mimeType="*/*"/>
    <data android:pathSuffix=".md"/><data android:pathSuffix=".markdown"/>
    <data android:pathSuffix=".mdown"/><data android:pathSuffix=".mkd"/>
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.SEND"/><category android:name="android.intent.category.DEFAULT"/>
    <data android:mimeType="text/plain"/><data android:mimeType="text/markdown"/><data android:mimeType="text/x-markdown"/>
</intent-filter>
<!-- inside <application> -->
<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.files"
    android:exported="false" android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
</provider>
```
**B. IntentRouter (sketch; the rules are exact)**
```kotlin
sealed interface RoutedIntent {
    data class OpenExternal(val uri: Uri, val wantsWrite: Boolean, val persistable: Boolean) : RoutedIntent
    data class ShareText(val subject: String?, val text: String) : RoutedIntent
    data class ShareStream(val uri: Uri) : RoutedIntent
    data object None : RoutedIntent
}
object IntentRouter {
    fun parse(intent: Intent?, ownAuthority: String? = null): RoutedIntent {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return RoutedIntent.None
        fun Uri.ok() = scheme == ContentResolver.SCHEME_CONTENT && authority != ownAuthority
        return when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> intent.data?.takeIf { it.ok() }?.let {
                RoutedIntent.OpenExternal(it,
                    wantsWrite = intent.action == Intent.ACTION_EDIT || intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
                    persistable = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
            } ?: RoutedIntent.None
            Intent.ACTION_SEND -> {
                val stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.takeIf { it.ok() }
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotEmpty() }
                when { stream != null -> RoutedIntent.ShareStream(stream)
                       !text.isNullOrBlank() -> RoutedIntent.ShareText(subject, text); else -> RoutedIntent.None }
            }
            else -> RoutedIntent.None
        }
    }
}
```
`ownAuthority` = `packageName + ".files"`, so our own exports are never re-imported.

**C. ExternalDocStore.adopt / refresh (sketch)**
```kotlin
suspend fun adopt(uri: Uri, persistable: Boolean): DocRef.External = withContext(io) {
    val writable = context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED                          // VIEW grants are often read-only (platform §3.3)
    if (persistable) runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
    }.onSuccess { remember(DocRef.External(uri.toString(), writable).key()) }   // RecentList.push, cap 100;
    DocRef.External(uri.toString(), writable)                                    // release grants of evicted keys
}
suspend fun refresh(ref: DocRef.External): DocRef.External   // recompute writable; throws PermissionLost if stat fails
```
**D. IntentHandler.resolve (sketch)**
- `OpenExternal`:
  1. `treeDocFor(uri)`: for each Ready `LocationId.Tree` with the same authority,
     `runCatching { DocumentsContract.isChildDocument(resolver, buildDocumentUriUsingTree(tree, getTreeDocumentId(tree)), uri) }`.
     True → `TreeDoc(tree, getDocumentId(uri))`.
  2. Otherwise `externalStore.adopt(uri, persistable)`.
  3. `Open(ref, showIme = false)`.
- `ShareText`:
  1. `SharedNote.compose`.
  2. `library.createUnique(rootOf(Internal), baseName, settings.newNoteExtension)`.
  3. Write `TextCodec.encode(body, TextFormat.DEFAULT)`.
  4. `Open(ref, showIme = true)`. Not added to `autoNamed` (it is already named).
- `ShareStream`:
  1. Name via `OpenableColumns.DISPLAY_NAME` (if not `NoteFiles.isSupported` → `"<base>.md"`).
  2. Read the bytes (≤ `MAX_OPEN_BYTES`, else `Message("Too large for mdwriter")`).
  3. `TextCodec.decode` gives `Binary` → `Message("Only text files can be imported")`.
  4. Create a unique name in the internal root, write the bytes **unchanged**.
  5. `Open(ref, false)`.
- A `SecurityException`/`StorageException` → `Message(userMessage)`.

**E. MainActivity / root (sketch)**
```kotlin
private val newIntents = Channel<RoutedIntent>(Channel.BUFFERED)
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)                      // keep T01/T02 lines (enableEdgeToEdge, splash)
    val own = "$packageName.files"
    val launch = if (savedInstanceState == null) IntentRouter.parse(intent, own) else RoutedIntent.None  // no re-import on recreation
    addOnNewIntentListener { newIntents.trySend(IntentRouter.parse(it, own)) }
    setContent { MdWriterRoot(container, commands, launch, newIntents.receiveAsFlow()) }   // keep T13's `commands` + onKeyShortcut
}
// MdWriterRoot: LaunchedEffect(Unit) { vm.startWith(launch) }; LaunchedEffect(Unit) { newIntents.collect(vm::handle) }
```
**F. ShareOut.intentFor (sketch; flags are exact, platform §3.4)**
```kotlin
suspend fun intentFor(ref: DocRef): Intent = withContext(io) {
    val store = library.storeFor(ref); val name = store.displayName(ref); val bytes = store.read(ref)
    val root = File(app.cacheDir, "exports").apply { mkdirs(); listFiles()?.filter { now() - it.lastModified() > 86_400_000 }?.forEach { it.deleteRecursively() } }
    val file = File(File(root, UUID.randomUUID().toString()).apply { mkdirs() }, name).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)  // packageName == applicationId (.debug too)
    val send = Intent(Intent.ACTION_SEND).setType(if (NoteFiles.extensionOf(name) == "txt") "text/plain" else "text/markdown")
        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, NoteFiles.baseName(name))
        .setClipData(ClipData.newRawUri(name, uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    (TextCodec.decode(bytes) as? DecodeResult.Text)?.text?.takeIf { it.length <= 100_000 }   // Binder limit (1 MB shared)
        ?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
    Intent.createChooser(send, null)
}
```
**G. ExportAllNotes (sketch)**
- `const val ZIP_ROOT = "mdwriter-notes/"`.
- `fun suggestedName(today: LocalDate = LocalDate.now()) = "mdwriter-notes-$today.zip"` (ISO `YYYY-MM-DD`).
- `writeZip(root, out, onProgress)`, all on IO:
  - Walk `libraryRoot` with `walkTopDown().onEnter { it == root || !it.name.startsWith(".") }` and skip hidden files
    (`.trash` lives outside `library/`, but temp `.x.tmp` files do not). Sort by relative path.
  - Empty folders become `"mdwriter-notes/Drafts/"` entries.
  - Each file becomes `ZipEntry(ZIP_ROOT + rel).apply { time = f.lastModified() }`, copied with a 64 KiB buffer.
  - `onProgress(done, total)` after each file. `zip.finish()`.
  - Returns `ExportSummary(files, bytes)`.
- `start(uri)`:
  - Ignored while `Running`.
  - Runs in `applicationScope` + `NonCancellable`.
  - Opens `resolver.openOutputStream(uri, "wt")`, falling back to `"w"` on `IllegalArgumentException`.
  - Sets `status` = `Running(done, total)` → `Done(summary)` | `Failed(message)`.

**H. Debug StrictMode (MdWriterApp.onCreate; use the same debug gate as `util/Log.kt`)**
```kotlin
if (isDebugBuild && Build.VERSION.SDK_INT >= 37) StrictMode.setVmPolicy(
    StrictMode.VmPolicy.Builder(StrictMode.getVmPolicy()).detectImplicitUriPermissionGrant().penaltyLog().build())
```

## Acceptance criteria
1. `make check` is green. All 6 new test classes pass, plus the new `EditorViewModelTest` cases.
2. `IntentRouterTest`: `viewMarkdownContent` (→ `OpenExternal`), `editWantsWrite`, `persistableFlagRead`,
   `fileSchemeIgnored`, `ownAuthorityIgnored`, `sendTextWithSubject` (→ `ShareText("Idea", …)`),
   `sendBlankTextIsNone`, `sendStreamWinsOverText`, `launchedFromHistoryIsNone`, `mainIsNone`.
3. `SharedNoteTest`: `subjectBecomesH1`, `noDuplicateHeading`, `crlfNormalized`, `singleTrailingNewline`, `baseNameFromFirstLine`.
   `RecentListTest`: `pushFront`, `dedupe`, `capEvictsOldest`.
4. `ExportAllNotesTest` (TemporaryFolder): `keepsFolderStructure` (`mdwriter-notes/Drafts/b.md` present), `skipsHidden`,
   `emptyFolderEntry`, `bytesIdentical` (a CRLF+BOM file survives), `progressReachesTotal`, `suggestedNameIsIsoDate`.
5. `ShareOutTest`: the chooser's `EXTRA_INTENT` is `ACTION_SEND`, type `text/markdown`; `clipData.getItemAt(0).uri ==
   EXTRA_STREAM`; `FLAG_GRANT_READ_URI_PERMISSION` set; the file exists under `cache/exports/`; no `EXTRA_TEXT` at 150k chars.
6. `ExternalDocStoreTest`: `readStatDisplayName`, `writeWhenWritableTruncates`, `writeWhenReadOnlyThrowsReadOnly`, `securityExceptionMapsToPermissionLost`.
7. `EditorViewModelTest`: `shareTextCreatesNoteOpensWithIme`, `newIntentFlushesCurrentFirst`, `readOnlyExternalSaveCopyOpensLibraryCopy`.
8. `pm query-activities` (Verification) lists mdwriter for VIEW `text/markdown` and for VIEW `application/octet-stream`
   with a `.md` path. It does NOT list mdwriter for `application/pdf` `.pdf`, nor for the BROWSABLE `https://…/a.md`.
9. Emulator:
   - The `am start … SEND` command below opens a new note whose first line is `# Shared idea`, with the IME visible.
   - `run-as … ls files/library` shows `Shared idea.md`.
10. Emulator: open `/sdcard/Download/Open me.md` from the Files app. The title is "Open me". Then EITHER edits reach
    the file (`adb shell cat`) OR the read-only pill is visible. Record which in STATUS; "Save a copy" creates a
    library note.
11. Emulator:
    - Overflow › share shows the system sharesheet (screenshot).
    - `adb logcat -d | grep -i "StrictMode" | grep -ci uri` prints `0`.
12. Emulator: long-press "On this device" › Export all notes… › Save in Downloads. The snackbar says "Exported N
    notes". `unzip -l` of the pulled zip lists `mdwriter-notes/Welcome.md`.

## Verification commands
```bash
make check
make install-debug DEVICE=emulator-5554
ADB=~/Library/Android/sdk/platform-tools/adb; S="-s emulator-5554"; P=dev.mdwriter.debug
$ADB $S shell pm query-activities --brief -a android.intent.action.VIEW -t text/markdown -d content://x.y/d/a.md | grep -c $P
$ADB $S shell pm query-activities --brief -a android.intent.action.VIEW -t application/octet-stream -d content://x.y/d/a.md | grep -c $P
$ADB $S shell pm query-activities --brief -a android.intent.action.VIEW -t application/pdf -d content://x.y/d/a.pdf | grep -c $P   # 0
$ADB $S shell pm query-activities --brief -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://example.com/a.md | grep -c $P  # 0
$ADB $S shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.SUBJECT "Shared idea" --es android.intent.extra.TEXT "from adb" -n $P/dev.mdwriter.MainActivity
$ADB $S shell run-as $P ls files/library
printf '# Open me\n\nhello\n' > /tmp/o.md; $ADB $S push /tmp/o.md "/sdcard/Download/Open me.md"
$ADB $S logcat -c   # before tapping share; afterwards:
$ADB $S logcat -d | grep -i StrictMode | grep -ci uri
$ADB $S pull /sdcard/Download/ /tmp/t18-dl && unzip -l /tmp/t18-dl/mdwriter-notes-*.zip
```

## Pitfalls
- **No `BROWSABLE`, no `INTERNET`** (hard rule 1, platform §3.2). MIME strings are lowercase. Path attributes need
  `scheme` + `host="*"`, and the extension filter still needs `mimeType="*/*"` (platform §3.1).
- **Parse the launch intent only when `savedInstanceState == null`**, and ignore `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`.
  Otherwise shared text is re-imported on every recreation or relaunch from Recents.
- **Flush before switching** on a new intent (01 §6.3). `DocumentSession.open` flushes, but `handle` must flush
  before `resolve` too, since resolve may create files.
- Writability is `checkCallingOrSelfUriPermission(…WRITE…)`, never the action alone. Only intents carrying
  `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` can be persisted (catch `SecurityException`). Release evicted grants.
- Share-out: add `FLAG_GRANT_READ_URI_PERMISSION` **and** `ClipData` explicitly (Android 18 stops implicit grants,
  platform §3.4). The FileProvider authority is `${applicationId}.files`: use `packageName` at runtime, never the
  literal `dev.mdwriter.files` (the debug app is `dev.mdwriter.debug`).
- Cap `EXTRA_TEXT` at 100k chars, or the chooser crashes with `TransactionTooLargeException`.
- `Document.FLAG_*`/`trashDocument` API-37 rules from T14 apply. `detectImplicitUriPermissionGrant` is API 37 → guard it.
- Imports copy bytes unchanged (BOM/CRLF kept). Export writes with `"wt"` (hard rule 15).
- `CreateDocument` cannot overwrite; the system appends "(1)". That is fine, and there is no confirmation dialog.

## Definition of done
- [ ] Acceptance 1–12 checked, with screenshots (new shared note, pill or saved external edit, sharesheet, export
      snackbar) described in STATUS.
- [ ] `make check` is green.
- [ ] STATUS.md entry:
  - names for T19: `rememberExportAllNotes(exporter, session)`, `ExportAllNotes.status`;
  - where the temporary export entry lives;
  - whether the preview share glyph was wired.
- [ ] Commit `T18: open from other apps, share in/out, export all notes`.
