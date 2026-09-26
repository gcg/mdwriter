# T10 — Storage layer: InternalStore, AtomicWriter, TextCodec, RecoveryStore

**Goal** — Build the pure data layer that keeps notes safe on disk: the document identity types (`DocRef`, `DocKey`,
`FolderRef`), the `DocumentStore` interface, crash-safe atomic writes, a text codec that round-trips BOMs and line
endings, the internal library store (`filesDir/library`) with folders, collision naming, an in-app trash with 30-day
purge, and the recovery-copy store. Nothing is visible to the user yet; T11 wires it into the editor. Everything
here is plain `java.io`/`java.nio` + coroutines and is tested with JUnit4 on a `TemporaryFolder` (no Robolectric).

**Depends on**
- **T01** — project builds, `make test` runs `:app` JVM tests (JUnit4, Truth, coroutines-test, Turbine on the test
  classpath), package `dev.mdwriter`.
- **T04** — `:core:markdown` has `DocTitle` (`dev.mdwriter.markdown.DocTitle`) including an excerpt helper
  (`DocTitle.excerpt(text: String): String?` = first non-empty line after the title line, Markdown markers stripped).
  Check `plans/STATUS.md` T04 entry. If `DocTitle.excerpt` does not exist, add it to `DocTitle.kt` yourself (pure,
  explicit `public`, 4 JUnit 6 cases: title-only → null, `"# T\n\nBody **b**"` → `"Body b"`, leading blank lines,
  front matter skipped) and record it under "Deviations".
- Can run in parallel with T05–T09 (touches only `data/` and its tests).

**Read first**
- `plans/01-architecture.md` §3 (package map, `data/`), §6.3 (data layer contract), §7 (threading), §8, §9 (size
  limits), §10 (hard rules), §11 (testing).
