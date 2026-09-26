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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.editor.EditorController
import dev.mdwriter.ui.theme.LocalWriterColors
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.toolbar.FormatToolbarOverlay

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
        // No insets/offset between this Box and EditorSurface's own AndroidView (T09): SelectionState.anchor is
        // published in EditorScrollView viewport coords, so their top-left corners must stay aligned.
        EditorSurface(controller, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(colors.bg.copy(alpha = WriterDimens.STATUS_PROTECTION_ALPHA)),
        )
    }
}

/**
 * [EditorController.scrollView] plus the selection pill overlay (T09), sharing one coordinate space: the pill's
 * `Box` and the `AndroidView` both fill this same, unpadded parent, so [dev.mdwriter.editor.SelectionState.anchor]
 * (published in `EditorScrollView` viewport coords) lines up with the overlay with no extra math. The overlay is
 * the LAST child (drawn above the `AndroidView`) — T13's chrome glyphs will later share this same layering.
 */
@Composable
internal fun EditorSurface(
    controller: EditorController,
    modifier: Modifier = Modifier,
) {
    val selection by controller.selection.collectAsStateWithLifecycle()
    Box(modifier) {
        AndroidView(
            factory = {
                (controller.scrollView.parent as? ViewGroup)?.removeView(controller.scrollView)
                controller.scrollView
            },
            modifier = Modifier.fillMaxSize(),
        )
        FormatToolbarOverlay(
            state = selection,
            highlightEnabled = controller.highlightEnabled,
            readOnly = controller.isReadOnly,
            canPaste = controller::canPaste,
            onAction = controller::perform,
            modifier = Modifier.matchParentSize(),
        )
    }
}
