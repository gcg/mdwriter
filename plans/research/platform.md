# mdwriter — Platform, Storage, Gestures & Architecture Research (Android 16 / 17)

Research date: **2026-09-24**. Every version number, API name and limit below was checked today against a primary source (developer.android.com reference/guides, Google Maven `maven-metadata.xml`, Maven Central, AOSP source at `android.googlesource.com` including the `android-17.0.0_r1` tag, androidx source on GitHub, or by decompiling the released AAR with `javap`). Anything I could not verify is marked **UNVERIFIED** with a confidence level.

Scope of this report: storage, opening/sharing files, gestures, predictive back, edge-to-edge/insets/IME, large screens, Android 17 behavior changes, app architecture, and miscellaneous platform topics (keyboard, a11y, haptics, splash, icons, backup). The Markdown engine, editor rendering, and toolchain (AGP/Gradle/Kotlin versions) are covered by other research tracks. I only touch them where the platform forces a constraint.

---

## 0. TL;DR — decisions for the plan author

| # | Topic | Decision | Confidence |
|---|---|---|---|
| 1 | SDK levels | `minSdk = 36`, `targetSdk = 37`, `compileSdk = 37`. The local SDK already has `platforms/android-37.0`. The emulator image is `android-37.1` (16 KB pages). | high |
| 2 | Default storage | **Internal app storage** (`filesDir/library/`) as the default "location". First launch is instant with no prompts, it can't lose permissions, and it's included in Auto Backup. | high |
| 3 | Sync-friendly storage | Add an optional **"Use a folder…"** that links an SAF tree (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission`). Offer it once, gently, from the library drawer (not a first-launch wall). Suggest `Documents/iA Writer` as the initial folder, because that's where iA Writer for Android stored files. | high |
| 4 | Storage to avoid | No `MANAGE_EXTERNAL_STORAGE`. No MediaStore Documents (you can't read other apps' files, and you lose ownership after a reinstall). No `getExternalFilesDir` (no benefit, and it's wiped on uninstall). | high |
| 5 | SAF listing | Use `DocumentsContract.buildChildDocumentsUriUsingTree` + **one** `ContentResolver.query` with a full projection per folder. **Do not use** `DocumentFile.listFiles()` + `getName()`/`lastModified()`: verified to do 1 + N×k IPC queries. | high |
| 6 | Writes | Internal: temp file + `fsync` + atomic rename (java.nio, JVM-testable). SAF: open with `"wt"` (never plain `"w"`, which may not truncate), fall back to `"w"` + truncate only on seekable fds, and always keep an internal recovery copy. | high |
| 7 | Autosave | Debounce 1 s idle, plus a max-latency flush every 10 s while typing continuously. Flush on `ON_STOP`, on document switch and on `onNewIntent`. Run writes in an **application-scoped** coroutine scope (not `viewModelScope`) with `NonCancellable`. | high |
| 8 | Delete | Soft-delete in the UI with an Undo snackbar, and commit when the snackbar times out or on `ON_STOP`. For the internal library, move files to an app trash folder. For SAF, use `DocumentsContract.trashDocument` (new in API 37) when `FLAG_SUPPORTS_TRASH` is set, otherwise `deleteDocument`. | medium-high |
| 9 | Swipe to open the library | Build a **custom horizontal-swipe detector** in `PointerEventPass.Initial` on the editor container. It commits at 56 dp horizontal travel with dx ≥ 2.5·|dy|, or on a fast flick. It aborts on long-press, vertical scroll, multi-touch, mouse, stylus, or an active selection. Committing consumes the events, so the EditText gets `ACTION_CANCEL`; then restore the selection snapshot and call `drawerState.open()`. `ModalNavigationDrawer(gesturesEnabled = drawerState.isOpen)` handles closing. Also provide a small auto-hiding library button and accessibility actions. | high on the facts, medium on the tuning |
| 10 | Edge swipe | Do **not** use `systemGestureExclusion` to steal the Back edge. It's capped at 200 dp vertical per edge and breaks Back for users. | high |
| 11 | Right-to-left swipe | Opens Preview (iA-like), using the same detector. Recommended but optional; can be phase 2. | medium |
| 12 | Back order | IME (system) → selection → find bar → drawer (M3 built-in predictive) → preview → system back-to-home. Enable each handler only when it has something to dismiss. | high |
| 13 | Insets | Call `enableEdgeToEdge()` or `WindowCompat.enableEdgeToEdge(window)`, and set `android:windowSoftInputMode="adjustResize"`. The EditText is the scroll container and fills the space. Put `Modifier.imePadding()` on its container; TextView re-measure then keeps the caret visible (verified in AOSP source). Use `clipToPadding = false` and a status-bar protection strip. | high |
| 14 | Large screens | Build for resizability (it's mandatory at target 37). Show a permanent library pane at `WIDTH_DP_EXPANDED_LOWER_BOUND` (840 dp) and above, using `currentWindowAdaptiveInfoV2()`. Keep a centered text column (64/72/80 chars) implemented as horizontal padding on a full-width EditText. | high |
| 15 | Config changes | Declare `android:configChanges="orientation\|screenSize\|screenLayout\|smallestScreenSize\|keyboard\|keyboardHidden\|navigation\|uiMode\|density"` so the EditText (undo stack, IME, scroll) survives rotation, resize and hardware-keyboard attach. You **still** need correct process-death restoration. | medium-high |
| 16 | EditText saved state | Set **`isSaveEnabled = false`** on the EditText. `EditText.getFreezesText()` is hard-coded `true`, so the whole document plus spans would go into the Bundle (1 MB Binder limit). Restore from disk and the ViewModel instead. | high |
| 17 | Launch mode | `android:launchMode="singleTask"` + `addOnNewIntentListener`. This gives a single editor instance and avoids two activities editing the same file. | medium |
| 18 | DI | Manual `AppContainer` in the `Application` class. Google explicitly allows "Hilt or manual dependency injection in simple apps". No Hilt/Koin, no navigation library. | high |
| 19 | Settings | DataStore Preferences `androidx.datastore:datastore-preferences:1.2.1` (latest stable). | high |
| 20 | Libraries (stable, verified today) | Compose BOM `2026.09.00` (ui/foundation 1.12.1, material3 1.4.0, adaptive 1.3.0), activity-compose 1.13.0, lifecycle 2.11.0, core-ktx 1.19.1, datastore 1.2.1, profileinstaller 1.4.1, Robolectric 4.17 (supports SDK 37). Avoid alpha libraries: activity 1.14.0-alpha02+ requires compileSdk **37.1**, which isn't installed. | high |
| 21 | Permissions | **No `INTERNET` permission** at all. It's a hard guarantee of "no nothing". No storage permissions (SAF needs none). | high |
| 22 | Backup | Keep `allowBackup=true`. `dataExtractionRules` includes `library/` and `datastore/` and excludes trash and recovery. Auto Backup quota is 25 MB/app. The linked SAF folder is **not** restorable (URI grants don't transfer), so re-validate it on start. | high |
| 23 | Baseline profile | Worth it, but in a later phase. For adb installs, profileinstaller compiles on first launch plus background dexopt. Use `make install` for a minified **release** variant signed with the debug key for daily use. | medium |

---

## 1. Verified platform baseline

| Fact | Value | Source (verified 2026-09-24) |
|---|---|---|
| Android 17 stable | Released **2026-06-16**, API level **37**; AOSP tag `android-17.0.0_r1` exists | android-developers.googleblog.com/2026/06/Android-17.html ; en.wikipedia.org/wiki/Android_17 ; `git ls-remote`-style `+refs/tags` on android.googlesource.com/platform/frameworks/base |
| Android 17 QPRs | Versions `37.1`, `37.2` exist (the local AVD image is `android-37.1`) | developer.android.com/about/versions/17 ; local `~/.android/avd/Pixel_10_Pro_XL.avd/config.ini` (`target=android-37.1`, `tag.displaynames=Google APIs PlayStore,Page Size 16KB`) |
| Android 16 | API 36 | developer.android.com/about/versions/16 |
| Local SDK platform | `platforms/android-37.0`, `AndroidVersion.ApiLevel=37.0`, `ExtensionLevel=22`, `Pkg.Revision=2` | local `source.properties` |
| Compose BOM | `2026.09.00` maps to `compose.ui`/`foundation` **1.12.1**, `material3` **1.4.0**, `material3.adaptive:*` **1.3.0**, `material-icons-extended` 1.7.8 | dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom |
| AAR `minCompileSdk` | ui/foundation 1.12.1 and core 1.19.1 need **compileSdk 37** and **AGP ≥ 9.1.0**. activity 1.13.0 needs 36 / AGP ≥ 8.9.1. material3 1.4.0 needs 35. | `META-INF/com/android/build/gradle/aar-metadata.properties` inside each AAR (downloaded from Google Maven) |
| AGP note | The navigationevent 1.1.0-rc01 notes say "Updated Compose compileSdk to API 37. This means that a minimum AGP version of 9.2.0 is required when using Compose." That conflicts with the AAR metadata (9.1.0), so **the toolchain track must pick AGP ≥ 9.2**. | developer.android.com/jetpack/androidx/releases/navigationevent |
| Alpha libs | activity 1.14.0-alpha02: "compileSdk for Compose libraries updated to **37.1**". Stay on stable, because only 37.0 is installed. | developer.android.com/jetpack/androidx/releases/activity |

Latest versions on Google Maven / Maven Central (fetched today):

| Artifact | Latest stable | Latest pre-release |
|---|---|---|
| `androidx.activity:activity-compose` | 1.13.0 (2026-03-11) | 1.14.0-alpha03 (2026-09-23) |
| `androidx.lifecycle:lifecycle-viewmodel-compose` / `-runtime-compose` | 2.11.0 | 2.12.0-alpha04 |
| `androidx.core:core-ktx` | 1.19.1 (2026-09-23) | — |
| `androidx.compose.material3:material3` | 1.4.0 | 1.5.0-alpha29 |
| `androidx.compose.material3.adaptive:adaptive*` | 1.3.0 (2026-08-12) | 1.4.0-alpha02 |
| `androidx.window:window` / `window-core` | 1.5.1 | 1.6.0-alpha05 |
| `androidx.datastore:datastore-preferences` | 1.2.1 | 1.3.0-alpha11 |
| `androidx.documentfile:documentfile` | 1.1.0 (not needed; see §2.3) | — |
| `androidx.core:core-splashscreen` | 1.2.0 (not needed at minSdk 36) | — |
| `androidx.navigationevent:navigationevent-compose` | 1.1.2 | 1.2.0-rc01 |
| `androidx.navigation3:navigation3-runtime` | 1.2.0 (not needed) | 1.3.0-alpha01 |
| `androidx.profileinstaller:profileinstaller` | 1.4.1 | — |
| `androidx.benchmark:benchmark-macro-junit4`, `androidx.baselineprofile` plugin | 1.5.0 | — |
| `androidx.test.ext:junit` | 1.3.0 | — |
| `org.robolectric:robolectric` | **4.17** (2026-09-10; "supports SDK 37") | — |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 1.11.0 | — |
| `junit:junit` 4.13.2 · `com.google.truth:truth` 1.4.5 · `app.cash.turbine:turbine` 1.2.1 | | |

---

## 2. Storage

### 2.1 Options compared

| Option | First-launch UX | Visible to sync tools / file managers | Survives uninstall | Permissions | Perf | Verdict |
|---|---|---|---|---|---|---|
| **Internal `filesDir`** | Instant, no prompt | No (other apps can't access; encrypted at rest on API 29+) | No (removed) | None | Fastest (`java.io`/`java.nio`) | **Default location** |
| App-specific external `getExternalFilesDir()` | Instant | No for apps: SAF can't select `Android/data/` "regardless of permissions, including MANAGE_EXTERNAL_STORAGE". USB MTP visibility **UNVERIFIED/varies**. | No (removed) | None | Fast | No benefit over internal; skip |
| MediaStore `Documents/` (MediaStore.Files + `RELATIVE_PATH`) | Instant | Yes, visible to others | Yes | None to create your own files | OK | **Reject.** You can't read files other apps create in Documents (e.g. Syncthing-synced notes). After a reinstall, "the system considers the file to be attributed to the previously installed version", so you'd need `READ_EXTERNAL_STORAGE`, which doesn't cover non-media anyway. |
| **SAF tree** (`ACTION_OPEN_DOCUMENT_TREE` + persistable grant) | One system picker | Yes (user picks e.g. `Documents/Notes`, synced by Syncthing/Nextcloud/etc.) | Yes | None (URI grant) | Slower (IPC per operation), but fine with batched queries | **Optional linked location** |
| `MANAGE_EXTERNAL_STORAGE` | Special settings screen | Yes | Yes | "All files access" | Fast (paths) | **Reject.** Not best practice. Google Play explicitly lists note-taking/text editors as invalid uses (irrelevant for sideload, but it's a quality signal). It also adds a scary settings screen, which is a "useless menu". |

Key primary-source quotes:
- App-specific storage: "When the user uninstalls your app, the files saved in app-specific storage are removed" (applies to both internal and external). "On Android 10 (API level 29) and higher, these locations are encrypted." (developer.android.com/training/data-storage/app-specific)
- Android 11+: `ACTION_OPEN_DOCUMENT_TREE` can't select the internal storage root, the root of "reliable" SD cards, or `Download/`. `Android/data/` and `Android/obb/` can't be selected "regardless of permissions, including MANAGE_EXTERNAL_STORAGE". (developer.android.com/about/versions/11/privacy/storage)
- MediaStore: "If the user uninstalls and reinstalls your app, however, you must request READ_EXTERNAL_STORAGE to access the files that your app originally created." READ_MEDIA_* only covers Images/Video/Audio. (developer.android.com/training/data-storage/shared/media)
- Play policy: note-taking/text editors don't qualify for All files access; use SAF. (support.google.com/googleplay/android-developer/answer/10467955)
- Obsidian (a precedent for *why* some apps use All files access) cites that scoped storage "performs many extra permission checks for every single file access" and "doesn't provide a way to watch for external changes". (Obsidian Help "Android app", via a mirror at huggingface.co/spaces/anpigon/obsidian-qa-bot/…/Android%20app.md. The official help URL now 404s, so this is secondary, medium confidence.) Mitigations for both are below: batched queries, and ContentObserver on ExternalStorageProvider cursors.
- iA Writer for Android stored public files in **`/storage/emulated/0/Documents/iA Writer`** and "was removed from sale in September 2024" (ia.net/writer/support/help/writer-classic/ia-writer-legacy-for-android). The user likely has files there, which makes a great first "Use a folder…" suggestion.

### 2.2 Recommended design: "Locations"

Model the library as a list of **Locations** (like iA Writer's library locations):

```kotlin
sealed interface LocationId { data object Internal : LocationId; data class Tree(val treeUri: String) : LocationId }

sealed interface DocRef {                 // stable identity of a document
    val location: LocationId
    data class InternalFile(val relPath: String) : DocRef { override val location = LocationId.Internal }
    data class TreeDoc(override val location: LocationId.Tree, val documentId: String) : DocRef
    data class External(val uri: String, val writable: Boolean) : DocRef {   // opened via VIEW/EDIT/OPEN_DOCUMENT
        override val location get() = error("not in a location") }
}
```

- **v1:** Internal (always present) plus **at most one** linked tree. Design the data model for N trees anyway.
- First launch: open straight into a new or last document in Internal. No prompts, no onboarding.
- The library drawer footer shows "On this device" and a subtle **"Use a folder…"** row. Optionally show a one-time dismissible hint card: "Keep notes in a folder you can sync (e.g. Documents/Notes). Coming from iA Writer? Pick Documents/iA Writer."
- Launch `ActivityResultContracts.OpenDocumentTree()` with `EXTRA_INITIAL_URI` = `DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents/iA Writer")`, falling back to `"primary:Documents"`. **Caveat:** the authority and document-ID format are AOSP ExternalStorageProvider implementation details. The contract only says the picker "will attempt" the initial location and it's otherwise "system specific". Best-effort, medium confidence.
- After the result: `contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)`, then store the tree URI string in DataStore.
- On every cold start, validate `contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }`. If it's missing (e.g. after a restore to a new device, or the folder was deleted), mark the location "Disconnected — Reconnect" and never crash.
- Moving notes between Internal and a Tree: copy the stream, then delete the source (DocumentsContract `moveDocument` only works within one provider).

Persisted grant limit: `MAX_PERSISTED_URI_GRANTS = 512` per package on Android 11+ (128 before). Sources: CommonsWare blog "Count Your SAF Uri Persisted Permissions!" and the AOSP UriGrantsManagerService constant (search-result snippet of cs.android.com). **Medium confidence**, secondary sources. It doesn't matter with a tree grant, since one grant covers everything. For individually opened files (see §3), keep an LRU and `releasePersistableUriPermission` for the oldest past ~100.

### 2.3 SAF implementation details

**Listing (verified performance trap).** androidx `TreeDocumentFile.listFiles()` queries only `COLUMN_DOCUMENT_ID`. Every later `getName()`, `isDirectory()`, `lastModified()` or `length()` runs **its own `ContentResolver.query`** (`DocumentsContractApi19.queryForString` etc.), so listing N files with 3 attributes costs 1 + 3N IPC round trips. (Source: androidx-main `documentfile/.../TreeDocumentFile.java` lines 130–158 and `DocumentsContractApi19.java`.) Use one query per folder:

```kotlin
private val PROJECTION = arrayOf(
    Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
    Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE, Document.COLUMN_FLAGS,
)

suspend fun listChildren(tree: Uri, parentDocId: String): List<Entry> = withContext(Dispatchers.IO) {
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
    resolver.query(children, PROJECTION, null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val mime = c.getString(2)
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue                        // hide dotfiles (.trash, .obsidian…)
                val isDir = mime == Document.MIME_TYPE_DIR
                if (!isDir && !isSupportedExtension(name)) continue      // .md .markdown .mdown .mkd .txt
                add(Entry(docId = c.getString(0), name = name, isDir = isDir,
                    lastModified = if (c.isNull(3)) null else c.getLong(3),   // "may be null if unknown"
                    size = if (c.isNull(4)) null else c.getLong(4),
                    flags = c.getInt(5)))
            }
        }
    }.orEmpty().sortedWith(compareByDescending<Entry> { it.isDir }.thenByDescending { it.lastModified ?: 0 })
}
```
- Providers may ignore `sortOrder`, so sort client-side.
- `COLUMN_LAST_MODIFIED` and `COLUMN_SIZE` "may be null if unknown" (reference docs).
- Nested folders mean one query per folder. Load lazily as the user drills in. For "all notes" or search, run a background recursive walk and cache it in memory.

**Watching for external changes (Syncthing etc.).** On AOSP's ExternalStorageProvider (the "internal storage" and SD card root), `queryChildDocuments` returns a `DirectoryCursor`. While that cursor stays open it runs a `FileObserver` on the directory for `ATTRIB | CLOSE_WRITE | MOVED_FROM | MOVED_TO | CREATE | DELETE | DELETE_SELF | MOVE_SELF`, and calls `notifyChange` on the notification URI (source: AOSP `core/java/com/android/internal/content/FileSystemProvider.java`, `DirectoryObserver` and `DirectoryCursor`). So keep the cursor for the **currently displayed folder** open while the drawer is visible or the app is started, `cursor.registerContentObserver(...)`, and re-query on change. It's non-recursive, and other providers may not notify. Always also re-check on `ON_START`.

**Writing: the `"w"` trap (verified).** From the ContentResolver reference: `openOutputStream(uri)` is a "Synonym for openOutputStream(uri, "w"). Please note the implementation of "w" is up to each Provider implementation and it may or may not truncate". The mode may be "r", "w", "wt", "wa", "rw" or "rwt". Since Android 10, `"w"` doesn't truncate on the platform provider. This is the "aCropalypse" root cause (issuetracker 180526528 / 135714729, iliana.fyi/blog/acropalypse-now). Some cloud providers return **pipes**, where `truncate()` throws `ESPIPE`/"Illegal seek" (github.com/kineapps/flutter_file_dialog/issues/62). Robust strategy:

```kotlin
suspend fun writeSaf(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    val pfd = try { resolver.openFileDescriptor(uri, "wt") }
              catch (e: IllegalArgumentException) { null }          // provider rejected mode
              catch (e: UnsupportedOperationException) { null }
        ?: resolver.openFileDescriptor(uri, "w") ?: throw IOException("provider returned null")
    pfd.use {
        FileOutputStream(it.fileDescriptor).use { out ->
            out.write(bytes)
            out.flush()
            val isRegular = runCatching { OsConstants.S_ISREG(Os.fstat(it.fileDescriptor).st_mode) }.getOrDefault(false)
            if (isRegular) {                       // belt & braces: guarantee no stale tail even if "wt" was ignored
                out.channel.truncate(bytes.size.toLong())
                runCatching { Os.fsync(it.fileDescriptor) }
            }
        }
    }
    // Verify: re-query COLUMN_SIZE; if size != bytes.size (and size != null) → report error, keep recovery copy.
}
```
SAF has no atomic rename-over. A crash mid-write can leave a truncated file, which is why §2.8 keeps an internal **recovery copy** of the latest buffer written *before* the provider write.

**Create / rename / delete (verified APIs).**
- `DocumentsContract.createDocument(resolver, parentDocUri, mimeType, displayName)`. **Pitfall (verified in AOSP `FileUtils.splitFileName`):** if the display name's extension doesn't map to the given MIME, the provider appends the MIME's extension. `createDocument(..., "text/plain", "Note.md")` gives **`Note.md.txt`**. Use `"text/markdown"` for `.md`/`.markdown` (Android 17's MimeMap maps `md markdown` to `text/markdown`, verified in `external/mime-support/mime.types` at tag `android-17.0.0_r1`). Use `"text/plain"` for `.txt`. Use `"application/octet-stream"` (`ContentResolver.MIME_TYPE_DEFAULT`) for unknown extensions (`.mdown`, `.mkd`), which keeps the name as-is. Name collisions get " (1)" appended by the provider, so read back `COLUMN_DISPLAY_NAME`.
- `renameDocument(resolver, docUri, newName)`: "If the underlying provider needs to create a new COLUMN_DOCUMENT_ID … that new document is returned and the original document is no longer valid." On ExternalStorageProvider the doc ID contains the path, so **always** replace the stored DocRef with the returned URI. Requires `FLAG_SUPPORTS_RENAME` (0x40).
- `deleteDocument` requires `FLAG_SUPPORTS_DELETE` (0x4). `FLAG_SUPPORTS_WRITE` (0x2) means `openOutputStream` "can be expected to succeed". `FLAG_DIR_SUPPORTS_CREATE` is needed on the parent to create. Gate UI actions on these flags.
- **New in API 37 (verified in the reference plus the AOSP `android-17.0.0_r1` source):** `DocumentsContract.trashDocument(resolver, uri)` (needs `Document.FLAG_SUPPORTS_TRASH` = 0x10000), `restoreDocumentFromTrash(resolver, src, targetParent)` (`FLAG_SUPPORTS_RESTORE` = 0x20000), `buildTrashDocumentsUri`, `Document.COLUMN_ORIGINAL_RELATIVE_PATH`, and `Document.COLUMN_CONTENT_SYNC_STATE_FLAGS` with `SYNC_STATE_FLAG_*` (upload/download progress/error). ExternalStorageProvider implements trash behind the read-only aconfig flag `android.provider.enable_documents_trash_api`. It's not supported on USB volumes or top-level default dirs. The DocumentsProvider javadoc says: "Any URI permission grants for the given document will be revoked", so **our app may not be able to restore it**, and Undo must not depend on restore. Use trash only as the *final commit* step, so the user can still recover via the Files app. Guard with `Build.VERSION.SDK_INT >= 37` (minSdk is 36) **and** the flag bit.
- `COLUMN_CONTENT_SYNC_STATE_FLAGS` (API 37, optional column) could show a tiny "syncing/error" dot for cloud providers. Nice-to-have for a later phase.

