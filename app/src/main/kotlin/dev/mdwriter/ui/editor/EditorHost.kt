package dev.mdwriter.ui.editor

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.mdwriter.editor.EditorController
import dev.mdwriter.ui.theme.LocalWriterColors
import dev.mdwriter.ui.theme.WriterDimens

/**
 * The Compose host: [EditorController.scrollView] wrapped in the IME/cutout/nav-bar insets it needs (never the
 * EditText itself — rule 2 / factcheck C9/A16), plus the status-bar protection strip (02 §5) so scrolled text
 * never collides with system icons.
 */
@Composable
fun EditorHost(
    controller: EditorController,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWriterColors.current
    Box(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .imePadding()
            .windowInsetsPadding(
                WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal),
            ),
    ) {
        AndroidView(
            factory = {
                (controller.scrollView.parent as? ViewGroup)?.removeView(controller.scrollView)
                controller.scrollView
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(colors.bg.copy(alpha = WriterDimens.STATUS_PROTECTION_ALPHA)),
        )
    }
}
