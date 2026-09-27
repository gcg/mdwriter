package dev.mdwriter.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.mdwriter.data.export.ExportAllNotes
import dev.mdwriter.data.export.ExportStatus
import dev.mdwriter.ui.editor.DocumentSession
import dev.mdwriter.ui.theme.WriterTheme
import kotlinx.coroutines.launch

/**
 * `() -> Unit` trigger for "Export all notes…" (T18 step 11; T19's Settings row and T18's own temporary "On this
 * device" long-press menu both call this the same way): flushes the current document first (a just-typed edit must
 * land before the zip is built), then launches the system "Create document" picker for a `.zip`; the returned URI
 * (if any — the user may cancel) starts [ExportAllNotes.start].
 */
@Composable
fun rememberExportAllNotes(
    exporter: ExportAllNotes,
    session: DocumentSession,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) exporter.start(uri)
        }
    return {
        scope.launch {
            session.flush()
            launcher.launch(exporter.suggestedName())
        }
    }
}

/** A 2 dp indeterminate-looking (until the file count is known) progress bar under the drawer header while
 * [status] is [ExportStatus.Running]; renders nothing otherwise. */
@Composable
fun ExportProgress(
    status: ExportStatus,
    modifier: Modifier = Modifier,
) {
    val running = status as? ExportStatus.Running ?: return
    LinearProgressIndicator(
        progress = { if (running.total > 0) running.done.toFloat() / running.total else 0f },
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = WriterTheme.colors.accent,
        trackColor = WriterTheme.colors.divider,
    )
}
