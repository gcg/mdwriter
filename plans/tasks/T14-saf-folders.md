# T14 — Linked folders (Storage Access Framework)

**Goal** The user can pick any folder with "Use a folder…" (for example `Documents/Notes`, a Syncthing folder or a
cloud provider). Its Markdown files then appear in the library next to "On this device". They can be opened, edited
(autosaved), created, renamed, moved and deleted like internal notes. Files changed by other apps refresh the list,
and they raise the conflict banner when the open note is dirty. A folder whose access was lost shows "Disconnected —
Reconnect". "Stop using this folder" unlinks it and never touches the files.

**Depends on**
- T13 (and through it T10–T12). T10 delivered `DocumentStore` (with `displayName`), `EntryCaps`, `LibraryEntry`,
  `NoteFiles` (`mimeFor`, `isSupported`, `uniqueName`, `decodeHead`), `StorageLimits`, `StorageException`, `TrashBin`,
  and `RecoveryStore`.
- T11: `LibraryRepository.storeFor(ref)` throws for `TreeDoc`. `DocumentRepository.save` writes the recovery copy
  first and detects conflicts with size + hash when `lastModified` is null. `AutosaveCoordinator.idleMsFor(TreeDoc)` is 2 s.
  `Settings.linkedTrees`. `EditorViewModel.onStart()`.
- T12: the `LibraryRepository` additions (`entries`, `rootOf`, `parentOf`, `nameOf`, `createUnique`, `rename`, `move`,
  `folderTree`, `prefix`, `search`, `invalidate`). Also `LibraryViewModel`, the `LibraryContent` "Locations"
  section with only "On this device", the Move… dialog, and the pending-delete + undo flow (trash is called only on commit).

**Read first**
- `plans/01-architecture.md` §6.3, §7 (SAF listing row), §10 rules 15 and 5.
- `plans/02-design-spec.md` §7 (Locations, rows, menus).
- `plans/research/platform.md` §2.2, §2.3 (all of it), §2.7, §2.9, §2.13 (grants are not restored).
- The STATUS entries of T10–T13. Grep the real names first:
  `grep -rn "fun \|class " app/src/main/kotlin/dev/mdwriter/data/library app/src/main/kotlin/dev/mdwriter/ui/library`.

## Scope — In / Out
In:
- `SafIo` (low-level SAF calls, reused by T18) and `SafTreeStore : DocumentStore`.
- `TreeGrants`, `LocationInfo`, `LocationState`.
- `LibraryRepository` locations/linking/routing/cross-location moves.
- Drawer location rows + the picker.
- ON_START revalidation, and a live refresh of the open folder.
- Rechecking the open document when its folder changes.

Out:
- External files opened from other apps (`ExternalDocStore`, intents) → **T18** (it reuses `SafIo`).
- Settings UI → **T19**. A sync-state dot (`COLUMN_CONTENT_SYNC_STATE_FLAGS`) → not in v1.
- A user-visible Trash view → not in v1.
- Do not change the autosave or conflict logic from T11 (only test it against SAF).

