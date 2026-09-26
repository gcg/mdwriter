package dev.mdwriter.ui.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.mdwriter.editor.SelectionState
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterMotion
import dev.mdwriter.ui.theme.WriterTheme

/**
 * The selection pill (02 §6): an in-tree Compose overlay, never a `Popup`/`DropdownMenu` — those are separate
 * focusable windows that would steal focus from [dev.mdwriter.editor.MarkdownEditText] and close the IME (T09
 * Pitfalls). [modifier] is expected to be `Modifier.matchParentSize()` inside the same `Box` the editor's
 * `AndroidView` is hosted in, so [SelectionState.anchor] (EditorScrollView viewport coords) lines up exactly —
 * the pill's own size is deterministic (fixed 48 dp button width, no icon-driven wrapping), so the first frame is
 * placed correctly with no post-layout jump.
 */
@Composable
fun FormatToolbarOverlay(
    state: SelectionState,
    highlightEnabled: Boolean,
    readOnly: Boolean,
    canPaste: () -> Boolean,
    onAction: (ToolbarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDensity.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    var moreOpen by remember { mutableStateOf(false) }
    // Keeps the anchor/geometry stable through the 90 ms exit animation (state.anchor is NONE once hidden).
    val last = remember { arrayOf(state) }
    if (state.visible) last[0] = state
    val shown = last[0]
    LaunchedEffect(state.visible, state.start, state.end) { moreOpen = false }
    val slots =
        remember(box.width, highlightEnabled) {
            ToolbarSlots.compute(with(d) { box.width.toDp().value }, highlightEnabled)
        }
    val paste = remember(state.visible, state.start, state.end) { canPaste() }
    val btn = with(d) { WriterDimens.pillButton.roundToPx() }
    val hair = 1 // "1 px" divider/border
    val pillW = btn * slots.buttonCount + (if (slots.hasDivider) hair else 0)
    val pillH = with(d) { WriterDimens.pillHeight.roundToPx() }
    val pos =
        pillOffset(
            selTop = shown.anchor.top,
            selBottom = shown.anchor.bottom,
            selCx = shown.anchor.centerX(),
            pillW = pillW,
            pillH = pillH,
            boxW = box.width,
            boxH = box.height,
            gapAbove = with(d) { WriterDimens.pillGapAboveSelection.roundToPx() },
            gapBelow = with(d) { WriterDimens.pillGapBelowSelection.roundToPx() },
            margin = with(d) { WriterDimens.pillScreenPadding.roundToPx() },
        )
    Box(modifier.onSizeChanged { box = it }) {
        // no pointerInput: taps outside a button fall through to the editor
        AnimatedVisibility(
            visible = state.visible && box.width > 0,
            modifier = Modifier.offset { pos },
            enter =
                fadeIn(tween(WriterMotion.PILL_IN_MS, easing = WriterMotion.emphasizedDecelerate)) +
                    scaleIn(
                        tween(WriterMotion.PILL_IN_MS, easing = WriterMotion.emphasizedDecelerate),
                        initialScale = WriterMotion.PILL_IN_SCALE_FROM,
                    ) +
                    slideInVertically(tween(WriterMotion.PILL_IN_MS, easing = WriterMotion.emphasizedDecelerate)) {
                        with(d) { WriterMotion.pillInOffsetY.roundToPx() }
                    },
            exit = fadeOut(tween(WriterMotion.PILL_OUT_MS, easing = WriterMotion.emphasizedAccelerate)),
        ) {
            FormatToolbar(
                slots = slots,
                canPaste = paste,
                readOnly = readOnly,
                onItem = { item ->
                    if (item == ToolbarItem.More) {
                        moreOpen = !moreOpen
                    } else {
                        moreOpen = false
                        item.action?.let(onAction)
                    }
                },
            )
        }
        if (moreOpen && state.visible) {
            MoreMenu(
                slots = slots,
                canPaste = paste,
                readOnly = readOnly,
                pillPos = pos,
                pillW = pillW,
                pillH = pillH,
                box = box,
                onAction = {
                    moreOpen = false
                    onAction(it)
                },
            )
        }
    }
}

/**
 * Pure (JVM-tested, `PillPositionTest`). Above the selection if there is room; else below (+[gapBelow] for the
 * handles); else — the selection fills the viewport — pinned at the top with [gapAbove] clearance. `x` is
 * centred on the selection, clamped to [margin] at both edges (or centred in the box if it is narrower than
 * `pillW + 2 * margin`).
 */
internal fun pillOffset(
    selTop: Float,
    selBottom: Float,
    selCx: Float,
    pillW: Int,
    pillH: Int,
    boxW: Int,
    boxH: Int,
    gapAbove: Int,
    gapBelow: Int,
    margin: Int,
): IntOffset {
    val above = selTop.toInt() - gapAbove - pillH
    val below = selBottom.toInt() + gapBelow
    val y =
        when {
            above >= 0 -> above
            below + pillH <= boxH -> below
            else -> gapAbove
        }.coerceIn(0, (boxH - pillH).coerceAtLeast(0))
    val x =
        if (boxW - 2 * margin < pillW) {
            (boxW - pillW) / 2
        } else {
            (selCx.toInt() - pillW / 2).coerceIn(margin, boxW - margin - pillW)
        }
    return IntOffset(x, y)
}

/** Paste needs a clip AND write access; Cut/formatting need write access; Copy/More work read-only. */
internal fun enabledFor(
    item: ToolbarItem,
    canPaste: Boolean,
    readOnly: Boolean,
): Boolean =
    when (item) {
        ToolbarItem.Paste -> canPaste && !readOnly
        ToolbarItem.Copy, ToolbarItem.More -> true
        else -> !readOnly
    }

/**
 * The pill itself (02 §6): 48 dp tall, fully rounded, `surface`, 1 px `divider` border, no shadow. Formatting
 * buttons, an optional 1 px divider, clipboard buttons, then More (always last).
 */
@Composable
internal fun FormatToolbar(
    slots: ToolbarSlots.Layout,
    canPaste: Boolean,
    readOnly: Boolean,
    onItem: (ToolbarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Row(
        modifier
            .height(WriterDimens.pillHeight)
            .background(colors.surface, RoundedCornerShape(WriterDimens.pillCornerRadius))
            .border(1.dp, colors.divider, RoundedCornerShape(WriterDimens.pillCornerRadius)),
    ) {
        slots.formatting.forEach { item ->
            PillButton(item, enabled = enabledFor(item, canPaste, readOnly), onClick = { onItem(item) })
        }
        if (slots.hasDivider) {
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .width(1.dp)
                    .height(24.dp)
                    .background(colors.divider),
            )
        }
        slots.clipboard.forEach { item ->
            PillButton(item, enabled = enabledFor(item, canPaste, readOnly), onClick = { onItem(item) })
        }
        PillButton(ToolbarItem.More, enabled = true, onClick = { onItem(ToolbarItem.More) })
    }
}

@Composable
internal fun PillButton(
    item: ToolbarItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    val label = stringResource(item.label)
    val painter: Painter = painterResource(item.icon)
    Box(
        modifier
            .size(WriterDimens.pillButton)
            // BEFORE clickable: no focus target, so the EditText keeps focus and the IME stays up.
            .focusProperties { canFocus = false }
            .clickable(
                interactionSource = null,
                indication = ripple(bounded = false, radius = 20.dp),
                enabled = enabled,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter,
            contentDescription = null,
            tint = colors.text.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(WriterDimens.icon),
        )
    }
}
