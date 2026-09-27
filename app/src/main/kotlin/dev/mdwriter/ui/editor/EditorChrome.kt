package dev.mdwriter.ui.editor

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mdwriter.R
import dev.mdwriter.markdown.Stats
import dev.mdwriter.ui.theme.LocalWriterTypography
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterMotion
import dev.mdwriter.ui.theme.WriterTheme
import kotlin.math.hypot

/**
 * The editor's only chrome (02 §5, T13): two faded glyph buttons (library top-start, overflow top-end) that fade
 * out on the first keystroke and back in per [ChromeVisibility]. While [visible] (and [overflowExpanded]) is
 * false, the glyphs are out of composition (`AnimatedVisibility`'s content is only composed while entering/
 * visible/exiting) — so they are never clickable while hidden. [stats] sits OUTSIDE the fade so T15 can render it
 * at reduced alpha while typing instead of hiding it outright.
 */
@Composable
fun EditorChrome(
    visible: Boolean,
    widthClass: WidthClass,
    @DrawableRes libraryIcon: Int,
    onLibrary: () -> Unit,
    overflowExpanded: Boolean,
    onOverflow: () -> Unit,
    onOverflowDismiss: () -> Unit,
    overflow: OverflowActions,
    modifier: Modifier = Modifier,
    stats: @Composable (chromeVisible: Boolean) -> Unit = {},
) {
    val colors = WriterTheme.colors
    val inset = WriterDimens.chromeGlyphInset(widthClass)
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(56.dp),
    ) {
        AnimatedVisibility(
            visible = visible || overflowExpanded,
            enter = fadeIn(tween(WriterMotion.CHROME_FADE_IN_MS, easing = WriterMotion.chromeFadeInEasing)),
            exit = fadeOut(tween(WriterMotion.CHROME_FADE_OUT_MS, easing = WriterMotion.chromeFadeOutEasing)),
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = inset)
                        .size(WriterDimens.touchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onLibrary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(libraryIcon),
                        contentDescription = stringResource(R.string.cd_library),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(WriterDimens.icon),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = inset)
                        .size(WriterDimens.touchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onOverflow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.cd_more),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(WriterDimens.icon),
                    )
                    OverflowMenu(expanded = overflowExpanded, onDismiss = onOverflowDismiss, actions = overflow)
                }
            }
        }
        Box(Modifier.align(Alignment.Center)) {
            stats(visible)
        }
    }
}

/**
 * Observes (never consumes) a tap whose down is within [topPx] of the top of this element; [PointerEventPass.Final]
 * would see the interop View's own consumption first, so [PointerEventPass.Initial] is used instead — the tap
 * still reaches the `EditText` underneath (a real tap there is harmless: it only moves the caret). Slop-bounded
 * (a drag/scroll starting in the strip does not count) and bounded by the platform long-press timeout (a
 * long-press-then-selection does not count either).
 */
fun Modifier.observeTopTap(
    topPx: () -> Float,
    onTap: () -> Unit,
): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.y > topPx()) return@awaitEachGesture
            val downTime = down.uptimeMillis
            val x0 = down.position.x
            val y0 = down.position.y
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!change.pressed) {
                    val dx = change.position.x - x0
                    val dy = change.position.y - y0
                    val elapsed = change.uptimeMillis - downTime
                    if (hypot(dx, dy) < viewConfiguration.touchSlop &&
                        elapsed < viewConfiguration.longPressTimeoutMillis
                    ) {
                        onTap()
                    }
                    return@awaitEachGesture
                }
                val dx = change.position.x - x0
                val dy = change.position.y - y0
                if (hypot(dx, dy) >= viewConfiguration.touchSlop) return@awaitEachGesture // drag/scroll: not a tap
            }
        }
    }

/**
 * The stats line (02 §5, T15): centred in [EditorChrome]'s glyph row, OUTSIDE the fade (never hides outright —
 * only dims to 60 % while [typing]). Hidden entirely when [stats] is `null` (Word count off). A tap cycles
 * [StatsDisplay] via [onCycle].
 */
@Composable
fun StatsLine(
    stats: Stats?,
    isSelection: Boolean,
    display: StatsDisplay,
    typing: Boolean,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stats == null) return
    val colors = WriterTheme.colors
    val alpha by animateFloatAsState(if (typing) 0.6f else 1f, tween(150), label = "statsAlpha")
    Text(
        text = formatStats(stats, display, isSelection),
        style = LocalWriterTypography.current.stats,
        color = colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .testTag("statsLine")
                .defaultMinSize(minHeight = WriterDimens.touchTarget)
                .wrapContentHeight(Alignment.CenterVertically)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.change_statistic),
                    onClick = onCycle,
                ).padding(horizontal = 8.dp)
                .alpha(alpha),
    )
}