## Files to create / modify (all under `app/src/`)
- `main/kotlin/dev/mdwriter/data/storage/SafIo.kt`: `query`/`stat`/`displayName`/`flags`/`readAll`/`writeWt` (+ verify).
- `main/kotlin/dev/mdwriter/data/storage/SafTreeStore.kt`: `DocumentStore` for one tree.
- `main/kotlin/dev/mdwriter/data/storage/TrashBin.kt` (modify only if needed): T10 already declares `copyIn(displayName, bytes, meta): String`; implement/fix it only if T10 left it as a stub.
- `main/kotlin/dev/mdwriter/data/library/TreeGrants.kt`: take/release/check persisted grants; root display name.
- `main/kotlin/dev/mdwriter/data/library/LocationInfo.kt`: `LocationInfo`, `LocationState`.
- `main/kotlin/dev/mdwriter/data/library/LibraryRepository.kt` (modify): locations, link/unlink/reconnect, Tree routing, cross-location move.
- `main/kotlin/dev/mdwriter/ui/library/LinkFolderLauncher.kt`: `rememberLinkFolderLauncher(onPicked)`.
- `main/kotlin/dev/mdwriter/ui/library/LibraryViewModel.kt` (modify): locations state, select/link/unlink/reconnect.
- `main/kotlin/dev/mdwriter/ui/library/LibraryContent.kt` (modify): linked rows, Disconnected state, "Use a folder…", long-press menu.
- `main/kotlin/dev/mdwriter/ui/library/MoveDialog.kt` (or T12's file for it): list all Ready locations' trees.
- `main/kotlin/dev/mdwriter/ui/editor/EditorViewModel.kt` (modify): `checkExternalNow()` when the open doc's folder changes.
- `main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt` (modify): `ON_START` → `library.revalidate()` + `invalidate()`.
- `main/kotlin/dev/mdwriter/AppContainer.kt` (modify): `safIo`, `treeGrants`; pass them to `LibraryRepository`.
- `test/kotlin/dev/mdwriter/testing/TestDocumentsProvider.kt`: a file-backed `DocumentsProvider` for Robolectric.
- `test/kotlin/dev/mdwriter/data/storage/SafTreeStoreTest.kt`, `test/kotlin/dev/mdwriter/data/storage/SafIoTest.kt`
- `test/kotlin/dev/mdwriter/data/library/LibraryRepositoryTreeTest.kt`
- `test/kotlin/dev/mdwriter/data/document/DocumentRepositorySafTest.kt`: conflict via size/hash with null `lastModified`.

## Steps
1. Read the T10–T13 STATUS entries. Run `make test` and confirm it is green.
2. `TestDocumentsProvider` (Reference D) first. `SafIoTest` and `SafTreeStoreTest` are written against it (test-first).
3. `SafIo` (Reference A). `writeWt`:
   - Open with `"wt"`; on `IllegalArgumentException`/`UnsupportedOperationException` fall back to `"w"`.
   - Write, flush.
   - If `S_ISREG`: `truncate(size)` + `Os.fsync`.
   - Then re-query `COLUMN_SIZE`. A non-null value that is `!= bytes.size` → throw
     `StorageException(ProviderFailure(IOException("size mismatch")))`.
   - `SecurityException` → `PermissionLost`; `FileNotFoundException` → `NotFound`.
4. `SafTreeStore` (Reference B). Each `DocumentStore` method:
   - `list`: ONE `query` of `buildChildDocumentsUriUsingTree(tree, folder.id)` with the full projection.
     - Skip dotfiles and unsupported extensions. Sort client-side with `NoteFiles.DEFAULT_ORDER`.
     - Map `COLUMN_FLAGS` to `EntryCaps`.
     - Excerpt: read the 2 KB head, 4 in parallel (`Semaphore(4)`), cap 300 files per folder, LRU cache (500) keyed by
       `"$docId|$lastModified|$size"`. Build it with the same helper `InternalStore` uses
       (`grep -n excerpt app/src/main/kotlin/dev/mdwriter/data/storage/InternalStore.kt`).
     - Record `parentOf[docId] = folder.id` in a `ConcurrentHashMap`.
   - `read`: `openInputStream`, capped at `StorageLimits.MAX_OPEN_BYTES` (→ `TooLarge`).
   - `write`: `SafIo.writeWt`. Before writing, check the doc's `FLAG_SUPPORTS_WRITE` (from the last listing or a stat
     query). Missing → `ReadOnly`.
   - `stat`: `FileStat(lastModified, size)`, either may be null. No row / `FileNotFoundException` → `null`.
   - `displayName`: query `COLUMN_DISPLAY_NAME`.
   - `create`:
     - `DocumentsContract.createDocument(resolver, parentDocUri, NoteFiles.mimeFor(name), name)` (hard rule 15).
     - A `null` result → `ProviderFailure`.
     - The new ref is `TreeDoc(tree, getDocumentId(uri))`.
     - Read the display name back (the provider may add " (1)").
   - `createFolder`: `createDocument(…, Document.MIME_TYPE_DIR, name)`.
   - `rename`: `renameDocument(resolver, docUri, newName) ?: docUri`. Return the ref built from the **returned** URI.
     Move `parentOf` to the new id.
   - `move` (same tree):
     - If `FLAG_SUPPORTS_MOVE` and the parent is known (cache, else `findDocumentPath`), use
       `DocumentsContract.moveDocument(resolver, docUri, srcParentUri, dstParentUri)`.
     - Otherwise copy + `trash`.
   - `trash`: Reference B. `restore` → always `null` (Undo is handled by T12's pending delete; SAF trash revokes our
     grant, platform §2.3).
   - `changes(folder)`: Reference C.