- `plans/research/platform.md` §2.2, §2.4, §2.6, §2.7, §2.11, §2.12.
- `plans/research/factcheck.md` §1 row A1 (only to know that trash for SAF is T14's business).

## Scope — In / Out
**In**
- `data/library/Location.kt`, `data/library/LibraryEntry.kt` (identity + listing model).
- `data/storage/`: `DocumentStore.kt`, `StorageError.kt`, `AtomicWriter.kt` (+ `KeyedMutex`), `TextCodec.kt`,
  `NoteFiles.kt` (+ `StorageLimits`), `Hashes.kt`, `TrashBin.kt`, `InternalStore.kt`, `RecoveryStore.kt`.
- JVM tests for all of the above.

**Out (do NOT build here)**
- `SafTreeStore`, `ContentIo`, persisted URI grants, linked folders → **T14**.
- `ExternalDocStore` (files opened from other apps), share/export → **T18**.
- `DocumentRepository`, `AutosaveCoordinator`, `LibraryRepository`, settings/DataStore, `AppContainer` wiring,
  calling `purgeTrash` on app start → **T11**.
- Any UI (drawer, rename dialogs, delete snackbar), auto-naming from the first line → **T12**.
- Do not touch `AndroidManifest.xml`, Compose code, or the editor engine.

## Files to create / modify
All paths are under `app/src/main/kotlin/dev/mdwriter/` unless they start with `app/src/test/`.
- `data/library/Location.kt` — `LocationId`, `DocRef`, `FolderRef`, `DocKey`, `DocRef.key()`, `DocKey.toRef()`, small helpers.
- `data/library/LibraryEntry.kt` — `LibraryEntry` (contract fields + `caps`), `EntryCaps`.
- `data/storage/DocumentStore.kt` — `FileStat`, `TrashToken`, `interface DocumentStore`.
- `data/storage/StorageError.kt` — `StorageError`, `StorageException`, `StorageError.userMessage()`.
- `data/storage/AtomicWriter.kt` — `AtomicWriter` (temp + fsync + ATOMIC_MOVE), `KeyedMutex`.
- `data/storage/TextCodec.kt` — `LineEnding`, `TextFormat`, `DecodeResult`, `TextCodec`.
- `data/storage/NoteFiles.kt` — extension/hidden rules, MIME map, collision naming, default order, head decoding; `StorageLimits`.
- `data/storage/Hashes.kt` — `Hashes.sha1Hex(ByteArray)`, `Hashes.sha1Hex(String)`.
- `data/storage/TrashBin.kt` — `filesDir/.trash/<uuid>/<name>` + `meta.json`; internal `FlatJson`.
- `data/storage/InternalStore.kt` — `DocumentStore` over `filesDir/library`.
- `data/storage/RecoveryStore.kt` — `noBackupFilesDir/recovery/<sha1(docKey)>.md`, `RecoveryCopy`.
- `app/src/test/kotlin/dev/mdwriter/data/library/LocationTest.kt`
- `app/src/test/kotlin/dev/mdwriter/data/storage/TextCodecTest.kt`
- `app/src/test/kotlin/dev/mdwriter/data/storage/AtomicWriterTest.kt`
- `app/src/test/kotlin/dev/mdwriter/data/storage/NoteFilesTest.kt`
- `app/src/test/kotlin/dev/mdwriter/data/storage/TrashBinTest.kt`
- `app/src/test/kotlin/dev/mdwriter/data/storage/InternalStoreTest.kt`
- `app/src/test/kotlin/dev/mdwriter/data/storage/RecoveryStoreTest.kt`

## Steps
1. Read the T01 and T04 STATUS entries. Confirm `make test` is green before you start.
2. Write `Location.kt` exactly as in Reference code §A (types are the 01 §6.3 contract; helpers are additions).
3. Write `LibraryEntry.kt` (§B) and `DocumentStore.kt` + `StorageError.kt` (§C). Note the two **additions** to the
   01 §6.3 interface: `displayName(ref)` and `LibraryEntry.caps`. Also update `plans/01-architecture.md` §6.3 in the
   same commit (add `suspend fun displayName(ref: DocRef): String`, `caps: EntryCaps = EntryCaps.ALL`,
   `TrashToken`, `StorageException`) and §3 (new files `NoteFiles.kt`, `Hashes.kt`, `TrashBin.kt`).
4. Write `Hashes.kt` (`MessageDigest.getInstance("SHA-1")`, lowercase hex; `sha1Hex(text)` hashes UTF-8 bytes).
5. Write `AtomicWriter.kt` verbatim from §D, then `AtomicWriterTest` (see Acceptance 3).
6. Write `TextCodec.kt` verbatim from §E, then `TextCodecTest`.
7. Write `NoteFiles.kt` from §F, then `NoteFilesTest`.
8. Write `TrashBin.kt` from §G (the `FlatJson` part is verbatim), then `TrashBinTest`.
9. Write `InternalStore.kt` following §H. Every public suspend function runs its body in `withContext(io)`; every
   `DocRef`/`FolderRef` that is not Internal throws `IllegalArgumentException`. Then `InternalStoreTest`.
10. Write `RecoveryStore.kt` from §I, then `RecoveryStoreTest`.
11. `make format`, `make test`, `make check`. Fix everything.
12. Append the STATUS entry (list the interface additions under "Deviations" and say 01 was updated) and commit
    `T10: storage layer (InternalStore, AtomicWriter, TextCodec, RecoveryStore)`.

## Reference code

### §A `data/library/Location.kt` (copy verbatim)
```kotlin
package dev.mdwriter.data.library

sealed interface LocationId {
    data object Internal : LocationId
    data class Tree(val treeUri: String) : LocationId
}

sealed interface DocRef {
    /** '/'-separated path relative to filesDir/library, e.g. "Drafts/Walk.md". Never starts with '/'. */
    data class InternalFile(val relPath: String) : DocRef
    data class TreeDoc(val treeUri: String, val documentId: String) : DocRef
    data class External(val uri: String, val writable: Boolean) : DocRef
}

/** Internal: rel path of the folder ("" = library root). Tree: SAF documentId (root = tree document id). */
data class FolderRef(val location: LocationId, val id: String) {
    companion object {
        val INTERNAL_ROOT = FolderRef(LocationId.Internal, "")
    }
}

/** Stable string id: "i:<relPath>" | "t:<treeUri>|<documentId>" | "x:<uri>". Used for settings, positions, recovery. */
@JvmInline
value class DocKey(val value: String)

fun DocRef.key(): DocKey = when (this) {
    is DocRef.InternalFile -> DocKey("i:$relPath")
    is DocRef.TreeDoc -> DocKey("t:$treeUri|$documentId")
    is DocRef.External -> DocKey("x:$uri") // 'writable' is NOT part of the identity
}

/** Inverse of [key]. External refs come back with writable=false; the caller re-checks the grant (T18). */
fun DocKey.toRef(): DocRef? = when {
    value.startsWith("i:") -> DocRef.InternalFile(value.substring(2)).takeIf { it.relPath.isNotEmpty() }
    value.startsWith("t:") -> {
        val rest = value.substring(2)
        val bar = rest.indexOf('|') // tree URIs are percent-encoded, so the FIRST '|' separates tree and docId
        if (bar <= 0 || bar == rest.lastIndex) null else DocRef.TreeDoc(rest.substring(0, bar), rest.substring(bar + 1))
    }
    value.startsWith("x:") && value.length > 2 -> DocRef.External(value.substring(2), writable = false)
    else -> null
}

val DocRef.location: LocationId?
    get() = when (this) {
        is DocRef.InternalFile -> LocationId.Internal
        is DocRef.TreeDoc -> LocationId.Tree(treeUri)
        is DocRef.External -> null
    }

val DocRef.InternalFile.fileName: String get() = relPath.substringAfterLast('/')
val DocRef.InternalFile.parentPath: String get() = relPath.substringBeforeLast('/', missingDelimiterValue = "")

/** Internal only: child rel path of this folder. */
fun FolderRef.childPath(name: String): String = if (id.isEmpty()) name else "$id/$name"
```

### §B `data/library/LibraryEntry.kt`
```kotlin
package dev.mdwriter.data.library

/** What the UI may offer for an entry. Internal entries are always ALL; T14 fills these from SAF COLUMN_FLAGS. */
data class EntryCaps(
    val write: Boolean = true,
    val rename: Boolean = true,
    val delete: Boolean = true,
    val createChildren: Boolean = true, // folders only: may create notes/folders inside
) {
    companion object { val ALL = EntryCaps() }
}

data class LibraryEntry(
    val name: String,            // display name WITH extension ("Walk.md") or folder name
    val isFolder: Boolean,
    val doc: DocRef?,            // non-null for files
    val folder: FolderRef?,      // non-null for folders
    val lastModified: Long?,
    val size: Long?,
    val excerpt: String?,        // DocTitle.excerpt of the first 2 KB; null for folders / empty files
    val caps: EntryCaps = EntryCaps.ALL,
)
```

### §C `DocumentStore.kt` + `StorageError.kt`
```kotlin
package dev.mdwriter.data.storage

data class FileStat(val lastModified: Long?, val size: Long?)

/** Returned by trash(); passed back to restore(). [trashId] is the TrashBin entry id (or a store-specific id). */
data class TrashToken(val original: DocRef, val displayName: String, val trashId: String)

interface DocumentStore { // implemented by InternalStore (T10), SafTreeStore (T14), ExternalDocStore (T18)
    suspend fun list(folder: FolderRef): List<LibraryEntry>
    suspend fun read(ref: DocRef): ByteArray
    suspend fun write(ref: DocRef, bytes: ByteArray)
    suspend fun stat(ref: DocRef): FileStat?                     // null = gone
    suspend fun displayName(ref: DocRef): String                 // ADDITION to 01 §6.3: name incl. extension
    suspend fun create(folder: FolderRef, displayName: String): DocRef
    suspend fun createFolder(parent: FolderRef, name: String): FolderRef
    suspend fun rename(ref: DocRef, newDisplayName: String): DocRef   // ALWAYS use the returned ref afterwards
    suspend fun trash(ref: DocRef): TrashToken
    suspend fun restore(token: TrashToken): DocRef?
    suspend fun move(ref: DocRef, to: FolderRef): DocRef
    fun changes(folder: FolderRef): Flow<Unit>
}
```
`StorageError.kt`: the sealed interface from 01 §6.3 with `data object NotFound`, `data object PermissionLost`,
`data object ReadOnly`, `data class TooLarge(val bytes: Long)`, `data object Encoding`,
`data class ProviderFailure(val cause: Throwable)`; plus
`class StorageException(val error: StorageError, cause: Throwable? = null) : java.io.IOException(error.toString(), cause)`
(stores signal every failure by throwing it) and `fun StorageError.userMessage(): String` returning:
NotFound "The file no longer exists", PermissionLost "mdwriter lost access to this file", ReadOnly "This file is
read-only", TooLarge "Too large to open (%.1f MB)", Encoding "Not a text file", ProviderFailure "Couldn't save:
<cause.message ?: class simpleName>". (Plain Kotlin strings are fine here; T20 may move them to resources.)

### §D `AtomicWriter.kt` (copy verbatim)
```kotlin
package dev.mdwriter.data.storage

class KeyedMutex {
    private val map = ConcurrentHashMap<String, Mutex>()
    suspend fun <T> withLock(key: String, block: suspend () -> T): T =
        map.computeIfAbsent(key) { Mutex() }.withLock { block() }
}

/**
 * Crash-safe replace: write a dot-prefixed temp file in the SAME directory, fsync it, then rename over the target
 * (rename(2) is atomic on one filesystem). A crash at any point leaves either the old or the new file, never a mix.
 * [openTemp] exists only so tests can inject a failing stream.
 */
class AtomicWriter(private val openTemp: (File) -> FileOutputStream = { FileOutputStream(it) }) {
    private val locks = KeyedMutex()

    /** Serialized per target path (one writer per document at a time). */
    suspend fun write(target: File, bytes: ByteArray) = locks.withLock(target.absolutePath) { writeBlocking(target, bytes) }

    fun writeBlocking(target: File, bytes: ByteArray) {
        val dir = target.absoluteFile.parentFile ?: throw IOException("no parent directory: $target")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val tmp = File(dir, ".${target.name}.${System.nanoTime()}.tmp") // dot prefix = hidden from listings
        try {
            openTemp(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
        // Best effort: persist the directory entry too (works on Linux/Android; ignored where unsupported).
        runCatching { FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) } }
    }
}
```

### §E `TextCodec.kt` (copy verbatim)
```kotlin
package dev.mdwriter.data.storage

enum class LineEnding(val chars: String) { LF("\n"), CRLF("\r\n"), CR("\r") }

/** How the file looked on disk, so saving writes the same bytes back. [convertedFrom1252]: will be saved as UTF-8. */
data class TextFormat(val bom: Boolean, val lineEnding: LineEnding, val convertedFrom1252: Boolean) {
    companion object { val DEFAULT = TextFormat(bom = false, lineEnding = LineEnding.LF, convertedFrom1252 = false) }
}

sealed interface DecodeResult {
    data class Text(val text: String, val format: TextFormat) : DecodeResult
    data object Binary : DecodeResult
}

object TextCodec {
    const val SNIFF_BYTES = 8 * 1024
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val CP1252: Charset = Charset.forName("windows-1252")

    fun hasBom(bytes: ByteArray): Boolean =
        bytes.size >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]

    fun decode(bytes: ByteArray): DecodeResult {
        val bom = hasBom(bytes)
        val start = if (bom) 3 else 0
        val sniffEnd = minOf(bytes.size, start + SNIFF_BYTES)
        for (i in start until sniffEnd) if (bytes[i] == 0.toByte()) return DecodeResult.Binary
        var converted = false
        val raw = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, start, bytes.size - start))
                .toString()
        } catch (e: CharacterCodingException) {
            converted = true
            String(bytes, start, bytes.size - start, CP1252)
        }
        return DecodeResult.Text(normalizeToLf(raw), TextFormat(bom, dominantLineEnding(raw), converted))
    }

    /** [text] should be LF-only (the editor contract); stray CR/CRLF (e.g. pasted) are normalized first. */
    fun encode(text: String, format: TextFormat): ByteArray {
        val lf = normalizeToLf(text)
        val body = (if (format.lineEnding == LineEnding.LF) lf else lf.replace("\n", format.lineEnding.chars))
            .toByteArray(StandardCharsets.UTF_8)
        return if (format.bom) BOM + body else body
    }

    fun dominantLineEnding(s: CharSequence): LineEnding {
        var lf = 0
        var crlf = 0
        var cr = 0
        var i = 0
        while (i < s.length) {
            when (s[i]) {
                '\r' -> if (i + 1 < s.length && s[i + 1] == '\n') { crlf++; i++ } else cr++
                '\n' -> lf++
            }
            i++
        }
        return when {
            crlf > lf && crlf >= cr -> LineEnding.CRLF
            cr > lf && cr > crlf -> LineEnding.CR
            else -> LineEnding.LF // ties and "no line breaks" -> LF
        }
    }

    fun normalizeToLf(s: String): String {
        if (s.indexOf('\r') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\r') {
                sb.append('\n')
                if (i + 1 < s.length && s[i + 1] == '\n') i++
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
```
(ktlint will reformat the one-line `when` branch; run `make format`.)

### §F `NoteFiles.kt` (sketch — fill in bodies)
```kotlin
object StorageLimits {
    const val LARGE_BYTES = 1L shl 20        // > 1 MiB: "Large document" notice (T11)
    const val READ_ONLY_BYTES = 5L shl 20    // > 5 MiB: open read-only (T11)
    const val MAX_OPEN_BYTES = 16L shl 20    // > 16 MiB: refuse with TooLarge (stores enforce on read)
    const val EXCERPT_BYTES = 2048
}

object NoteFiles {
    val EXTENSIONS = setOf("md", "markdown", "mdown", "mkd", "txt")
    fun extensionOf(name: String): String      // lowercase, no dot; "" if none ("a.MD" -> "md", ".md" -> "")
    fun baseName(name: String): String         // "Walk.md" -> "Walk"; "Walk" -> "Walk"
    fun isHidden(name: String): Boolean = name.startsWith(".")
    fun isSupported(name: String): Boolean = !isHidden(name) && extensionOf(name) in EXTENSIONS
    /** MIME for SAF createDocument (01 §10 rule 15): md/markdown -> text/markdown, txt -> text/plain, else octet-stream. */
    fun mimeFor(name: String): String
    /** "Untitled.md" -> first of "Untitled.md", "Untitled 2.md", "Untitled 3.md"... not in [taken] (case-insensitive). */
    fun uniqueName(desired: String, taken: Collection<String>): String
    /** Folders first (name, case-insensitive), then files newest lastModified first, then name. */
    val DEFAULT_ORDER: Comparator<LibraryEntry>
    /** Lenient UTF-8 decode of a file head: strips a BOM, drops a trailing partial character (U+FFFD at the end). */
    fun decodeHead(head: ByteArray, length: Int): String
}
```
`uniqueName` rule (exact): split `desired` into base + `.ext` (ext may be empty); candidate 1 = `desired`, candidate
n ≥ 2 = `"$base $n.$ext"` (or `"$base $n"` without ext). Do **not** parse existing numbers ("Untitled 2.md" desired
and taken → "Untitled 2 2.md"; that is fine and predictable). Compare with `equals(ignoreCase = true)` because linked
folders may be on case-insensitive filesystems.

### §G `TrashBin.kt`
Layout: `root/<uuid>/<displayName>` + `root/<uuid>/meta.json`. `meta.json` is a flat JSON object of **string** values.
Keys always present: `displayName`, `deletedAt` (epoch ms as a string), `source` (`"internal"`; T14 adds `"tree"`).
InternalStore adds `originalRelPath`.
```kotlin
class TrashBin(private val root: File) {
    data class Entry(val id: String, val dir: File, val meta: Map<String, String>) {
        val displayName: String get() = meta["displayName"].orEmpty()
        val deletedAt: Long get() = meta["deletedAt"]?.toLongOrNull() ?: 0L
        val payload: File get() = File(dir, displayName)
    }
    fun moveIn(file: File, meta: Map<String, String>): String      // Files.move into a new uuid dir; returns id
    fun copyIn(displayName: String, bytes: ByteArray, meta: Map<String, String>): String  // used by T14
    fun get(id: String): Entry?                                      // null if missing/corrupt
    fun remove(id: String)                                           // deleteRecursively
    fun purgeOlderThan(cutoffMillis: Long): Int                      // returns number of entries deleted
}
```
Write `meta.json` **before** moving the payload in (so a crash never leaves a payload without metadata) and use the
`AtomicWriter.writeBlocking` for it. `FlatJson` (copy verbatim; `org.json` is not usable in JVM unit tests):
```kotlin
internal object FlatJson {
    fun write(map: Map<String, String>): String = map.entries.joinToString(",", "{", "}") { (k, v) -> "${q(k)}:${q(v)}" }

    private fun q(s: String): String = buildString {
        append('"')
        for (c in s) when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
        append('"')
    }

    /** Parses exactly what [write] produces (flat object, string values). Throws IllegalArgumentException otherwise. */
    fun read(s: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        var i = s.indexOf('{').also { require(it >= 0) } + 1
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun str(): String {
            skipWs(); require(s[i] == '"'); i++
            val sb = StringBuilder()
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (s[i]) {
                        'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        else -> sb.append(s[i])
                    }
                } else sb.append(s[i])
                i++
            }
            i++
            return sb.toString()
        }
        skipWs()
        if (s[i] == '}') return out
        while (true) {
            val k = str(); skipWs(); require(s[i] == ':'); i++
            out[k] = str(); skipWs()
            if (s[i] == ',') { i++; continue }
            require(s[i] == '}'); return out
        }
    }
}
```

### §H `InternalStore.kt` (sketch — behaviour is normative, code shape is yours)
```kotlin
class InternalStore(
    val root: File,                       // filesDir/library (T11 passes it; created if missing)
    private val trash: TrashBin,          // filesDir/.trash
    private val writer: AtomicWriter = AtomicWriter(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
) : DocumentStore {
    private val mutations = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override fun changes(folder: FolderRef): Flow<Unit> = mutations.filter { it == folder.id }.map { }
    suspend fun purgeTrash(maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000): Int  // + deletes ".*.tmp" older than 1 h under root
    suspend fun newestDocument(): DocRef.InternalFile?                          // recursive, skips hidden; used by T11
    // ... DocumentStore members
}
```
Normative behaviour:
- **Path safety:** `fileFor(relPath)` rejects empty segments, `.`/`..`, leading `/` and `\`; then checks
  `candidate.canonicalPath` starts with `root.canonicalPath + File.separator` (canonicalize the root too — macOS
  temp dirs are symlinks). Violations → `IllegalArgumentException`.
- **list:** `dir.listFiles()` (null → `StorageException(NotFound)`); skip `NoteFiles.isHidden`; directories →
  folder entries (`folder = FolderRef(Internal, childPath)`, size null, excerpt null); files with
  `NoteFiles.isSupported` → file entries; everything else skipped. Excerpt: read **at most**
  `StorageLimits.EXCERPT_BYTES` via `RandomAccessFile`/`FileInputStream.readNBytes`, `NoteFiles.decodeHead`, then
  `DocTitle.excerpt(head)`; empty file → null. Return sorted by `NoteFiles.DEFAULT_ORDER` (T12 applies the user's sort).
- **read:** missing → `StorageException(NotFound)`; `length() > MAX_OPEN_BYTES` → `StorageException(TooLarge(len))`.
- **write:** `writer.write(file, bytes)` (creates missing parent dirs); then emit the parent folder id.
- **stat:** `FileStat(file.lastModified(), file.length())` if `file.isFile`, else null.
- **displayName:** `ref.fileName`.
- **create(folder, displayName):** `NoteFiles.uniqueName(displayName, names in dir)`, then `File.createNewFile()`
  (retry the next candidate if it returns false); emit; return `InternalFile(folder.childPath(name))`.
- **createFolder:** same naming (no extension), `mkdir()`.
- **rename(ref, newDisplayName):** reject names containing `/` or blank; if only the case changes, rename directly;
  otherwise `uniqueName` against siblings **excluding the file itself**; `Files.move` (no REPLACE_EXISTING); emit;
  return the new ref.
- **move(ref, to):** target must be Internal; `uniqueName` in the target dir; `Files.move`; emit both folder ids.
- **trash(ref):** `trash.moveIn(file, mapOf("source" to "internal", "originalRelPath" to relPath, "displayName" to name, "deletedAt" to clock()))`;
  emit; return `TrashToken(ref, name, id)`.
- **restore(token):** entry missing → null. Target = `originalRelPath`; recreate missing parent dirs; if the name is
  taken use `uniqueName`; move payload back; `trash.remove(id)`; emit; return the new ref.

### §I `RecoveryStore.kt`
```kotlin
data class RecoveryCopy(val text: String, val savedAt: Long)

/** Latest unsaved buffer per document (crash / SAF safety net). root = noBackupFilesDir/recovery (NOT backed up). */
class RecoveryStore(
    private val root: File,
    private val writer: AtomicWriter = AtomicWriter(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun fileFor(key: DocKey): File = File(root, Hashes.sha1Hex(key.value) + ".md")
    suspend fun write(key: DocKey, text: String)          // UTF-8 of the LF text, atomic
    suspend fun read(key: DocKey): RecoveryCopy?           // savedAt = file.lastModified()
    suspend fun delete(key: DocKey)
    suspend fun newerThan(key: DocKey, millis: Long): Boolean  // exists && lastModified > millis
}
```

## Acceptance criteria
1. `make test` runs and passes these test classes (names exact): `LocationTest`, `TextCodecTest`,
   `AtomicWriterTest`, `NoteFilesTest`, `TrashBinTest`, `InternalStoreTest`, `RecoveryStoreTest`. None uses
   Robolectric (`grep -rl "RobolectricTestRunner\|AndroidJUnit4" app/src/test/kotlin/dev/mdwriter/data/storage app/src/test/kotlin/dev/mdwriter/data/library` prints nothing).
2. `LocationTest`: `key()`/`toRef()` round-trip for all three `DocRef` kinds (External comes back `writable=false`);
   a docId containing `|` round-trips; `DocKey("t:")`, `DocKey("i:")`, `DocKey("zz")` → null.
3. `AtomicWriterTest`:
   - `failureMidWriteKeepsOldFile`: target contains `"old"`; `AtomicWriter(openTemp = { ThrowingAfterBytes(it, 2) })`
     (a `FileOutputStream` subclass that writes 2 bytes then throws `IOException`) → `write` throws, target still
     reads `"old"`, and the directory contains **no** file whose name ends in `.tmp`.
   - `failureAtRenameKeepsOldFile`: target path is a non-empty directory → throws, directory untouched, no `.tmp` left.
   - `concurrentWritesAreSerialized`: 50 coroutines on `Dispatchers.IO` write different payloads to one target →
     final content equals one of the payloads exactly (no interleaving), no `.tmp` left.
   - `createsMissingParentDirs`.
4. `TextCodecTest` (each asserts `encode(decode(x)) contentEquals x` unless stated):
   `lfRoundTrip`, `crlfRoundTrip` (format CRLF, text has no `\r`), `crRoundTrip`, `bomRoundTrip`
   (`format.bom == true`, text does not start with `﻿`), `bomOnlyFile` (text `""`),
   `emptyFile` (text `""`, `TextFormat.DEFAULT`, encode → 0 bytes), `emojiRoundTrip` (4-byte UTF-8),
   `mixedEndingsNormalizeToDominant` (`"a\r\nb\nc\r\n"` → CRLF, re-encode = `"a\r\nb\r\nc\r\n"` — **not** identical,
   by design), `invalidUtf8FallsBackTo1252` (`[0x63,0x61,0x66,0xE9]` → `"café"`, `convertedFrom1252 == true`,
   encode → UTF-8 `63 61 66 C3 A9`), `truncatedUtf8FallsBack`, `nulInFirst8KbIsBinary` (NUL at index 100 →
   `Binary`), `nulAfter8KbIsText` (NUL at index 9000 → `Text`), `encodeNormalizesStrayCr`
   (`encode("a\r\nb", LF)` → `"a\nb"`).
5. `NoteFilesTest`: supported/unsupported/hidden names (`.md .markdown .mdown .mkd .txt`, `.MD` supported,
   `.hidden.md` hidden, `notes.docx` unsupported); `mimeFor` table (`a.md`/`a.markdown` → `text/markdown`,
   `a.txt` → `text/plain`, `a.mdown`/`a.mkd` → `application/octet-stream`); `uniqueName("Untitled.md", {"untitled.md","Untitled 2.md"})`
   → `"Untitled 3.md"`; no-extension case; `decodeHead` drops a cut multi-byte char and a BOM.
6. `TrashBinTest`: `FlatJson` round-trips a map with quotes, backslashes, `\n`, a tab, non-ASCII and emoji;
   `moveIn`/`get`/`remove`; `purgeOlderThan` deletes only entries with `deletedAt < cutoff`; a dir without
   `meta.json` is ignored by `get` and deleted by `purgeOlderThan`.
7. `InternalStoreTest` (all via `runTest`, store built on `tmp.newFolder("library")` and `tmp.newFolder(".trash")`):
   `createThreeUntitledGivesCollisionNames` ("Untitled.md", "Untitled 2.md", "Untitled 3.md");
   `listHidesDotfilesTempFilesAndUnsupported` (a leftover `.Note.md.123.tmp`, `.obsidian/`, `x.docx` are absent);
   `listReturnsFoldersFirstThenNewestFirst`; `excerptReadsOnlyFirst2Kb` (a 1 MB file whose second line is
   `"Body line"` → excerpt `"Body line"`, and a file whose only body text starts after byte 4096 → excerpt null);
   `nestedFoldersCreateListMove`; `renameReturnsNewRefAndOldIsGone`; `renameCaseOnly`;
   `renameCollisionGetsSuffix`; `trashMovesFileAndWritesMeta` (`meta.json` has `originalRelPath` and `deletedAt`);
   `restorePutsFileBackEvenIfFolderWasDeleted`; `restoreWithNameTakenUsesUniqueName`;
   `purgeRemovesEntriesOlderThan30Days` (use the injected `clock`); `pathTraversalRejected` (`"../x.md"`,
   `"/etc/x"`, `"a//b.md"` → `IllegalArgumentException`); `readMissingThrowsNotFound`
   (`StorageException` with `error == StorageError.NotFound`); `readTooLargeThrows` (sparse file via
   `RandomAccessFile.setLength(17 MiB)`); `changesEmitsAfterCreateWriteRenameTrash` (Turbine `test {}` on
   `changes(FolderRef.INTERNAL_ROOT)`); `newestDocumentIsRecursive`.
8. `RecoveryStoreTest`: write/read round trip (text with CRLF-free Unicode), `fileFor` name is 40 hex chars + `.md`,
   `delete` removes, `newerThan` true/false around `File.setLastModified`, `read` of a missing key → null.
9. `grep -rn "import android\." app/src/main/kotlin/dev/mdwriter/data/storage/{AtomicWriter,TextCodec,NoteFiles,Hashes,TrashBin,InternalStore,RecoveryStore,DocumentStore,StorageError}.kt app/src/main/kotlin/dev/mdwriter/data/library/{Location,LibraryEntry}.kt`
   prints nothing (the whole T10 layer is plain JVM).
10. `make check` exits 0.

## Verification commands
```bash
make test
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:testDebugUnitTest --tests 'dev.mdwriter.data.storage.*' --tests 'dev.mdwriter.data.library.*'
grep -rn "import android\." app/src/main/kotlin/dev/mdwriter/data/storage app/src/main/kotlin/dev/mdwriter/data/library || echo "no android imports"
make format && make check
```
(No emulator needed for this task.)

## Pitfalls
- **Temp files must be dot-prefixed and in the same directory** as the target (cross-directory `ATOMIC_MOVE` fails
  with `AtomicMoveNotSupportedException` across filesystems). Listings must hide them (`NoteFiles.isHidden`).
- `out.fd.sync()` must happen **before** the rename, or a power loss can leave a renamed empty file.
- Never `REPLACE_EXISTING` in `rename`/`move`/`restore` — that silently destroys another note. Only the atomic
  save replaces.
- `TextCodec` must **not** add or strip a trailing newline (platform §2.11). Byte-identical round trips hold for
  uniform line endings only; mixed endings become the dominant one (tested, documented).
- Do not use `org.json`, `android.util.AtomicFile`, `android.util.Log` or anything from `android.*` in these files:
  JVM unit tests would hit "Method … not mocked"/stub errors (01 §11: pure logic stays JVM).
- `File.lastModified()` is millisecond-granular; tests that need "changed" timestamps must set them explicitly with
  `setLastModified` instead of sleeping.
- Every store function must run in `withContext(io)` with the **injected** dispatcher (tests pass a test dispatcher
  where they use virtual time; T11 relies on this).
- Excerpts must read ≤ 2 KB per file (never `readText()` the whole file in `list`) — a library of 500 notes must
  list in well under a second.
- `canonicalPath` on macOS resolves `/var` → `/private/var`; compare canonical against canonical.
- Keep `DocKey` strings stable forever: they are persisted by T11 (positions, `lastOpenDoc`, `autoNamed`) and name
  recovery files. Changing the format silently loses everyone's caret positions.

## Definition of done
- [ ] All files in "Files to create / modify" exist; no Android imports in them (Acceptance 9).
- [ ] The seven test classes pass; `make check` green.
- [ ] `plans/01-architecture.md` §3/§6.3 updated for `displayName`, `EntryCaps`/`caps`, `TrashToken`,
      `StorageException`, `NoteFiles.kt`, `Hashes.kt`, `TrashBin.kt`.
- [ ] `plans/STATUS.md` entry appended (what changed, test counts, deviations, "T11 must call
      `internalStore.purgeTrash()` on app start").
- [ ] One commit: `T10: storage layer (InternalStore, AtomicWriter, TextCodec, RecoveryStore)`.
