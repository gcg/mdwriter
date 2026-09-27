package dev.mdwriter.ui.library

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/**
 * "Use a folder…" / "Reconnect" picker (T14). The returned function optionally takes an `initial` tree/document
 * [Uri] to preselect — Reconnect passes the old tree so the user lands back where they started; "Use a folder…"
 * passes `null`, which falls back to `primary:Documents` (best-effort; `EXTRA_INITIAL_URI` is honored by
 * `ExternalStorageProvider` but not guaranteed by every provider).
 */
@Composable
fun rememberLinkFolderLauncher(onPick: (Uri) -> Unit): (Uri?) -> Unit {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(onPick) }
    return { initial ->
        launcher.launch(
            initial
                ?: DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Documents"),
        )
    }
}
