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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.R
import dev.mdwriter.editor.EditorController
import dev.mdwriter.ui.gesture.SwipeDir
import dev.mdwriter.ui.gesture.editorSwipeNav
import dev.mdwriter.ui.theme.LocalWriterColors
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.toolbar.FormatToolbarOverlay

/**
 * The Compose host: [EditorController.scrollView] wrapped in the IME/cutout/nav-bar insets it needs (never the
 * EditText itself — rule 2 / factcheck C9/A16), plus the status-bar protection strip (02 §5) so scrolled text
 * never collides with system icons. T13 adds the swipe-navigation gesture, the top-strip tap-to-show-chrome
 * gesture, and the "Open library" accessibility custom action — all layered on the SAME Box that hosts the
 * `AndroidView`, never touching the EditText's padding/insets (rule 2).
 */
@Composable
fun EditorHost(
    controller: EditorController,
    modifier: Modifier = Modifier,
    swipeEnabled: () -> Boolean = { false },
    swipeAccepts: (SwipeDir) -> Boolean = { true },
    onSwipeArmedDown: () -> Unit = {},
    onSwipe: (SwipeDir) -> Unit = {},
    onTopTap: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenPreview: () -> Unit = {},
) {
    val colors = LocalWriterColors.current
    val currentOnOpenLibrary by rememberUpdatedState(onOpenLibrary)
    val currentOnOpenPreview by rememberUpdatedState(onOpenPreview)
    val label = stringResource(R.string.library_open_library)
    val previewLabel = stringResource(R.string.library_show_preview)
    DisposableEffect(controller, label) {
        val id =
            ViewCompat.addAccessibilityAction(controller.editText, label) { _, _ ->
                currentOnOpenLibrary()
                true
            }
        onDispose { ViewCompat.removeAccessibilityAction(controller.editText, id) }
    }
    DisposableEffect(controller, previewLabel) {
        val id =
            ViewCompat.addAccessibilityAction(controller.editText, previewLabel) { _, _ ->
                currentOnOpenPreview()
                true
            }
        onDispose { ViewCompat.removeAccessibilityAction(controller.editText, id) }
    }
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
        EditorSurface(
            controller,
            Modifier.fillMaxSize(),
            swipeEnabled = swipeEnabled,
            swipeAccepts = swipeAccepts,
            onSwipeArmedDown = onSwipeArmedDown,
            onSwipe = onSwipe,
            onTopTap = onTopTap,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(colors.bg.copy(alpha = WriterDimens.STATUS_PROTECTION_ALPHA)),
        )
    }
}

/**
 * [EditorController.scrollView] plus the selection pill overlay (T09) and (T13) the swipe-navigation +
 * top-tap-to-show-chrome gestures, sharing one coordinate space: the pill's `Box` and the `AndroidView` both fill
 * this same, unpadded parent, so [dev.mdwriter.editor.SelectionState.anchor] (published in `EditorScrollView`
 * viewport coords) lines up with the overlay with no extra math. The overlay is the LAST child (drawn above the
 * `AndroidView`) — the chrome glyphs (`EditorChrome`, composed by `EditorScreen` above this) share this same
 * layering.
 */
@Composable
internal fun EditorSurface(
    controller: EditorController,
    modifier: Modifier = Modifier,
    swipeEnabled: () -> Boolean = { false },
    swipeAccepts: (SwipeDir) -> Boolean = { true },
    onSwipeArmedDown: () -> Unit = {},
    onSwipe: (SwipeDir) -> Unit = {},
    onTopTap: () -> Unit = {},
) {
    val selection by controller.selection.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val statusBarInsets = WindowInsets.statusBars
    val currentEnabled by rememberUpdatedState(swipeEnabled)
    val currentAccepts by rememberUpdatedState(swipeAccepts)
    val currentArmedDown by rememberUpdatedState(onSwipeArmedDown)
    val currentSwipe by rememberUpdatedState(onSwipe)
    val currentTopTap by rememberUpdatedState(onTopTap)
    val topPx =
        remember(density, statusBarInsets) {
            { with(density) { WriterDimens.chromeTapZone.toPx() + statusBarInsets.getTop(density) } }
        }
    Box(
        modifier
            .editorSwipeNav(
                enabled = { currentEnabled() },
                accepts = { currentAccepts(it) },
                onArmedDown = { currentArmedDown() },
                onSwipe = { currentSwipe(it) },
                rtl = rtl,
            ).observeTopTap(topPx = topPx, onTap = { currentTopTap() }),
    ) {
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
