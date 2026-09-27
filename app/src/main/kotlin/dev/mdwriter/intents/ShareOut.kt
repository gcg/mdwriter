package dev.mdwriter.intents

import android.app.Application
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.storage.DecodeResult
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.data.storage.TextCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Shares any [DocRef] out as a real file (T18 step 10): copies its bytes to `cacheDir/exports/<uuid>/<name>` (a
 * fresh sub-folder per share, so two shares never collide, and stale ones — over a day old — are swept on the way
 * in) and builds a chooser [Intent] for it.
 *
 * Two things here are load-bearing (01 pitfalls): the [FileProvider] authority is built from [Application.getPackageName]
 * at RUNTIME, never a literal string (the debug build's `applicationIdSuffix` makes ITS real package
 * `dev.mdwriter.debug`); and the share intent carries BOTH [Intent.FLAG_GRANT_READ_URI_PERMISSION] and an explicit
 * [ClipData] (Android 18+ stops honouring an implicit URI grant without one).
 */
class ShareOut(
    private val app: Application,
    private val library: LibraryRepository,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun intentFor(ref: DocRef): Intent =
        withContext(io) {
            val store = library.storeFor(ref)
            val name = store.displayName(ref)
            val bytes = store.read(ref)
            val root =
                File(app.cacheDir, "exports").apply {
                    mkdirs()
                    listFiles()
                        ?.filter { now() - it.lastModified() > EXPORT_MAX_AGE_MS }
                        ?.forEach { it.deleteRecursively() }
                }
            val dir = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            val file = File(dir, name).apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType(if (NoteFiles.extensionOf(name) == "txt") "text/plain" else "text/markdown")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, NoteFiles.baseName(name))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(name, uri) // Intent.setClipData returns void, not chainable.
            // A Binder transaction has a real size limit — a multi-hundred-KB EXTRA_TEXT can crash the chooser
            // with TransactionTooLargeException. The file itself (EXTRA_STREAM) has no such limit.
            (TextCodec.decode(bytes) as? DecodeResult.Text)
                ?.text
                ?.takeIf { it.length <= MAX_EXTRA_TEXT_CHARS }
                ?.let { send.putExtra(Intent.EXTRA_TEXT, it) }
            Intent.createChooser(send, null)
        }

    private companion object {
        const val EXPORT_MAX_AGE_MS = 86_400_000L
        const val MAX_EXTRA_TEXT_CHARS = 100_000
    }
}