5. `TrashBin.copyIn`: write `bytes` into `root/<uuid>/<displayName>` and write `meta.json`.
   - Meta: `source="tree"`, `treeUri`, `documentId`, `deletedAt`, and `originalRelPath=""`.
   - T10's 30-day purge must still pick these up (it reads `deletedAt`; check it and adjust).
6. `TreeGrants(context)` (Reference E) and `LocationInfo.kt`:
   - `data class LocationInfo(val id: LocationId, val name: String, val state: LocationState)`.
   - `enum class LocationState { Ready, Disconnected }`.
7. `LibraryRepository` changes:
   - `val locations: StateFlow<List<LocationInfo>>`: Internal first ("On this device"), then `settings.linkedTrees` in order.
   - `storeFor(ref)`: `TreeDoc` → the tree's `SafTreeStore` (one instance per tree, cached). A Disconnected tree →
     `StorageException(PermissionLost)`.
   - `storeFor(location)`, `rootOf(Tree(t)) = FolderRef(Tree(t), DocumentsContract.getTreeDocumentId(Uri.parse(t)))`.
   - `parentOf(TreeDoc)` from the store's cache, else `findDocumentPath`. `nameOf(TreeDoc)` = `displayName`.
   - `suspend fun linkTree(treeUri: Uri): LocationInfo`:
     - `grants.take(uri)` (read + write).
     - If the tree is already linked, return it.
     - Append to `linkedTrees`, name = `grants.rootName(uri)`.
   - `suspend fun unlinkTree(id: LocationId.Tree)`:
     - Remove from settings, `grants.release`, drop the store.
     - Remove `lastOpenDoc` if it points into that tree.
     - Never delete files.
   - `suspend fun reconnect(old: LocationId.Tree, picked: Uri)`:
     - Same URI → take the grant again.
     - Different URI → replace it at the same index, and release the old grant if it is still held.
   - `suspend fun revalidate()`, called on every ON_START:
     - A tree is `Ready` iff `grants.isGranted(tree)` AND a `stat` of its root document succeeds.
     - Otherwise it is `Disconnected`.
     - After a backup restore, grants are missing (platform §2.13), so the tree shows as Disconnected. Never auto-unlink.
   - `move(ref, to)`:
     - Same location → `store.move`.
     - Different location → copy + delete:
       1. `bytes = src.read(ref)`.
       2. Create a unique name in `to`.
       3. `dst.write(new, bytes)`.
       4. Verify `dst.stat(new)?.size` equals `bytes.size` (when non-null).
       5. `src.trash(ref)`.
       6. `positions.move(old.key(), new.key())`.
       7. Return `new`.
     - On failure after the create, trash `new` and rethrow. The source stays intact.
   - `search`/`folderTree`/`prefix` for Tree locations: run the same recursive walk over `SafTreeStore.list`
     (depth ≤ 8, ≤ 2,000 files, on IO).
8. `EditorViewModel`: when the open doc is a `TreeDoc`, collect `library.storeFor(parent.location).changes(parent)`
   while started (debounced 300 ms) → `checkExternalNow()`. This uses T11's `onStart` logic: silent reload if clean,
   banner if dirty.
9. UI (`LibraryContent` + `LibraryViewModel`, 02 §7 item 3):
   - Rows: `ic_phone_android` "On this device" · one row per linked tree (`ic_folder` + name) · `ic_create_new_folder`
     "Use a folder…" in `textSecondary`.
   - Tapping a Ready location → `currentFolder = rootOf(location)`, and the breadcrumb starts at that location's name.
   - A Disconnected row: name in `textSecondary`, second line "Disconnected" (12 sp), trailing `TextButton`
     "Reconnect" (`accent`) → the picker with `initial = the old tree URI`.
   - Long-press on a linked row → the menu "Stop using this folder" (no confirmation dialog: files are untouched).
     Then snackbar "Stopped using ‘Notes’. Files were not changed."
   - If the current folder's location becomes Disconnected or is unlinked → go to the Internal root.
   - Row menus gate actions on `entry.caps`:
     - hide Rename if `!rename`;
     - hide Delete if `!delete`;
     - hide Move… if `!delete` (a move needs a source delete);
     - the new-note glyph is disabled if the current folder's `createChildren` is false.
   - Move… dialog: every Ready location as a depth-0 header, followed by its `folderTree`.
10. `rememberLinkFolderLauncher` (Reference F). The initial location is best-effort `primary:Documents`.
11. `make format && make check`, then run the manual emulator test (Verification) and write the STATUS entry.

