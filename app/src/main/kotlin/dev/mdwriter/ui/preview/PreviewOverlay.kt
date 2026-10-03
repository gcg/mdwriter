package dev.mdwriter.ui.preview

import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.mdwriter.R
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.ui.gesture.SwipeDir
import dev.mdwriter.ui.gesture.editorSwipeNav
import dev.mdwriter.ui.theme.LocalWriterColors
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterMotion
import kotlinx.coroutines.CancellationException

/**
 * Full-screen, read-only rendered preview (02 §8, T16): slides + fades in over 250 ms, tracks a predictive-back
 * gesture (scale + edge-follow translation) before actually closing, and closes on a start→end swipe, Back, Esc
 * or the `arrow_back` glyph. Composed at `MdWriterRoot`'s `PreviewSlot()` position — after every other overlay,
 * before the root's own find/selection `BackHandler`s (so those still win a same-frame race; preview and the
 * drawer are already mutually exclusive by construction, since opening either closes the other).
 */
@Composable
fun PreviewOverlay(
    open: Boolean,
    page: PreviewPage?,
    holder: PreviewWebViewHolder,
    currentDoc: DocRef?,
    onClose: () -> Unit,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWriterColors.current
    var back by remember { mutableFloatStateOf(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    LaunchedEffect(open) { if (open) back = 0f }

    PredictiveBackHandler(enabled = open) { events ->
        try {
            events.collect {
                back = it.progress
                edge = it.swipeEdge
            }
            onClose()
        } catch (e: CancellationException) {
            back = 0f
            throw e
        }
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val enterSpec = tween<IntOffset>(WriterMotion.PREVIEW_TRANSITION_MS, easing = WriterMotion.emphasizedDecelerate)
    val exitSpec = tween<IntOffset>(WriterMotion.PREVIEW_TRANSITION_MS, easing = WriterMotion.emphasizedAccelerate)

    AnimatedVisibility(
        visible = open,
        modifier = modifier,
        enter =
            slideInHorizontally(enterSpec) { w -> if (rtl) -w / 4 else w / 4 } +
                fadeIn(tween(WriterMotion.PREVIEW_TRANSITION_MS)),
        exit =
            slideOutHorizontally(exitSpec) { w -> if (rtl) -w / 4 else w / 4 } +
                fadeOut(tween(WriterMotion.PREVIEW_TRANSITION_MS)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.bg)
                .graphicsLayer {
                    val scale = 1f - 0.1f * back
                    scaleX = scale
                    scaleY = scale
                    translationX = (if (edge == BackEventCompat.EDGE_LEFT) 1 else -1) * back * 24.dp.toPx()
                }.editorSwipeNav(
                    enabled = { true },
                    accepts = { it == SwipeDir.TowardEnd },
                    onArmedDown = {},
                    onSwipe = { onClose() },
                ),
        ) {
            AndroidView(
                factory = { holder.get().also { (it.parent as? ViewGroup)?.removeView(it) } },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    wv.setBackgroundColor(colors.bg.toArgb()) // before loading content — no white flash in dark mode
                    wv.onEscape = onClose
                    holder.currentDoc = currentDoc
                    page?.let(wv::show)
                },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                    ).height(PreviewGlyphRowHeight),
            ) {
                Glyph(
                    icon = R.drawable.ic_arrow_back,
                    contentDescription = stringResource(R.string.preview_close),
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                if (onShare != null) {
                    Glyph(
                        icon = R.drawable.ic_share,
                        contentDescription = stringResource(R.string.overflow_share),
                        onClick = onShare,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun Glyph(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWriterColors.current
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .size(WriterDimens.touchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = colors.textSecondary,
            modifier = Modifier.size(WriterDimens.icon),
        )
    }
}

/** Height of the close/share glyph row; the page's top padding includes it (`MdWriterRoot`'s preview theme). */
val PreviewGlyphRowHeight = 56.dp