### 2.4 Internal library implementation

```
filesDir/
  library/            ← Internal location root (nested folders allowed). BACKED UP.
  datastore/          ← DataStore prefs (created by DataStore). BACKED UP.
  .trash/<uuid>/      ← soft-deleted files + meta.json {originalRelPath, deletedAt}. NOT backed up. Purge > 30 days.
noBackupFilesDir/
  recovery/<docKey>.md ← latest unsaved/just-saved buffer per open doc (crash & SAF safety net)
cacheDir/exports/     ← temp files for share-out via FileProvider
```

Atomic write, pure JVM (testable without Robolectric):
```kotlin
fun atomicWrite(target: File, bytes: ByteArray) {
    val tmp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")   // dot-prefixed → hidden from listing
    FileOutputStream(tmp).use { out -> out.write(bytes); out.fd.sync() }
    try { Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
    catch (e: Exception) { tmp.delete(); throw e }
}
```
`android.util.AtomicFile` is an acceptable alternative. It writes a sibling `<name>.new`, then renames, and "does not confer any file locking semantics" (reference docs). If you use it, filter `*.new`/`*.bak` from listings. Serialize writers per document with a `Mutex`.

### 2.5 Nested folders
- Internal: real directories under `library/`. SAF: `MIME_TYPE_DIR` entries; create with `createDocument(parent, Document.MIME_TYPE_DIR, name)`.
- Drawer UI: breadcrumb header ("Library › Journal › 2026"), subfolders first, then files by last-modified. Tap a folder to drill in. When the drawer is open and not at the root, Back goes up one level (see §5).
- New note goes into the currently displayed folder (iA's "Preferred action" model).

### 2.6 File naming
- iA Writer's documented settings cover a default extension (`.md`/`.txt`/`.text`), not auto-naming (ia.net/writer/support/basics/settings). iA-style auto-naming from the first line is **not documented**, so this is our design choice.
- Recommendation: a new note gets a provisional name immediately (`Untitled.md`, then `Untitled 2.md` on collision) so it can be autosaved. While the doc has `autoNamed = true` (no explicit rename by the user), **rename on "leave"** (document switch, `ON_STOP`, drawer open) to a name derived from the first non-empty line, with Markdown markers stripped (`#`, `>`, `-`, `*`, `**`, `` ` ``, link syntax). Don't rename on every keystroke, since sync tools would churn.
- Sanitize: remove `/ \ : * ? " < > |` and control chars, collapse whitespace, trim trailing dots/spaces, cap at ~80 chars (FAT/exFAT SD cards and ext4 both cap names near 255 bytes/UTF-16 units), then append the extension.
- An explicit rename (long-press in the library or a title tap) sets `autoNamed = false`. Store `autoNamed` in a small DataStore map keyed by DocRef. No sidecar files in user folders.

### 2.7 Rename / delete / undo
- **Delete:** swipe-to-delete or long-press "Delete" in the library. Remove the entry from the list immediately and show a snackbar "Deleted ‘X’ · Undo" (~6 s). **Commit** on snackbar timeout, when another delete replaces the snackbar, or on `ON_STOP`.
  - Internal: move to `.trash/` (restorable in-app later if you want a Trash view; not required for v1).
  - SAF: `trashDocument` if API ≥ 37 and `FLAG_SUPPORTS_TRASH`, else `deleteDocument`.
  - Deleting the **currently open** doc: close the editor first, then open the most recent other doc or a fresh note.
- **No confirmation dialogs.** Undo is the modern pattern and matches "no useless menus".

### 2.8 Autosave strategy
```
text change ──▶ dirty=true, gen++ ──▶ debounce(1000 ms) ─┐
                         └──▶ maxLatency ticker(10 s while dirty) ─┴─▶ saveNow(doc, snapshot)
ON_STOP / doc switch / onNewIntent / drawer "open other file" ──▶ flushNow() (await)
```
- Snapshot on main: `editable.toString()` (O(n) copy, fine for ≤ a few MB). Encode and write on `Dispatchers.IO`.
- **Scope:** run saves in `appContainer.applicationScope` (`CoroutineScope(SupervisorJob() + Dispatchers.IO)`) wrapped in `withContext(NonCancellable)`. Rationale: when the activity finishes (e.g. Back from an externally opened file isn't a root-launcher back, so the activity is finished, per Android 12 behavior changes), `ViewModel.onCleared()` cancels `viewModelScope`, which would drop the final flush.
- Order for SAF docs: (1) write `noBackupFilesDir/recovery/<key>.md` (fast, local); (2) write to the provider; (3) on success, record baseline `{lastModified, size}` from a fresh query and delete or refresh the recovery file.
- For SAF debounce, use 2 s (cloud providers are expensive). Internal: 1 s.
- Skip the write if `gen == lastSavedGen`. Coalesce with a conflated channel per doc, so only the newest snapshot gets written.
- "Saved" feedback: a tiny, fading status in the header area. **Don't** use `announceForAccessibility`, which is deprecated in Android 16 (see §10.2). Use a polite live region if you announce anything.

### 2.9 Detecting external modifications & conflicts
- Keep `baseline = {lastModified, size, sha256?}` captured after each load and save.
- On `ON_START` (LifecycleStartEffect), after a ContentObserver fires, and before each save: re-stat the doc (internal `File.lastModified()/length()`; SAF query `COLUMN_LAST_MODIFIED`, `COLUMN_SIZE`).
- If the file changed on disk and the local buffer is **clean**, reload silently and restore the caret to roughly the same offset. If it changed and the buffer is **dirty**, show a non-modal banner: "Changed on disk — Reload · Keep mine · Save both" ("Save both" writes the local buffer as `Name (conflict YYYY-MM-DD HHmm).md`). If `lastModified` is null (provider doesn't know), compare size, then a hash of the content read back.
- If the file is gone: show "File was moved or deleted" with "Save as new here" / "Close". Also note from the docs: after `takePersistableUriPermission`, "your app doesn't retain access to the URI if the associated document is moved or deleted".

### 2.10 Process death & state restoration
- The source of truth is **disk + recovery copy**, not the Bundle. `SavedStateHandle` stores only `{docKey, selectionStart, selectionEnd, scrollY, drawerOpen, previewOpen}`. The saving-states guide: "store only primitive types and simple, small objects such as String … a minimal amount of data necessary, such as an ID".
- **Pitfall (verified in AOSP):** `EditText.getFreezesText()` returns `true` unconditionally, and `TextView.onSaveInstanceState()` then copies the full text plus parcelable spans into the saved state. The Binder transaction buffer is "a limited fixed size, currently 1MB, which is shared by all transactions in progress for the process" (TransactionTooLargeException reference). **Set `editText.isSaveEnabled = false`** and don't give it an ID. Restore text from the repository and selection/scroll from SavedStateHandle.
- On restore: load the doc from disk. If `recovery/<key>.md` is newer than or different from disk (a save didn't complete before death), prefer the recovery content, mark it dirty, and save.
- Undo history is lost on process death (acceptable, and same as most editors). It survives rotation because of `configChanges` (see §10.6).

### 2.11 Encoding & line endings
- Read bytes. If they start with `EF BB BF`, strip and remember `hadBom`. Decode with `StandardCharsets.UTF_8.newDecoder().onMalformedInput(REPORT)`. On failure, decode as `windows-1252`, mark `encodingConverted = true`, and show a one-time note "Converted to UTF-8 on save".
- Line endings: detect the dominant style (`\r\n` vs `\n`), normalize the buffer to `\n`, and restore the original style on save. Don't add or strip a trailing newline. (Normalizing avoids stray `\r` in spans and offsets. Rendering of lone `\r` in TextView is **UNVERIFIED**, so normalize regardless.)
- Always write UTF-8, re-adding the BOM only if the original had one.

### 2.12 File size limits (heuristic, UNVERIFIED thresholds, tune on device)
- ≤ 1 MB: full live styling. 1–5 MB: open with styling limited to the visible window or plain, and show a subtle notice. > 5 MB: open read-only plain, or refuse with "Too large for mdwriter". Reject binary files: if a NUL byte appears in the first 8 KB, treat as binary.
- Android 17 enforces RAM-based app memory limits. A kill shows `ApplicationExitInfo.getDescription()` containing `"MemoryLimiter:AnonSwap"` (behavior-changes-all). That's one more reason to cap sizes.

### 2.13 Auto Backup / device transfer
Facts (developer.android.com/identity/data/autobackup): 25 MB per app per user (over quota, `onQuotaExceeded()` runs and there's no cloud backup). By default it includes `getFilesDir()`, databases, shared prefs and `getExternalFilesDir()`, and excludes `getCacheDir()`, `getCodeCacheDir()` and `getNoBackupFilesDir()`. Backup runs at most every 24 h, when idle, on Wi-Fi; "the backup system ignores apps that are running in the foreground"; "the system shuts down the app" during backup. It's end-to-end encrypted with the device PIN on Android 9+, and only the latest backup is kept.

`res/xml/data_extraction_rules.xml` (API 31+ format; minSdk 36, so `fullBackupContent` isn't needed):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup disableIfNoEncryptionCapabilities="true">
        <include domain="file" path="library/"/>
        <include domain="file" path="datastore/"/>
    </cloud-backup>
    <device-transfer>
        <include domain="file" path="library/"/>
        <include domain="file" path="datastore/"/>
    </device-transfer>
</data-extraction-rules>
```
When you use `<include>`, anything not included (`.trash/`, `noBackupFilesDir`, cache) is left out. Manifest: `android:allowBackup="true" android:dataExtractionRules="@xml/data_extraction_rules"`. Persisted SAF grants don't come across, so after a restore the linked-folder setting must be re-validated (§2.2). Test with `adb shell bmgr enable true`, `adb shell bmgr transport com.android.localtransport/.LocalTransport`, `adb shell bmgr backupnow <pkg>`, `adb shell bmgr restore` (commands verified on developer.android.com/identity/data/testingbackup). **Decision for the user:** "no cloud" might mean they don't want Google backup either. Default is ON (it's the OS's feature and a safety net); a settings toggle could exclude it later.

---

## 3. Opening files from other apps, share-in, share-out

### 3.1 Intent-filter facts (verified)
- "MIME type matching in the Android framework is case-sensitive … always specify MIME types using lowercase letters." "If the filter has a data type set (mimeType) but no scheme, the content: and file: schemes are assumed." (data-element guide)
- "An intent that contains both a URI and a MIME type … passes the MIME type part … only if that type matches a type listed in the filter." So an extension-based filter must still declare a `mimeType` (use `*/*`).
- `pathPattern`/`pathSuffix`/`pathAdvancedPattern` "are meaningful only if the scheme and host attributes are also specified". `pathSuffix` and `pathAdvancedPattern` are **API 31+**, fine at minSdk 36. `pathPattern` has no backtracking, which is why `pathSuffix` is preferred. Content-URI paths are provider-specific (MediaStore IDs have no extension), so extension filters are a **fallback**, not the primary mechanism.
- Android 17's MimeMap: `md`/`markdown` map to `text/markdown`. `mdown`/`mkd`/`mkdn` aren't mapped (become `application/octet-stream`).

### 3.2 Manifest sketch (single activity)
```xml
<manifest ...>
  <!-- NO <uses-permission android:name="android.permission.INTERNET"/> — deliberate. -->
  <application
      android:name=".MdWriterApp"
      android:allowBackup="true"
      android:dataExtractionRules="@xml/data_extraction_rules"
      android:icon="@mipmap/ic_launcher"
      android:label="@string/app_name"
      android:theme="@style/Theme.MdWriter">
    <!-- android:enableOnBackInvokedCallback defaults to true at targetSdk 36+; don't set it to false. -->

    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:launchMode="singleTask"
        android:windowSoftInputMode="adjustResize|stateUnspecified"
        android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|keyboard|keyboardHidden|navigation|uiMode">

      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>

      <!-- Open/edit by MIME -->
      <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <action android:name="android.intent.action.EDIT"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="content"/>
        <data android:mimeType="text/markdown"/>
        <data android:mimeType="text/x-markdown"/>
        <data android:mimeType="text/plain"/>
      </intent-filter>

      <!-- Fallback by extension when the sender says application/octet-stream or */* -->
      <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <action android:name="android.intent.action.EDIT"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:scheme="content" android:host="*" android:mimeType="*/*"/>
        <data android:pathSuffix=".md"/>
        <data android:pathSuffix=".markdown"/>
        <data android:pathSuffix=".mdown"/>
        <data android:pathSuffix=".mkd"/>
      </intent-filter>

      <!-- Share text in → new note -->
      <intent-filter>
        <action android:name="android.intent.action.SEND"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <data android:mimeType="text/plain"/>
        <data android:mimeType="text/markdown"/>
        <data android:mimeType="text/x-markdown"/>
      </intent-filter>
    </activity>

    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.files"
        android:exported="false"
        android:grantUriPermissions="true">
      <meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
    </provider>
  </application>
</manifest>
```
- `<data>` elements in one filter combine (any scheme × any host × any MIME), which is intended here. Test that the fallback filter doesn't make mdwriter appear for unrelated files. Drop `BROWSABLE` if you don't want web links (`https://…/x.md`) to show mdwriter. Without `INTERNET` we can't fetch them anyway, so **remove `BROWSABLE`**.
- Whether `file://` needs handling: senders targeting API 24+ get `FileUriExposedException`, so content-only is fine.

### 3.3 Handling
- `ComponentActivity.addOnNewIntentListener { handleIntent(it) }` plus `handleIntent(intent)` in `onCreate` (only when `savedInstanceState == null`, to avoid re-importing on recreation).
- VIEW/EDIT with a `content://` URI:
  1. Read the display name via `OpenableColumns.DISPLAY_NAME`.
  2. Check writability: `checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PERMISSION_GRANTED`. ACTION_VIEW grants are often **read-only**; ACTION_EDIT usually includes write.
  3. If `(intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0`, try `takePersistableUriPermission` (catch `SecurityException`) and add it to "Recent" (LRU, cap ~100). Per the reference, only grants carrying `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` can be persisted. Otherwise the doc is session-only.
  4. If read-only, show a subtle "Read-only · Save a copy to Library" pill.
- If the URI is inside the linked tree, `DocumentsContract.isChildDocument(resolver, treeDocUri, uri)` (API 29+, requires `Root.FLAG_SUPPORTS_IS_CHILD`, which ExternalStorageProvider sets) lets you treat it as a TreeDoc.
- SEND: `EXTRA_TEXT` (and optional `EXTRA_SUBJECT` as `# Subject` title) creates a new internal note. `EXTRA_STREAM` with a `content://` URI imports a copy.
- Before switching docs on a new intent: `flushNow()` on the current doc.

### 3.4 Share-out & export
- Share: write a temp copy to `cacheDir/exports/Name.md`, get the URI via `FileProvider.getUriForFile`, and build `Intent(ACTION_SEND).setType("text/markdown").putExtra(EXTRA_STREAM, uri).setClipData(ClipData.newRawUri(name, uri)).addFlags(FLAG_GRANT_READ_URI_PERMISSION)`, wrapped in `Intent.createChooser`. Also put the text in `EXTRA_TEXT` for apps that only accept text. **Android 17 note (behavior-changes-all):** "Starting in Android 18, the system will no longer automatically grant these permissions" for `ACTION_SEND`/`SEND_MULTIPLE`. Add the flag explicitly now and enable `StrictMode.VmPolicy.Builder().detectImplicitUriPermissionGrant()` in debug (API 37 method, verified in the reference).
- Export ("Save a copy…"): `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown"))` then write with `"wt"`. The no-arg `CreateDocument()` constructor is deprecated in favor of the MIME one (activity release notes). ACTION_CREATE_DOCUMENT "cannot overwrite existing files; system appends (1)".

### 3.5 Launch mode rationale
`singleTask` keeps one editor instance, so VIEW intents from Files bring our task forward and arrive via `onNewIntent`. This avoids two activities (and two ViewModels) editing the same file concurrently. The alternative (standard launch mode plus an app-scoped document session registry with per-doc locks) is more complex. Downside: Back from our root activity moves our task back and returns to Files (Android 12+ behavior), which is acceptable. **Confidence medium.** Verify on device that VIEW from Files → edit → Back returns to Files.

---

## 4. Gestures: swipe to open the library

### 4.1 Verified facts
1. **System back zones.** Under gesture navigation, touches that start inside the left/right system-gesture insets (`WindowInsets.systemGestures`) are taken by the system as Back and never reach the app. Home/quick-switch at the bottom can't be opted out; read `getMandatorySystemGestureInsets()` (gesture-nav guide).
2. **Exclusion limit (current reference for `View.setSystemGestureExclusionRects`):** "the system will put a limit of **200dp on the vertical extent** of the exclusions it takes into account. The limit does not apply while the navigation bar is stickily hidden, nor to the input method and home activity." Compose has `Modifier.systemGestureExclusion()` and `systemGestureExclusion { coords -> Rect }` (foundation, androidMain). Guidance: mark exclusion only for "a precision touch gesture in a small area … such as an edge swipe or dragging a SeekBar thumb." Views' `DrawerLayout` "support[s] automatic opt-out behavior out of the box". There's no Material3 Compose equivalent.
3. **Material3 `ModalNavigationDrawer` (1.4.0, verified by decompiling the AAR and reading androidx-main source):** the outer `Box(modifier.fillMaxSize().anchoredDraggable(state, Orientation.Horizontal, enabled = gesturesEnabled, reverseDirection = isRtl))`. So it opens by **dragging anywhere** over the content (not edge-only) whenever `gesturesEnabled`, as long as no child consumes the drag first. The **scrim click only dismisses when `gesturesEnabled` is true** (`onDismissRequest` checks it). There's **no public continuous-drag API** on `DrawerState` (only `open()`, `close()`, `animateTo`, `snapTo`, `currentOffset`, `targetValue`, `isOpen`, `isAnimationRunning`; `anchoredDraggableState` is internal). Escape closes it. `ModalDrawerSheet(drawerState = …)` "will handle back by default for all Android versions, as well as animate during predictive back"; the overload without `drawerState` doesn't handle back.
4. **EditText steals horizontal drags (verified in AOSP `Editor.java`, main).** `InsertionPointCursorController.onTouchEvent` starts a **"cursor drag from anywhere"** when the EditText is focused, the finger has moved past touch slop, and the initial drag direction is more than **45° from vertical** (`WidgetFlags.ENABLE_CURSOR_DRAG_FROM_ANYWHERE_DEFAULT = true`, `CURSOR_DRAG_MIN_ANGLE_FROM_VERTICAL_DEFAULT = 45`). It then calls `mTextView.getParent().requestDisallowInterceptTouchEvent(true)`. Long-press drag-selection (`SelectionModifierCursorController.enterDrag`) also calls `requestDisallowInterceptTouchEvent(true)`. So on a focused EditText, any mostly-horizontal swipe **moves the cursor** and blocks ordinary parent interception.
5. **Compose ↔ View interop (verified in `PointerInteropFilter.android.kt`):** events reach the embedded View during `PointerEventPass.Initial`. If the View requested disallow-intercept, the filter consumes moves in the Main pass, so M3's `anchoredDraggable` (Main pass) **never sees them**. **But** a Compose ancestor listening in the **Initial pass runs before the child**. If it consumes, "we intercept the stream and dispatch ACTION_CANCEL to the Android View". The KDoc says this interception works "even after requestDisallowInterceptTouchEvent has been called". This is the lever.
6. Obsidian's Android app is a real-world cautionary tale: users report it "misinterprets text selection as a side swipe", and there are feature requests to disable swipe (forum.obsidian.md threads). iA Writer for Android had a setting so that "you can swipe between the Library, Editor and Preview" (ia.net settings-android page, now redirected to the legacy page; wording from a search snippet, medium confidence).

**Conclusion:** M3's built-in drag-anywhere is unreliable over a focused EditText (the cursor drag wins) and unpredictable when not focused. It also collides with vertical scrolling and selection. Disable it while closed and use a custom detector.

### 4.2 Recommended gesture design
- **Open the library:** a horizontal swipe **anywhere in the editor body** (not from the edge). Start → end (left-to-right in LTR) opens the library.
- **Open the preview (optional, phase 2):** end → start.
- **Armed only when:** no text selection (`selStart == selEnd`), the drawer, preview and find bar are closed, `PointerType.Touch` (not mouse, stylus or eraser; stylus horizontal strokes may be handwriting), and there's a single pointer.
- **Commit rule (during move):** |dx| ≥ **56 dp** AND |dx| ≥ **2.5 × |dy|** (within ~22° of horizontal) AND elapsed < 600 ms.
- **Flick rule (on up):** |vx| ≥ **1000 dp/s**, |vx| ≥ 2.5·|vy|, |dx| ≥ 24 dp.
- **Abort:** long-press timeout reached while within touch slop (selection is starting); |dy| > touchSlop while not horizontal (scrolling); a second pointer (pinch/zoom).
- **On commit:** consume the Initial-pass changes (the EditText gets `ACTION_CANCEL`). Restore the selection snapshot taken at down (the EditText may already have moved the caret during the first ~56 dp). Hide the IME. Haptic `HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE` (Compose `HapticFeedbackType.GestureThresholdActivate`, both verified). Call `drawerState.open()`. Swallow the rest of the gesture.
- **Closing:** `ModalNavigationDrawer(gesturesEnabled = drawerState.isOpen || drawerState.targetValue == DrawerValue.Open)` gives drag-to-close, scrim tap, Escape, and predictive back (via `ModalDrawerSheet(drawerState)`).
- **Fallback affordances (required for discoverability and a11y):** a 48 dp-target, low-contrast "library" glyph at top-start inside the safe area. It fades out after ~1.5 s of typing and fades in on scroll-up, tap, or IME hide. Also Ctrl+L / Ctrl+O and accessibility custom actions ("Open library", "Show preview") on the editor.
- **Don't** use `systemGestureExclusion`.

Code sketch (Compose; applied to the Box that hosts the `AndroidView(EditText)`):
```kotlin
enum class SwipeDir { TowardEnd, TowardStart }   // TowardEnd = LTR left→right

fun Modifier.editorSwipeNav(
    isArmed: () -> Boolean,
    onArmedDown: () -> Unit,              // snapshot selection
    onSwipe: (SwipeDir) -> Unit,          // restore selection, hide IME, haptic, open drawer/preview
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    val commitPx = 56.dp.toPx(); val minFlickPx = 24.dp.toPx(); val flingPxPerS = 1000.dp.toPx()
    val rtl = layoutDirection == LayoutDirection.Rtl
    fun dir(dx: Float) = if ((dx > 0) != rtl) SwipeDir.TowardEnd else SwipeDir.TowardStart

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.type != PointerType.Touch || !isArmed()) return@awaitEachGesture
        onArmedDown()
        val vt = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            if (ev.changes.count { it.pressed } > 1) return@awaitEachGesture
            val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            vt.addPosition(c.uptimeMillis, c.position)
            val d = c.position - down.position
            val horizontal = abs(d.x) >= 2.5f * abs(d.y)
            if (!c.pressed) {                                           // finger up → flick?
                val v = vt.calculateVelocity()
                if (abs(v.x) >= flingPxPerS && abs(v.x) >= 2.5f * abs(v.y) && abs(d.x) >= minFlickPx) {
                    c.consume(); onSwipe(dir(d.x))                    // up consumed → View gets CANCEL, no tap
                }
                return@awaitEachGesture
            }
            val elapsed = c.uptimeMillis - down.uptimeMillis
            if (elapsed > longPressMs && d.getDistance() < slop) return@awaitEachGesture   // long-press/selection
            if (abs(d.y) > slop && !horizontal) return@awaitEachGesture                   // vertical scroll
            if (abs(d.x) >= commitPx && horizontal && elapsed < 600) {
                ev.changes.forEach { it.consume() }                    // → interop sends ACTION_CANCEL to EditText
                onSwipe(dir(d.x))
                do { val e = awaitPointerEvent(PointerEventPass.Initial); e.changes.forEach { it.consume() } }
                while (e.changes.any { it.pressed })
                return@awaitEachGesture
            }
        }
    }
}
```
- APIs verified in foundation/ui 1.12.1: `awaitFirstDown(requireUnconsumed, pass)`, `awaitEachGesture`, `ViewConfiguration.touchSlop` / `longPressTimeoutMillis` / `minimumFlingVelocity`, `androidx.compose.ui.input.pointer.util.VelocityTracker`.
- Selection snapshot/restore: `saved = et.selectionStart to et.selectionEnd` in `onArmedDown`, then `et.setSelection(saved.first, saved.second)` on swipe.
- IME: remember `WindowInsets.isImeVisible` at open. Hide via `WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())`. On drawer close, re-show if it was visible and the editor still has focus.
- If the editor track picks Compose `BasicTextField` instead of EditText, the same Initial-pass detector works (its gesture handlers run in Main and respect consumption).
- Pure-View fallback (if the editor ends up hosted in a View parent): a `FrameLayout` subclass overriding **`dispatchTouchEvent`** (not `onInterceptTouchEvent`, which is bypassed after disallow-intercept) with the same state machine. On commit, dispatch a synthesized `ACTION_CANCEL` to `super.dispatchTouchEvent`.
- **Tuning is empirical (medium confidence):** 56 dp / 2.5 ratio / 1000 dp/s are starting points. Put them in one `SwipeTuning` object and test on the Pixel 10 Pro XL AVD plus the user's phone.

### 4.3 Should right-to-left open the preview?
Pros: iA Writer parity (Library ← Editor → Preview). One detector covers both. It's useful for rendered tables, images and links. Cons: the live editor already styles headings and emphasis, so preview is secondary; accidental triggers are twice as likely. **Recommendation:** implement it behind a setting that defaults **on**. The preview is a full-screen read-only overlay that closes via reverse swipe, predictive back, or Ctrl+R. Make it phase 2 if time is short.

---

## 5. Predictive back

Facts:
- **Android 16 (targetSdk 36+):** predictive back system animations (back-to-home, cross-task, cross-activity) are on by default. "`onBackPressed()` is NOT called; `KeyEvent.KEYCODE_BACK` is NOT dispatched." The opt-out is `android:enableOnBackInvokedCallback="false"` (behavior-changes-16). **Don't opt out.**
- Android 16 (all apps): predictive back for **3-button navigation** via long-press of Back, for apps that migrated (behavior-changes-all 16).
- Android 16 APIs: `OnBackInvokedDispatcher.PRIORITY_SYSTEM_NAVIGATION_OBSERVER`, `SystemOnBackInvokedCallbacks.finishAndRemoveTaskCallback()` / `moveTaskToBackCallback()` (features-16). Not needed here.
- Android 17: no new predictive-back behavior changes listed (checked the full 17 summary page).
- "Intercepting back at the root activity disables the back-to-home animation" (compose predictive-back-setup), so **only enable handlers when they have something to dismiss**.
- activity-compose 1.13.0 `BackHandler(enabled) {}` and `PredictiveBackHandler(enabled) { progress: Flow<BackEventCompat> -> }` are **not deprecated** (checked the class files). Since activity 1.12 they're built on `androidx.navigationevent` (NavigationEventDispatcher). The KDoc states "**last composed wins**" and recommends unconditional composition with the `enabled` flag. `navigationevent-compose` 1.1.2's `NavigationBackHandler` is the newer alternative, but plain `BackHandler` is simpler and fine.
- Platform `Editor` registers its own `OnBackInvokedCallback` (PRIORITY_DEFAULT) that calls `stopTextActionMode` while the selection toolbar is showing (AOSP Editor.java `mBackCallback`). Ordering relative to AndroidX's callback depends on registration order, so **handle "collapse selection" explicitly** in Compose.

Order (highest priority first) and implementation:
1. **IME visible:** the system/IME handles Back and hides the keyboard. Not in the app's control.
2. **Selection active:** `BackHandler(enabled = ui.hasSelection) { editor.collapseSelection() }` (`setSelection(selectionEnd)`, which ends the floating toolbar action mode).
3. **Find bar open:** close it.
4. **Drawer open:** if in a subfolder, go up a level (`BackHandler(enabled = drawerOpen && !atRoot)` placed *inside* the drawer content, so it's composed after the sheet's own handler and wins). Otherwise `ModalDrawerSheet(drawerState)` closes it with the predictive animation.
5. **Preview open:** `PredictiveBackHandler(enabled = ui.previewOpen) { progress -> progress.collect { /* scale/translate preview by it.progress */ }; vm.closePreview() }` with a `CancellationException` catch to reset.
6. **Nothing open:** no handler enabled. The system back-to-home animation runs, and the root launcher activity moves to the background (Android 12+ behavior). `ON_STOP` flushes the save.

Compose ordering sketch (the last composed has the highest priority, so compose the lowest first):
```kotlin
PredictiveBackHandler(enabled = ui.previewOpen) { ... }         // 5
BackHandler(enabled = ui.findOpen) { vm.closeFind() }             // 3
BackHandler(enabled = ui.hasSelection) { editor.collapseSelection() } // 2
```
Opening the drawer collapses the selection and hides the IME first, so states 2 and 4 don't overlap.

---

## 6. Edge-to-edge, insets & IME

Facts:
- Android 15 (target 35): edge-to-edge enforced. Status bar is transparent by default; `setStatusBarColor` is deprecated with no effect. Gesture nav bar is transparent; 3-button nav is 80% opaque with `navigationBarContrastEnforced` true by default. `layoutInDisplayCutoutMode` of non-floating windows is treated as `ALWAYS`. `Configuration.screenWidthDp/HeightDp` no longer exclude system bars (behavior-changes-15).
- Android 16 (target 36): `windowOptOutEdgeToEdgeEnforcement` is "deprecated and disabled" (behavior-changes-16). There's no opt-out.
- `ComponentActivity.enableEdgeToEdge()` (activity 1.13.0) is still stable and sets system-bar icon appearance via `SystemBarStyle.auto(lightScrim, darkScrim, detectDarkMode: (Resources) -> Boolean)`. activity **1.14.0-alpha03** (2026-09-23) says "Use `WindowCompat#enableEdgeToEdge(Window)` instead of `EdgeToEdge#enable()`". `WindowCompat.enableEdgeToEdge(Window)` exists in core 1.19.1 (javap-verified) but doesn't manage icon contrast, so pair it with `WindowInsetsControllerCompat.isAppearanceLightStatusBars/NavigationBars`.
- Compose insets doc: `android:windowSoftInputMode="adjustResize"` is "required for `WindowInsets.ime` in Compose". The `SOFT_INPUT_ADJUST_RESIZE` constant is deprecated since API 30, but the manifest value still selects resize over pan. "if neither … set, then the system will try to pick one or the other depending on the contents", so set it explicitly.
- Caret visibility (verified in AOSP `TextView.java`): `onMeasure()` calls `registerForPreDraw()` whenever `mMovement != null` (always true for EditText). `onPreDraw()` then calls `bringPointIntoView(getSelectionEnd())`, or the selection start while its handle is dragged. So **if the EditText itself shrinks** when the IME appears, it scrolls the caret into view on the next frame. That holds only if the EditText is its own scroll container, not `wrap_content` inside an outer `verticalScroll`.

Recommended implementation:
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()                               // or WindowCompat.enableEdgeToEdge(window)
        window.isNavigationBarContrastEnforced = false   // no translucent scrim on 3-button nav (editor bg shows through)
        setContent { MdWriterApp() }
    }
}

@Composable fun SystemBarAppearance(darkTheme: Boolean) {   // in-app theme may differ from system
    val view = LocalView.current
    val activity = LocalActivity.current ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable fun EditorSurface(...) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val imeVisible = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize().background(bg)) {
        AndroidView(
            factory = { ctx -> MarkdownEditText(ctx).apply {
                isSaveEnabled = false; clipToPadding = false; background = null
                gravity = Gravity.TOP or Gravity.START; isVerticalScrollBarEnabled = true
            } },
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .imePadding()                              // EditText shrinks → TextView keeps caret visible
                .editorSwipeNav(...),
            update = { et ->
                et.setPadding(sidePadPx, statusTop + topComfortPx,
                              sidePadPx, (if (imeVisible) 0 else navBottom) + bottomComfortPx)
            },
        )
        // Status-bar protection so scrolled text doesn't collide with status icons:
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(bg.copy(alpha = 0.94f)))
    }
}
```
- Keep the EditText **full width** and create the centered column with horizontal padding: `sidePad = max(minMargin, (width - maxColumnPx) / 2)`. Taps in the margins still place the caret, and the swipe area covers the whole screen.
- `imePadding` animates frame-by-frame, so the EditText re-measures each frame and the caret follows. Re-measuring with an unchanged width doesn't rebuild the text layout (TextView only calls `makeNewLayout` on width changes). **Verify smoothness on device with a 200 KB document** (medium confidence).
- Display cutout in landscape: horizontal `displayCutout` padding as above. `safeDrawing` also covers `captionBar` for desktop windowing.
- Android 17 IME change (all apps): "when the device's configuration changes (for example, through rotation), and this is not handled by the app itself, the previous IME visibility is not restored." With the recommended `configChanges` the activity isn't recreated on rotation, so it's moot. For recreation cases (e.g. locale change), re-request with `InputMethodManager.showSoftInput(view, SHOW_IMPLICIT)` if the editor had focus.

---

## 7. Large screens, desktop windowing, adaptive layout

Facts:
- Android 16 (target 36): on displays with smallest width ≥ 600 dp, `screenOrientation`, `resizableActivity`, `minAspectRatio`, `maxAspectRatio`, `setRequestedOrientation()` and `getRequestedOrientation()` are ignored. There was a temporary opt-out, `<property android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" android:value="true"/>`, which "won't apply when targeting API 37+" (behavior-changes-16).
- Android 17 (target 37): "removes opt-out capability — changes are mandatory". Exceptions: games (`android:appCategory`), sw < 600 dp, or user opt-in in aspect-ratio settings. Test with compat flag `UNIVERSAL_RESIZABLE_BY_DEFAULT` (17/changes/ff-restrictions-ignored).
- The Android 17 release post mentions App Bubbles (long-press the launcher icon), the Bubble Bar, interactive desktop PiP, Desktop Mode on connected displays, and "Googlebooks". **The app must work in tiny windows too** (compact width and height).
- Window size class breakpoints (verified constants in window-core 1.5.1 `WindowSizeClass`): `WIDTH_DP_MEDIUM_LOWER_BOUND = 600`, `WIDTH_DP_EXPANDED_LOWER_BOUND = 840`, `WIDTH_DP_LARGE_LOWER_BOUND = 1200`, `WIDTH_DP_EXTRA_LARGE_LOWER_BOUND = 1600`, `HEIGHT_DP_MEDIUM_LOWER_BOUND = 480`, `HEIGHT_DP_EXPANDED_LOWER_BOUND = 900`. Methods: `isWidthAtLeastBreakpoint(Int)`, `isHeightAtLeastBreakpoint(Int)`, `isAtLeastBreakpoint(w, h)`.
- material3-adaptive **1.3.0**: `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth)` is `@Deprecated("Please use V2 version of this function to support L and XL width size classes.")`. Use **`currentWindowAdaptiveInfoV2()`** (both javap-verified in the 1.3.0 AAR).

Recommendation:
```kotlin
val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
val expanded = wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
if (expanded) {
    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(ui.libraryPaneVisible) { LibraryPane(Modifier.width(320.dp).fillMaxHeight()) }
        EditorSurface(Modifier.weight(1f))           // swipe TowardEnd/TowardStart toggles the pane
    }
} else {
    ModalNavigationDrawer(drawerState = drawerState, gesturesEnabled = drawerState.isOpen,
        drawerContent = { ModalDrawerSheet(drawerState) { LibraryContent() } }) { EditorSurface() }
}
```
- Text column: a max measure of 64/72/80 "characters" (iA Writer's line-length setting, per ia.net/writer/support/basics/settings). Compute px = N × `paint.measureText("0")` of the body font at the current size, then center.
- Don't lock orientation anywhere. Test resizing with the emulator's resizable/desktop profiles and split screen.

---

## 8. Android 17 (API 37) and Android 16 behavior changes: relevance

### 8.1 Android 17 — apps targeting 37 (developer.android.com/about/versions/17/behavior-changes-17, updated 2026-09-16)
| Change | Relevance to mdwriter |
|---|---|
| Orientation/resizability/aspect ratio **ignored on sw ≥ 600 dp, no opt-out** | **High.** Adaptive layout is mandatory (§7). |
| Config changes `CONFIG_KEYBOARD`, `KEYBOARD_HIDDEN`, `NAVIGATION`, `TOUCHSCREEN`, `COLOR_MODE`, and `UI_MODE` only for desk-type changes **no longer restart activities**; new `android:recreateOnConfigChanges` attribute to opt back in (runtime-changes guide; the 17 blog; the Beta 1 blog frames it for apps targeting 37) | Medium, and good for an editor: attaching a hardware keyboard won't recreate. Night-mode `uiMode` changes aren't in this list, so we still declare `uiMode` in `configChanges`. |
| New lock-free `MessageQueue` | Low. Don't reflect on MessageQueue; avoid old libraries that do. |
| `static final` fields unmodifiable via reflection | Low. |
| Background audio hardening, ECH, CT on by default, `ACCESS_LOCAL_NETWORK`, SMS OTP delay, CP2 PII/SQL, safer native DCL, RemoteViews memory limit, BluetoothSocket read, NPU feature flag, `setContentCaptureEnabled` deprecation, WebView UA reduction | None (no network, audio, contacts, widgets or native code). If we ever add a home-screen widget, mind the RemoteViews bitmap memory cap. |
| Hide last-typed password character on physical keyboards | None. |
| Accessibility for complex IME physical-keyboard typing (`TextAttribute.isTextSuggestionSelected`, `AccessibilityEvent.setTextChangeTypes`) | Low. "TextView handles this automatically by default", so EditText gets it for free. |

### 8.2 Android 17 — all apps (behavior-changes-all)
| Change | Relevance |
|---|---|
| **IME visibility not restored after unhandled rotation** | Medium. Mitigated by `configChanges`; see §6. |
| App memory limits (RAM-based; `"MemoryLimiter:AnonSwap"`) | Medium. Cap file sizes and avoid span explosions on huge docs. |
| Restrict implicit URI grants (auto-grant removal in **Android 18**) | **Medium.** Share-out must add `FLAG_GRANT_READ_URI_PERMISSION` explicitly; use StrictMode `detectImplicitUriPermissionGrant()`. |
| Global keyboard navigation shortcuts (summary page: "Meta+Back/F1 overrides app shortcuts") | Low. Use **Ctrl**-based app shortcuts. Details **UNVERIFIED** (the item appears on the 17 summary page; the linked detail text wasn't retrievable). |
| Touchpad relative events during pointer capture; per-app keystore limits; cross-profile loopback block; Bluetooth re-pairing; background audio | None. |

### 8.3 Android 17 new APIs worth knowing
- DocumentsContract **trash APIs**, `COLUMN_CONTENT_SYNC_STATE_FLAGS`, `COLUMN_ORIGINAL_RELATIVE_PATH`, and `CATEGORY_APPROVED_DOCUMENT_HANDLER` (needs system file-manager allowlisting, so not for us). See §2.3.
- `StrictMode.VmPolicy.Builder.detectImplicitUriPermissionGrant()`.
- Handoff (`Activity.setHandoffEnabled`, `onHandoffActivityDataRequested`) and "Continue On": could later hand off the open document between devices. Skip for v1 (it would require a cross-device data path, which conflicts with "no cloud").
- ProfilingManager triggers (`TRIGGER_TYPE_COLD_START`, `TRIGGER_TYPE_OOM`, `TRIGGER_TYPE_ANOMALY`). Optional for debugging.
- Blog statement: "Android development is now Compose-first … Legacy View components (in the android.widget package) … are now in maintenance mode. They will receive only critical bug fixes, and no new features." This is a relevant signal for the editor track's EditText vs BasicTextField decision. EditText remains fully supported, but new text features will land in Compose.

### 8.4 Android 16 changes still relevant (target 36 is implied by target 37)
| Change | Relevance |
|---|---|
| Edge-to-edge opt-out disabled | High (§6). |
| Predictive back on by default; `onBackPressed`/`KEYCODE_BACK` not delivered | High (§5). |
| `elegantTextHeight` deprecated and ignored | Low-medium. Don't rely on it for Thai/Arabic/Indic line heights. Consider `TextView.setUseBoundsForWidth(true)` and `setShiftDrawingOffsetForStartOverhang(true)` (API 35) to avoid clipping italic overhangs, and `setLocalePreferredLineHeightForMinimumUsed(true)` / `setMinimumFontMetrics` (API 35) for tall scripts. Editor track decides. |
| Large-screen orientation/resizability ignored (temporary opt-out) | Superseded by 17. |
| Fixed-rate `scheduleAtFixedRate` catch-up change | Low. Use coroutines for the autosave ticker. |
| All apps: `announceForAccessibility()` / `TYPE_ANNOUNCEMENT` **deprecated** | Medium. Use pane titles, live regions (`Modifier.semantics { liveRegion = LiveRegionMode.Polite }`) or snackbars. |
| All apps: JobScheduler quota tightening | None (no background jobs). |
| All apps: 16 KB page-size compatibility mode | None (no native libs). The AVD is `ps16k` anyway, which is good for testing. |
| All apps: intent-redirection hardening | None (we don't forward nested intents). |
| Safer intents opt-in (`android:intentMatchingFlags="enforceIntentFilter"`) | Optional; not needed. |

---

## 9. App architecture (2026 best practice for a small single-activity Compose app)

Google's architecture recommendations (developer.android.com/topic/architecture/recommendations):
- **Strongly recommended:** a data layer with repositories ("even if they contain only a single data source"); a UI layer in Compose; coroutines and flows between layers; UDF; `collectAsStateWithLifecycle`; ViewModels at screen level only and without references to `Context`/`Activity`; plain state holders for reusable UI; `LifecycleStartEffect`/`LifecycleResumeEffect` instead of overriding `onResume`.
- **Recommended:** a single `uiState: StateFlow` built with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`; no `AndroidViewModel`.
- **Domain layer:** "Recommended in big apps". We keep a tiny pure-Kotlin `domain`/`markdown` package for the engine and naming rules, **no** use-case classes.
- **DI:** "Use Hilt or manual dependency injection in simple apps. Use Hilt if … Multiple screens with ViewModels; Uses WorkManager; Has ViewModels scoped to the navigation back stack." None of those apply, so use **manual `AppContainer`** (pattern from developer.android.com/training/dependency-injection/manual: an `AppContainer` class held by the custom `Application`).

Proposed packages (single `:app` module is fine; an optional pure-JVM `:markdown` module helps fast tests):
```
app/src/main/java/<pkg>/
  MdWriterApp.kt            Application; val container = AppContainer(this); StrictMode in debug
  AppContainer.kt           applicationScope, dispatchers, DataStore, stores, repositories, AutosaveCoordinator
  MainActivity.kt           edge-to-edge, intents (onCreate + addOnNewIntentListener), keyboard shortcuts helper
  data/
    storage/  DocumentStore.kt (interface), InternalStore.kt, SafTreeStore.kt, ExternalDocAccess.kt,
              AtomicWriter.kt, TextCodec.kt (BOM/UTF-8/line endings), FileNames.kt
    library/  LibraryRepository.kt (locations, listing, observers, trash/undo)
    document/ DocumentRepository.kt (open/load/save/conflicts/recovery)
    settings/ SettingsRepository.kt (DataStore Preferences)
  domain/     (pure Kotlin) TitleFromContent.kt, SwipeTuning.kt, models
  ui/
    editor/   EditorScreen.kt, EditorViewModel.kt, MarkdownEditText.kt (View), EditorHost.kt (AndroidView), SwipeNav.kt
    library/  LibraryDrawer.kt, LibraryPane.kt, LibraryViewModel.kt (or folded into EditorViewModel)
    preview/  PreviewOverlay.kt
    theme/    Theme.kt, Typography.kt, SystemBars.kt
```
- ViewModel creation without Hilt: `viewModel { EditorViewModel(container.documents, container.library, container.settings, createSavedStateHandle()) }` (lifecycle-viewmodel-compose initializer DSL).
- **No navigation library.** One screen; drawer, preview and find are state flags in `uiState`. Navigation3 1.2.0 is stable but unnecessary.
- Coroutines: inject `CoroutineDispatcher`s (IO/Default/Main) through the container for tests. The app-level `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` is used for saves (§2.8).
- **Settings:** DataStore Preferences 1.2.1. Keys: theme (system/light/dark), fontFamily, fontSize (sp), lineWidth (64/72/80), focus/typewriter flags, linkedTreeUri, lastOpenDoc, swipeToPreview, autoNamed map (JSON string), and hint-card-dismissed.
- **StrictMode (debug only):**
  ```kotlin
  StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
  StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
      .detectLeakedClosableObjects().detectContentUriWithoutPermission().detectUnsafeIntentLaunch()
      .detectImplicitUriPermissionGrant()      // API 37
      .detectActivityLeaks().penaltyLog().build())
  ```
  (All method names verified on the StrictMode builder reference pages.)
- **Logging:** a tiny `Log` wrapper gated on `BuildConfig.DEBUG`, plus an R8 `-assumenosideeffects class android.util.Log { v(...); d(...); }` rule. No Timber (fewer dependencies; no network anyway).
- **Error handling:** `sealed interface StorageError { NotFound; PermissionLost; ReadOnly; ProviderFailure(cause); TooLarge(bytes); Encoding }`. Repositories return `Result`-like values, and the UI shows a non-modal banner or snackbar. **Never drop user text.** On any save failure, keep the buffer dirty, keep the recovery copy, and retry with backoff (1 s, 2 s, 5 s, 10 s).
- **Testing strategy:**
  - Pure JVM (fast; `make test`): Markdown engine, `TitleFromContent`, `FileNames.sanitize`, `TextCodec` (BOM, CRLF, invalid UTF-8), `AtomicWriter` (TemporaryFolder), `InternalStore` (java.io on TemporaryFolder), `AutosaveCoordinator` with `kotlinx-coroutines-test` virtual time (debounce 1 s, max latency 10 s, flush), and ViewModel state via Turbine + fakes (`FakeDocumentStore`). "Prefer fakes to mocks" is strongly recommended by Google.
  - Robolectric 4.17 (JVM; supports SDK 37): `SafTreeStore` against a **test `DocumentsProvider`** registered with `Robolectric.buildContentProvider(...)`, intent parsing (`handleIntent`), DataStore settings, and backup-rules XML presence. Keep Robolectric for Android-framework glue only. JDK ≥ 17 may need jvmFlags per robolectric.org getting-started (release notes).
  - Instrumented on the AVD (`make itest` → `connectedDebugAndroidTest`): Compose UI tests for drawer open/close via swipe (`performTouchInput { swipeRight() }` on the editor), "swipe with selection active doesn't open the drawer", the selection toolbar appearing only on selection, back ordering, IME + caret visibility (screenshot or bounds check), and a SAF round-trip with a debug-only `FakeDocumentsProvider` declared in `src/debug/AndroidManifest.xml`.
- **Baseline Profiles:** "~30% improvement from the first launch". For adb installs, `profileinstaller` 1.4.1 installs the profile at first launch and "Users of apps installed through non-Play-Store channels don't see benefits until background dexopt runs"; force it locally with `adb shell cmd package compile -f -m speed-profile <pkg>`. The generator needs API 33+ or root; the AVD (API 37.1) qualifies. Module: `androidx.baselineprofile` plugin 1.5.0 + `benchmark-macro-junit4` 1.5.0; task `:app:generateReleaseBaselineProfile`. Profiles only apply to **release** builds. **Plan:** a late-phase task. Meanwhile make `make install` build a minified release variant signed with the debug keystore (Compose debug builds are unoptimized; performance should be judged on release/R8 builds — standard Compose guidance, not re-verified today).

---

## 10. Miscellaneous platform topics

### 10.1 Hardware keyboard shortcuts
- Built into TextView/EditText (verified in `TextView.onKeyShortcut`): Ctrl+A select all, Ctrl+X/C/V, Ctrl+Z undo, Ctrl+Y and Ctrl+Shift+Z redo, Ctrl+Shift+V paste as plain text. Don't re-implement these.
- App shortcuts: override `onKeyShortcut` in `MarkdownEditText` and delegate to a `ShortcutHandler` (it gets first crack while the editor is focused). Also override `Activity.onKeyShortcut`, which is "called when a key shortcut event is not handled by any of the views in the Activity" (reference), for when the editor isn't focused. Suggested set: Ctrl+N new note, Ctrl+O / Ctrl+L library, Ctrl+F find, Ctrl+B bold, Ctrl+I italic, Ctrl+K link, Ctrl+1…6 heading level, Ctrl+0 body text, Ctrl+Shift+C inline code, Ctrl+R preview, Ctrl+D focus mode, Ctrl+S "save now" (autosave exists; show a tick). The iA-style mappings (R = preview, D = focus) are **UNVERIFIED** against iA docs; they're a design choice.
- Publish them to the system Keyboard Shortcuts Helper (Meta+/) via `override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int)` with `KeyboardShortcutGroup` / `KeyboardShortcutInfo(label, KeyEvent.KEYCODE_B, KeyEvent.META_CTRL_ON)` (compose keyboard-shortcuts-helper guide). `Activity.requestShowKeyboardShortcuts()` opens it.
- Compose parts (library, preview) use `Modifier.onPreviewKeyEvent { it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.N }`.
- Esc closes the drawer (M3 built-in), find bar and preview.

### 10.2 Accessibility
- TalkBack works natively on EditText inside `AndroidView`. It will read Markdown markers as typed ("hash hash Title"), which is correct for a plain-text editor.
- Gestures aren't discoverable for screen-reader users, so **always** provide buttons plus custom actions: `ViewCompat.addAccessibilityAction(editText, "Open library") { _, _ -> openLibrary(); true }` and "Show preview". Compose parts use `semantics { customActions = … }`.
- Font scale: set the editor size in **sp** (`setTextSize(TypedValue.COMPLEX_UNIT_SP, x)`). Android 14+ scales nonlinearly up to 200%. "Don't … use scaledDensity"; use `TypedValue.applyDimension`/`deriveDimension` for conversions (features-14). Line height in sp too.
- Dimmed Markdown markers: keep ≥ 3:1 contrast against the background (design choice; WCAG non-text guidance). Headings' size spans must not clip at 200% font.
- Android 16: `announceForAccessibility` is deprecated; use a live region for "Saved"/errors, or nothing.
- Android 16 "outline text" (replaces high-contrast text): `AccessibilityManager.isHighContrastTextEnabled()` + listener. Optional: thicken dim markers when on.

### 10.3 Haptics
- `View.performHapticFeedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)` when the swipe commits (the constant's doc: "a swipe/drag-style gesture … eligible at a certain threshold"). `CONFIRM` on a successful "Save a copy"/export, `REJECT` on failure, `TEXT_HANDLE_MOVE` is already done by the platform. Compose `LocalHapticFeedback` + `HapticFeedbackType.{Confirm, Reject, GestureThresholdActivate, SegmentTick, ToggleOn, ToggleOff, ContextClick, LongPress, TextHandleMove, KeyboardTap, VirtualKey, GestureEnd, SegmentFrequentTick}` (verified in ui 1.12.1). No `VIBRATE` permission is needed for these.

### 10.4 Splash screen
- Android 12+ gives all apps a splash that uses the launcher icon plus `windowBackground`. At minSdk 36, **no `core-splashscreen` is needed**. Use platform theme attributes: `android:windowSplashScreenBackground` (= editor background color, with a `values-night` variant), optional `android:windowSplashScreenAnimatedIcon`, and `android:windowSplashScreenIconBackgroundColor`. Icon sizes: 240 dp with an icon background (fits in a 160 dp circle) or 288 dp without (192 dp circle).
- Keep it on screen until the last document is loaded, using the `ViewTreeObserver.OnPreDrawListener` pattern from the guide with a ~300 ms cap. Or skip that: internal loads are fast.
- Theme parent: a platform `android:Theme.Material.Light.NoActionBar` / night variant (no MDC dependency needed for Compose).

### 10.5 Launcher icon
- Adaptive icon with **foreground, background and monochrome** layers. 108 dp canvas, **66 dp** safe zone, logo 48–66 dp. The monochrome layer drives themed icons (Android 13+). "Android 16 QPR 2+: Automatically generates themed icons for apps without monochrome layer", but ship our own anyway. Put it in `res/mipmap-anydpi/ic_launcher.xml` (the `-v26` qualifier is redundant at minSdk 36; harmless if kept). `roundIcon` is optional. A Play 512 px icon isn't needed (sideload).

### 10.6 Configuration changes (recommended manifest value and why)
`android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|density|keyboard|keyboardHidden|navigation|uiMode"`
- The guide says declaring this means "Compose recomposes your UI with the new values", **but** "you must manually update embedded components like AndroidView … which rely on Activity recreation to refresh their resources". So the EditText must take colors, fonts and sizes from Compose state (the `update` block), never from XML theme attributes. It also warns "it is impossible to entirely disable Activity recreation", so §2.10 is still required.
- Not included: `locale`/`layoutDirection`/`fontScale`/`fontWeightAdjustment`, so the activity recreates on those. That's fine and rare, and resources reload correctly.

### 10.7 Permissions and privacy ("no nothing")
- Declare **no** `<uses-permission>` at all. Check the merged manifest (`app/build/intermediates/merged_manifests/...`). AndroidX core adds a signature-level `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which is harmless. profileinstaller adds a DUMP-protected receiver, also harmless.
- No analytics, crash reporting SDKs, Play Services or Firebase. Local crash info is `ApplicationExitInfo` read on next launch in debug builds only (optional).

### 10.8 Per-app language
Not needed. Ship English strings only. Keep strings in `strings.xml` so they're translatable later.

---

## 11. Device test checklist (for the plan's QA tasks)

Standard adb commands (the `bmgr` ones are verified; the others are common, low-risk, but **UNVERIFIED today**):
- Gesture vs 3-button nav: Settings › System › Navigation mode. Test both. Predictive back on 3-button nav: long-press Back (Android 16+).
- Process death: background the app, then `adb shell am kill <pkg>`, relaunch from Recents. Expect the same doc, caret and scroll, no text loss. Also the "Don't keep activities" developer option.
- Font scale: `adb shell settings put system font_scale 2.0` (and 1.0 to reset).
- Night mode: `adb shell cmd uimode night yes|no` (must not recreate; colors update).
- Large screen: the emulator's resizable device or tablet AVD, split-screen, and desktop windowing if available. Also compat flag `UNIVERSAL_RESIZABLE_BY_DEFAULT` for regression.
- Backup: the `bmgr` sequence from §2.13.
- SAF: link `Documents/Notes`, create/rename/delete from mdwriter, modify the file from another app (e.g. Files → rename; `adb push` a changed file to `/sdcard/Documents/Notes/`) and confirm the list refreshes and the conflict banner appears when dirty.
- Share-in from Chrome (text), open `.md` from Files (VIEW), open a read-only file, share-out to Gmail/Drive (grant flags).
- Hardware keyboard: the emulator's host keyboard. Check Ctrl shortcuts and the Meta+/ helper.
- Swipe matrix: focused vs unfocused editor; caret mid-line; with selection (must NOT open); long-press then drag (must select, not open); fast vertical scroll with slight horizontal drift (must scroll); two-finger (must not open); RTL locale (direction flips).

---

## 12. Open questions / items the plan should flag to the user
1. Include notes in Google Auto Backup by default? (Recommended yes; the user said "no cloud", so ask or expose a toggle.)
2. Swipe right-to-left for Preview: default on or off?
3. On first run, show the "Use a folder…" hint card (mentioning `Documents/iA Writer`), or keep it totally silent?
4. Auto-rename files from the first line: default on (recommended) or off?
5. `singleTask` launch mode: acceptable that Back after opening a file from Files returns to Files, with mdwriter's library in its own task?

## 13. Risks
- Swipe thresholds need on-device tuning. False positives would feel like Obsidian's reported bug. Mitigation: strict arming conditions, one `SwipeTuning` object, and instrumented tests.
- SAF provider variance (cloud providers: pipes, null `lastModified`, no change notifications, slow IPC). Mitigation: the `"wt"` + fallback write, recovery copies, re-stat on `ON_START`, and gating on flags.
- The API 37 trash API is behind an aconfig flag and revokes grants, so treat it as optional and never rely on restore.
- AGP minimum for Compose 1.12 / navigationevent 1.1: AAR metadata says 9.1.0, the release notes say 9.2.0. The toolchain track must resolve this.
- Compose-first/View maintenance mode: an EditText-based editor is fully supported now but won't get new platform text features. Record this in the editor decision.
- `imePadding`-driven EditText resize on very large docs might stutter during the IME animation. Mitigation: size caps (§2.12); if needed, switch to `WindowInsets.imeAnimationTarget` so it resizes once.

---

## 14. Sources (all fetched 2026-09-24)
- Android 17 behavior changes (target 37): https://developer.android.com/about/versions/17/behavior-changes-17
- Android 17 behavior changes (all): https://developer.android.com/about/versions/17/behavior-changes-all
- Android 17 summary: https://developer.android.com/about/versions/17/summary
- Android 17 features: https://developer.android.com/about/versions/17/features
- Android 17 large-screen restrictions ignored: https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored
- Android 17 release post: https://android-developers.googleblog.com/2026/06/Android-17.html
- Android 17 Beta 1 post: https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html
- Wikipedia Android 17 (release date cross-check): https://en.wikipedia.org/wiki/Android_17
- Android 16 behavior changes (target 36 / all): https://developer.android.com/about/versions/16/behavior-changes-16 , https://developer.android.com/about/versions/16/behavior-changes-all
- Android 16 features: https://developer.android.com/about/versions/16/features
- Android 15 behavior changes: https://developer.android.com/about/versions/15/behavior-changes-15
- Android 12 behavior changes (root activity back): https://developer.android.com/about/versions/12/behavior-changes-all
- Android 11 storage: https://developer.android.com/about/versions/11/privacy/storage
- Runtime/config changes: https://developer.android.com/guide/topics/resources/runtime-changes
- App-specific storage: https://developer.android.com/training/data-storage/app-specific
- SAF documents: https://developer.android.com/training/data-storage/shared/documents-files
- MediaStore: https://developer.android.com/training/data-storage/shared/media
- ContentResolver reference (openOutputStream modes, takePersistableUriPermission): https://developer.android.com/reference/android/content/ContentResolver
- DocumentsContract / Document reference (incl. API 37 trash & sync-state): https://developer.android.com/reference/android/provider/DocumentsContract , https://developer.android.com/reference/android/provider/DocumentsContract.Document
- AtomicFile: https://developer.android.com/reference/android/util/AtomicFile
- TransactionTooLargeException: https://developer.android.com/reference/android/os/TransactionTooLargeException
- Saving UI state: https://developer.android.com/topic/libraries/architecture/saving-states
- Play policy All files access: https://support.google.com/googleplay/android-developer/answer/10467955
- CommonsWare, persisted grant limits: https://commonsware.com/blog/2020/06/13/count-your-saf-uri-permission-grants.html
- aCropalypse / "w" truncation: https://iliana.fyi/blog/acropalypse-now/ ; pipe truncate issue: https://github.com/kineapps/flutter_file_dialog/issues/62
- AOSP sources: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/Editor.java , …/TextView.java , …/EditText.java , …/core/java/android/text/method/Touch.java , …/core/java/android/widget/WidgetFlags.java , …/core/java/com/android/internal/content/FileSystemProvider.java , …/core/java/android/os/FileUtils.java , …/refs/tags/android-17.0.0_r1/packages/ExternalStorageProvider/…/ExternalStorageProvider.java , …/refs/tags/android-17.0.0_r1/core/java/android/provider/DocumentsProvider.java , …/core/java/android/provider/flags.aconfig , https://android.googlesource.com/platform/external/mime-support/+/refs/tags/android-17.0.0_r1/mime.types
- androidx sources: https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/NavigationDrawer.kt , …/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/input/pointer/PointerInteropFilter.android.kt , …/compose/foundation/foundation/src/androidMain/kotlin/androidx/compose/foundation/SystemGestureExclusion.android.kt , …/documentfile/documentfile/src/main/java/androidx/documentfile/provider/TreeDocumentFile.java , …/core/core/src/main/java/androidx/core/view/WindowCompat.java
- View.setSystemGestureExclusionRects: https://developer.android.com/reference/android/view/View
- Gesture navigation guide: https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav
- Compose predictive back setup: https://developer.android.com/develop/ui/compose/system/predictive-back-setup
- Activity release notes: https://developer.android.com/jetpack/androidx/releases/activity ; Core: https://developer.android.com/jetpack/androidx/releases/core ; NavigationEvent: https://developer.android.com/jetpack/androidx/releases/navigationevent ; Material3 adaptive: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- Compose insets: https://developer.android.com/develop/ui/compose/system/insets ; Views edge-to-edge: https://developer.android.com/develop/ui/views/layout/edge-to-edge
- WindowManager.LayoutParams (adjustResize deprecation note): https://developer.android.com/reference/android/view/WindowManager.LayoutParams
- Window size classes: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
- Data element / intent filters: https://developer.android.com/guide/topics/manifest/data-element , https://developer.android.com/guide/components/intents-filters
- Auto Backup & testing: https://developer.android.com/identity/data/autobackup , https://developer.android.com/identity/data/testingbackup
- Splash screen: https://developer.android.com/develop/ui/views/launch/splash-screen ; Adaptive icons: https://developer.android.com/develop/ui/views/launch/icon_design_adaptive
- Architecture recommendations: https://developer.android.com/topic/architecture/recommendations ; Manual DI: https://developer.android.com/training/dependency-injection/manual
- Baseline profiles: https://developer.android.com/topic/performance/baselineprofiles/overview , https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile
- Keyboard: https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/keyboard-shortcuts-helper , https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands , https://developer.android.com/reference/android/app/Activity
- Nonlinear font scaling: https://developer.android.com/about/versions/14/features#non-linear-font-scaling
- Haptics: https://developer.android.com/develop/ui/views/haptics/haptics-apis , https://developer.android.com/reference/android/view/HapticFeedbackConstants
- StrictMode builders: https://developer.android.com/reference/android/os/StrictMode.VmPolicy.Builder , https://developer.android.com/reference/android/os/StrictMode.ThreadPolicy.Builder
- TextView reference (API 35 text APIs): https://developer.android.com/reference/android/widget/TextView
- Robolectric 4.17: https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17 ; Maven Central metadata for robolectric, coroutines, junit, truth, turbine
- Google Maven metadata: https://dl.google.com/dl/android/maven2/<group>/<artifact>/maven-metadata.xml (all androidx artifacts listed in §1), plus downloaded AARs for `aar-metadata.properties` and `javap` checks
- iA Writer: https://ia.net/writer/support/help/writer-classic/ia-writer-legacy-for-android , https://ia.net/writer/support/basics/settings , https://www.thurrott.com/mobile/android/310882/ia-writer-abandons-android-citing-google-play-policy-changes
- Obsidian: https://forum.obsidian.md/t/settings-to-disable-the-swipe-left-right-action/96806 (and related threads in the search results), https://huggingface.co/spaces/anpigon/obsidian-qa-bot/blob/main/docs/obsidian-help/Obsidian/Android%20app.md (mirror of Obsidian Help)
