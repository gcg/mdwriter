package dev.mdwriter.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
 * The editor's top chrome (02 §5, T13): the overflow glyph (top-end) that fades out on the first keystroke and back
 * in per [ChromeVisibility]. The library has no glyph here — [LibraryHandle] hints at the swipe instead. While
 * [visible] (and [overflowExpanded]) is false, the glyph is out of composition (`AnimatedVisibility`'s content is
 * only composed while entering/visible/exiting) — so it is never clickable while hidden. [stats] sits OUTSIDE the
 * fade so T15 can render it at reduced alpha while typing instead of hiding it outright.
 */
@Composable
fun EditorChrome(
    visible: Boolean,
    widthClass: WidthClass,
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
 * Stands in for a library button: a thin grip on the start edge, vertically centred, hinting that a swipe toward
 * the end opens the library. Fades with the chrome (gone while typing). Draw-only — no pointer input, so every
 * drag/long-press/scroll in its band still reaches the `EditText`; `EditorSurface` detects a tap on it with
 * [observeTap] (for when swipes are off in Settings, and for the tablet pane). [onClick] is the TalkBack action.
 */
@Composable
fun LibraryHandle(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    val label = stringResource(R.string.cd_library)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(WriterMotion.CHROME_FADE_IN_MS, easing = WriterMotion.chromeFadeInEasing)),
        exit = fadeOut(tween(WriterMotion.CHROME_FADE_OUT_MS, easing = WriterMotion.chromeFadeOutEasing)),
    ) {
        Box(
            Modifier
                .testTag("libraryHandle")
                .size(width = WriterDimens.libraryHandleWidth, height = WriterDimens.libraryHandleHeight)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                    onClick {
                        onClick()
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(CircleShape)
                    .background(colors.textSecondary),
            )
        }
    }
}

/**
 * Observes a tap whose down lands where [hit] says (given this element's size); [PointerEventPass.Final] would see
 * the interop View's own consumption first, so [PointerEventPass.Initial] is used instead. Slop-bounded (a
 * drag/scroll starting in the zone does not count, and reaches the View untouched) and bounded by the platform
 * long-press timeout (a long-press-then-selection does not count either). With [consume] false the tap still
 * reaches the `EditText` underneath (harmless: it only moves the caret); with [consume] true the up is consumed, so
 * the View gets `ACTION_CANCEL` instead of a caret move.
 */
fun Modifier.observeTap(
    hit: (Offset, IntSize) -> Boolean,
    consume: Boolean = false,
    onTap: () -> Unit,
): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!hit(down.position, size)) return@awaitEachGesture
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
                        if (consume) change.consume()
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