## Reference code
**A. `SafIo` projection + write (adapted from platform §2.3; the logic is exact)**
```kotlin
class SafIo(private val resolver: ContentResolver) {
    companion object {
        val PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_SIZE, Document.COLUMN_FLAGS)
        const val FLAG_SUPPORTS_TRASH_37 = 0x10000            // Document.FLAG_SUPPORTS_TRASH (API 37)
    }
    fun writeWt(uri: Uri, bytes: ByteArray) {
        val pfd = try { resolver.openFileDescriptor(uri, "wt") }
            catch (_: IllegalArgumentException) { null } catch (_: UnsupportedOperationException) { null }
            ?: resolver.openFileDescriptor(uri, "w") ?: throw StorageException(StorageError.ProviderFailure(IOException("null pfd")))
        pfd.use { p -> FileOutputStream(p.fileDescriptor).use { out ->
            out.write(bytes); out.flush()
            val regular = runCatching { OsConstants.S_ISREG(Os.fstat(p.fileDescriptor).st_mode) }.getOrDefault(false)
            if (regular) { out.channel.truncate(bytes.size.toLong()); runCatching { Os.fsync(p.fileDescriptor) } }
        } }
        val size = statRow(uri)?.size
        if (size != null && size != bytes.size.toLong()) throw StorageException(StorageError.ProviderFailure(IOException("size mismatch")))
    }
}
```
**B. `SafTreeStore.trash` + caps (sketch)**
```kotlin
override suspend fun trash(ref: DocRef): TrashToken = withContext(io) {
    val doc = ref as DocRef.TreeDoc; val uri = docUri(doc.documentId); val name = safIo.displayName(uri)
    val bytes = runCatching { readCapped(uri) }.getOrNull()
    if (bytes != null) trashBin.copyIn(name, bytes, mapOf("source" to "tree", "treeUri" to treeUri.toString(),
        "documentId" to doc.documentId, "deletedAt" to clock().toString(), "originalRelPath" to ""))   // app-side safety copy
    val flags = safIo.flags(uri)
    if (Build.VERSION.SDK_INT >= 37 && flags and SafIo.FLAG_SUPPORTS_TRASH_37 != 0) DocumentsContract.trashDocument(resolver, uri)
    else if (!DocumentsContract.deleteDocument(resolver, uri)) throw StorageException(StorageError.ProviderFailure(IOException("delete failed")))
    mutations.tryEmit(parentOf[doc.documentId] ?: ""); TrashToken(ref, name, trashId = "")
}
fun capsOf(flags: Int, isDir: Boolean) = EntryCaps(
    write = flags and Document.FLAG_SUPPORTS_WRITE != 0, rename = flags and Document.FLAG_SUPPORTS_RENAME != 0,
    delete = flags and Document.FLAG_SUPPORTS_DELETE != 0 || flags and SafIo.FLAG_SUPPORTS_TRASH_37 != 0,
    createChildren = isDir && flags and Document.FLAG_DIR_SUPPORTS_CREATE != 0)
```
**C. `changes(folder)` (sketch)**
- Keep the children cursor OPEN while the flow is collected. `ExternalStorageProvider` runs a `FileObserver` only while
  it is open (platform §2.3).
```kotlin
override fun changes(folder: FolderRef): Flow<Unit> = merge(mutations.filter { it == folder.id }.map { },
    callbackFlow {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folder.id)
        val cursor = runCatching { resolver.query(children, SafIo.PROJECTION, null, null, null) }.getOrNull()
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(self: Boolean) { trySend(Unit) } }
        cursor?.registerContentObserver(obs); resolver.registerContentObserver(children, true, obs)
        awaitClose { cursor?.unregisterContentObserver(obs); resolver.unregisterContentObserver(obs); cursor?.close() }
    }.conflate())
```
**D. `TestDocumentsProvider` (sketch; test source set)**
- Backed by `File` root `ctx.cacheDir/tdp`. Authority `dev.mdwriter.test.documents`.
- Document id = the path relative to root, with `"root"` for the root.
- Implements: `queryRoots`, `queryDocument`, `queryChildDocuments`, `openDocument`, `createDocument`,
  `renameDocument` (returns the NEW id), `deleteDocument`, and `isChildDocument`.
