package dev.mdwriter.data.export

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Result of a completed [ExportAllNotes.writeZip]: how many files were copied and their total (uncompressed) size. */
data class ExportSummary(
    val files: Int,
    val bytes: Long,
)

sealed interface ExportStatus {
    data object Idle : ExportStatus

    data class Running(
        val done: Int,
        val total: Int,
    ) : ExportStatus

    data class Done(
        val summary: ExportSummary,
    ) : ExportStatus

    data class Failed(
        val message: String,
    ) : ExportStatus
}

/**
 * "Export all notes…" (02 §10 / T19's Settings row, T18's own temporary entry point): zips the whole internal
 * library to a user-picked location, the backup path for release installs (research/build.md §9). [writeZip] is
 * pure `java.io`/`java.util.zip` — no Android — so it is directly JVM-testable against a [java.io.File] tree; only
 * [start] touches a real [android.content.ContentResolver].
 */
class ExportAllNotes(
    private val context: Context,
    private val libraryRoot: File,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _status = MutableStateFlow<ExportStatus>(ExportStatus.Idle)
    val status: StateFlow<ExportStatus> = _status.asStateFlow()

    fun suggestedName(today: LocalDate = LocalDate.now()): String = Companion.suggestedName(today)

    /** A no-op while an export is already [ExportStatus.Running]. Runs in [appScope] under [NonCancellable] (a
     * finishing activity must never truncate the zip mid-write); writes with `"wt"` (hard rule 15), falling back to
     * `"w"` for a provider that rejects the mode string outright. */
    fun start(uri: Uri) {
        if (_status.value is ExportStatus.Running) return
        _status.value = ExportStatus.Running(0, 0)
        appScope.launch {
            withContext(NonCancellable + io) {
                try {
                    val out =
                        try {
                            context.contentResolver.openOutputStream(uri, "wt")
                        } catch (_: IllegalArgumentException) {
                            context.contentResolver.openOutputStream(uri, "w")
                        } ?: throw IOException("null output stream for $uri")
                    val summary =
                        out.use { stream ->
                            writeZip(libraryRoot, stream) { done, total ->
                                _status.value = ExportStatus.Running(done, total)
                            }
                        }
                    _status.value = ExportStatus.Done(summary)
                } catch (e: IOException) {
                    _status.value = ExportStatus.Failed(e.message ?: e::class.simpleName ?: "unknown error")
                } catch (e: SecurityException) {
                    _status.value = ExportStatus.Failed(e.message ?: "permission denied")
                }
            }
        }
    }

    companion object {
        const val ZIP_ROOT = "mdwriter-notes/"
        private const val BUFFER_BYTES = 64 * 1024

        /** Pure — ISO `YYYY-MM-DD` (no instance needed, so it's directly JVM-testable without a real [Context]). */
        fun suggestedName(today: LocalDate = LocalDate.now()): String = "mdwriter-notes-$today.zip"

        /**
         * Walks [root] (skipping any dotfile/dot-folder — `.trash`/recovery live OUTSIDE `filesDir/library`, but a
         * temporary `.x.tmp` write-in-progress file does not, and must never end up in the zip), sorted by relative
         * path. A folder with no file anywhere below it becomes its own empty-folder entry
         * (`"mdwriter-notes/Drafts/"`); every real file is copied with a 64 KiB buffer, its own entry's `time` set
         * from [File.lastModified], and [onProgress] called after each one. Returns the final [ExportSummary].
         */
        fun writeZip(
            root: File,
            out: OutputStream,
            onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        ): ExportSummary {
            val files = mutableListOf<File>()
            val dirs = mutableListOf<File>()
            root
                .walkTopDown()
                .onEnter { it == root || !it.name.startsWith(".") }
                .forEach { f ->
                    if (f == root || f.name.startsWith(".")) return@forEach
                    if (f.isDirectory) dirs += f else files += f
                }
            val nonEmptyDirs = mutableSetOf<File>()
            for (file in files) {
                var p = file.parentFile
                while (p != null && p != root) {
                    nonEmptyDirs += p
                    p = p.parentFile
                }
            }
            val emptyDirs = dirs.filter { it !in nonEmptyDirs }.sortedBy { it.relativeToRootPath(root) }
            val sortedFiles = files.sortedBy { it.relativeToRootPath(root) }
            val total = sortedFiles.size
            var done = 0
            var totalBytes = 0L
            ZipOutputStream(out).use { zip ->
                for (dir in emptyDirs) {
                    zip.putNextEntry(
                        ZipEntry("$ZIP_ROOT${dir.relativeToRootPath(root)}/").apply {
                            time =
                                dir.lastModified()
                        },
                    )
                    zip.closeEntry()
                }
                val buffer = ByteArray(BUFFER_BYTES)
                for (file in sortedFiles) {
                    zip.putNextEntry(
                        ZipEntry(ZIP_ROOT + file.relativeToRootPath(root)).apply {
                            time =
                                file.lastModified()
                        },
                    )
                    FileInputStream(file).use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            zip.write(buffer, 0, read)
                            totalBytes += read
                        }
                    }
                    zip.closeEntry()
                    done++
                    onProgress(done, total)
                }
                zip.finish()
            }
            return ExportSummary(total, totalBytes)
        }

        private fun File.relativeToRootPath(root: File): String = relativeTo(root).path.replace(File.separatorChar, '/')
    }
}