- It mimics the traps:
  - `openDocument` with `"w"` opens WITHOUT truncate (`MODE_WRITE_ONLY or MODE_CREATE`); `"wt"` adds `MODE_TRUNCATE`.
  - `createDocument("text/plain", "Note.md")` produces `Note.md.txt`.
  - A name collision appends `" (1)"`.
- Companion `var flags: Int` (default WRITE|RENAME|DELETE|DIR_SUPPORTS_CREATE|MOVE) and `var nullLastModified = false`.
- Register it in the test:
```kotlin
Robolectric.buildContentProvider(TestDocumentsProvider::class.java).create(ProviderInfo().apply {
    authority = AUTH; exported = true; grantUriPermissions = true
    readPermission = Manifest.permission.MANAGE_DOCUMENTS; writePermission = Manifest.permission.MANAGE_DOCUMENTS })
val tree = DocumentsContract.buildTreeDocumentUri(AUTH, "root")
```
(`DocumentsProvider.attachInfo` throws unless the provider is exported, has `grantUriPermissions`, and both permissions
are `MANAGE_DOCUMENTS`.)

**E. `TreeGrants` (sketch)**
```kotlin
class TreeGrants(private val context: Context) {
    private val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    fun take(tree: Uri) = context.contentResolver.takePersistableUriPermission(tree, rw)   // SecurityException -> PermissionLost
    fun release(tree: Uri) = runCatching { context.contentResolver.releasePersistableUriPermission(tree, rw) }
    fun isGranted(tree: Uri) = context.contentResolver.persistedUriPermissions
        .any { it.uri == tree && it.isReadPermission && it.isWritePermission }
    fun rootName(tree: Uri): String   // query COLUMN_DISPLAY_NAME of buildDocumentUriUsingTree(tree, getTreeDocumentId(tree))
}
```
**F. Picker**
```kotlin
@Composable fun rememberLinkFolderLauncher(onPicked: (Uri) -> Unit): (Uri?) -> Unit {
    val l = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(onPicked) }
    return { initial -> l.launch(initial ?: DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents", "primary:Documents")) }   // EXTRA_INITIAL_URI, best-effort
}
```

## Acceptance criteria
1. `make check` is green. The 4 new test classes pass: `SafIoTest`, `SafTreeStoreTest`, `LibraryRepositoryTreeTest`,
   `DocumentRepositorySafTest`.
2. `SafTreeStoreTest`:
   - `listUsesOneQueryAndFilters`: dotfiles, `.png` and `.obsidian/` are hidden; folders come first.
   - `createMdUsesTextMarkdown`: the name is `Note.md`, not `Note.md.txt`.
   - `createCollisionReadsBackName`: `Note (1).md`.
   - `renameReturnsNewRef`: the old id is gone, and reading via the new ref works.
   - `writeShorterContentTruncates`: 100 bytes then 10 → the file is exactly 10 bytes.
   - `deleteRemovesAndKeepsSafetyCopy`: a `.trash/<uuid>/meta.json` exists with `source=tree`.
   - `capsFollowFlags`: flags without RENAME → `caps.rename == false`.
   - `changesEmitsOnNotify`: `resolver.notifyChange(childrenUri)` → one emission.
   - `permissionLossMapsToPermissionLost`.
3. `LibraryRepositoryTreeTest`:
   - `linkAddsLocationWithRootName`.
   - `unlinkReleasesGrantAndKeepsFiles`.
   - `revalidateMarksDisconnectedWithoutGrant`.
   - `moveInternalToTreeCopiesThenTrashes`: bytes are identical; the source is in the internal trash.
   - `moveFailureKeepsSource`.
4. `DocumentRepositorySafTest`: with `nullLastModified = true`:
   - an external same-size change → `Conflict` (via hash);
   - a different size → `Conflict`;
   - no change → `Saved`.
5. Emulator:
   - The drawer shows "Notes" under Locations after linking `Documents/Notes`.
   - It lists the pushed `a.md`, and not `.hidden.md` or `x.png`.
6. Emulator: create a note in "Notes", type, and wait 3 s. `adb shell cat /sdcard/Documents/Notes/Untitled.md`
   shows the text.
7. Emulator: push a modified `a.md` while the drawer shows "Notes" → the row date/excerpt updates within 2 s, with no reopen.
8. Emulator: open `a.md`, type, and push a changed `a.md` within 1 s → the banner "Changed on disk" appears.
9. Emulator: "Stop using this folder" → the row disappears. `adb shell ls /sdcard/Documents/Notes` still lists every file.

## Verification commands
```bash
make check
export JAVA_HOME=$(/usr/libexec/java_home -v 21); ./gradlew :app:testDebugUnitTest --tests 'dev.mdwriter.data.storage.Saf*' --tests 'dev.mdwriter.data.library.*' --tests 'dev.mdwriter.data.document.DocumentRepositorySafTest'
make install-debug DEVICE=emulator-5554
ADB=~/Library/Android/sdk/platform-tools/adb; S="-s emulator-5554"
$ADB $S shell mkdir -p /sdcard/Documents/Notes
printf '# A\n\nfirst\n' > /tmp/a.md; $ADB $S push /tmp/a.md /sdcard/Documents/Notes/a.md
$ADB $S push /tmp/a.md /sdcard/Documents/Notes/.hidden.md; $ADB $S shell touch /sdcard/Documents/Notes/x.png
# In the app: swipe to library -> "Use a folder…" -> navigate to Documents/Notes -> "Use this folder" -> "Allow"
# (find buttons with: $ADB $S shell uiautomator dump /sdcard/ui.xml && $ADB $S shell cat /sdcard/ui.xml)
$ADB $S exec-out screencap -p > /tmp/t14-drawer.png
printf '# A\n\nchanged outside\n' > /tmp/a.md; $ADB $S push /tmp/a.md /sdcard/Documents/Notes/a.md
$ADB $S shell cat /sdcard/Documents/Notes/Untitled.md
```

## Pitfalls
- **Never use `DocumentFile.listFiles()`**: that is 1 + 3N IPC calls (platform §2.3). Use one `query` per folder with
  `SafIo.PROJECTION`. Providers may ignore `sortOrder`, so sort client-side.
- **Writes are `"wt"`** + truncate-if-regular + fsync + size verify (hard rule 15). **Create `.md` with `text/markdown`**
  (hard rule 15). Both have a test.
- **Always use the ref `rename`/`move` return**. On `ExternalStorageProvider` the doc id contains the path (01 §6.3).
  T12's `onCurrentRefChanged` must be called for the open doc.
- `DocumentsContract.trashDocument` and `Document.FLAG_SUPPORTS_TRASH` are API 37, and minSdk is 36:
  - guard with `Build.VERSION.SDK_INT >= 37` (lint `NewApi` is an error);
  - use our `FLAG_SUPPORTS_TRASH_37` literal so the field reference does not trigger `InlinedApi`.
- **Undo never depends on `restore`** (platform §2.3: the trash revokes our grant). T12 already delays `trash()` until the
  snackbar commits. `restore` returns `null`. The app-side safety copy is the extra net, purged after 30 days.
- `COLUMN_LAST_MODIFIED`/`COLUMN_SIZE` may be null → `FileStat(null, …)`. Never treat null as 0 for conflicts (T11's
  hash path handles it).
- Keep the change cursor open only while collected. Close it in `awaitClose`, or you leak a `FileObserver` per folder.
- Persisted grants don't survive backup/restore, and apps can hold at most 512. Always release on unlink, and never
  auto-unlink on revalidate.
- Robolectric fallback: if Robolectric's `ContentResolver.call`/`openFileDescriptor` routing into a `DocumentsProvider`
  does not work:
  - move `SafTreeStoreTest` + `SafIoTest` (and the provider) to `app/src/androidTest`;
  - declare the provider in `app/src/androidTest/AndroidManifest.xml` with `MANAGE_DOCUMENTS`;
  - run them with `make test-device DEVICE=emulator-5554`;
  - record the move in STATUS. Do not delete the tests.
- The picker "Allow" tap is a consent in the emulator's system UI, for the agent's own test app only. Never do it on a
  physical phone (README rule 5).

## Definition of done
- [ ] Acceptance 1–9 checked. The screenshots of the drawer (linked + disconnected state) are described in STATUS.
      For the disconnected state: `adb shell cmd uri revoke`… is not available, so unlink/relink, or explain how you
      forced it in the test.
- [ ] `make check` is green. `make test-device DEVICE=emulator-5554` is green if the fallback was used.
- [ ] STATUS.md entry:
  - the `SafIo` API (T18 reuses `writeWt`, `statRow`, `displayName`);
  - the `LibraryRepository` additions;
  - the `TrashBin.copyIn` addition (update 01 §3/§6.3 if names changed).
- [ ] Commit `T14: linked folders via SAF`.
