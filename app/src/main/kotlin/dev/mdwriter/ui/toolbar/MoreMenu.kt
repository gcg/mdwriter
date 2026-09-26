package dev.mdwriter.ui.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme

private val MORE_MENU_GAP = 4.dp
private val MORE_ROW_HEIGHT = 48.dp
private val MORE_MENU_VERTICAL_PADDING = 8.dp

/**
 * The pill's "More" menu (02 §6): opens upward when there is room, `surface`, 12 dp radius, 1 px `divider`
 * border, no shadow — an in-tree overlay, never a `Popup`/`DropdownMenu` (same reasoning as
 * [FormatToolbarOverlay]: those steal focus and close the IME).
 */
@Composable
internal fun MoreMenu(
    slots: ToolbarSlots.Layout,
    canPaste: Boolean,
    readOnly: Boolean,
    pillPos: IntOffset,
    pillW: Int,
    pillH: Int,
    box: IntSize,
    onAction: (ToolbarAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = LocalDensity.current
    val rows = slots.overflow.size + slots.more.size
    val menuW = with(d) { WriterDimens.overflowMenuWidth.roundToPx() }
    val menuH =
        with(d) {
            rows * MORE_ROW_HEIGHT.roundToPx() + MORE_MENU_VERTICAL_PADDING.roundToPx() * 2
        }
    val pos =
        moreMenuOffset(
            pillPos = pillPos,
            pillW = pillW,
            pillH = pillH,
            menuW = menuW,
            menuH = menuH,
            boxW = box.width,
            boxH = box.height,
            gap = with(d) { MORE_MENU_GAP.roundToPx() },
            margin = with(d) { WriterDimens.pillScreenPadding.roundToPx() },
        )
    val colors = WriterTheme.colors
    Column(
        modifier
            .offset { pos }
            .width(WriterDimens.overflowMenuWidth)
            .heightIn(max = with(d) { (box.height.toDp() - 16.dp).coerceAtLeast(0.dp) })
            .background(colors.surface, RoundedCornerShape(WriterDimens.menuCornerRadius))
            .border(1.dp, colors.divider, RoundedCornerShape(WriterDimens.menuCornerRadius))
            // BEFORE verticalScroll: no focus target, so the EditText keeps focus and the IME stays up.
            .focusProperties { canFocus = false }
            .verticalScroll(rememberScrollState())
            .padding(vertical = MORE_MENU_VERTICAL_PADDING)
            .wrapContentHeight(),
    ) {
        slots.overflow.forEach { item ->
            val action = item.action ?: return@forEach
            MoreMenuRow(
                label = stringResource(item.label),
                enabled = enabledFor(item, canPaste, readOnly),
                onClick = { onAction(action) },
            )
        }
        if (slots.overflow.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(colors.divider),
            )
        }
        slots.more.forEach { entry ->
            MoreMenuRow(
                label = stringResource(entry.label),
                enabled = entry == MoreEntry.SelectAll || !readOnly,
                onClick = { onAction(entry.action) },
            )
        }
    }
}

@Composable
private fun MoreMenuRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = WriterTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MORE_ROW_HEIGHT)
            .focusProperties { canFocus = false } // BEFORE clickable
            .clickable(
                interactionSource = null,
                indication = ripple(),
                enabled = enabled,
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ).padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.text.copy(alpha = if (enabled) 1f else 0.38f),
        )
    }
}

/**
 * Pure (JVM-tested, `PillPositionTest`). Above the pill if it fits, else below, else pinned with [gap] clearance.
 * `x` is end-aligned with the pill's own right edge, clamped to [margin] (or centred if the box is too narrow).
 * [menuH] is a deterministic estimate (`rows * 48 dp + 16 dp`) used only for placement — the real `Column` clips
 * to `heightIn(max = boxH - 16 dp)` and scrolls if the actual content is taller.
 */
internal fun moreMenuOffset(
    pillPos: IntOffset,
    pillW: Int,
    pillH: Int,
    menuW: Int,
    menuH: Int,
    boxW: Int,
    boxH: Int,
    gap: Int,
    margin: Int,
): IntOffset {
    val x =
        if (boxW - 2 * margin < menuW) {
            (boxW - menuW) / 2
        } else {
            (pillPos.x + pillW - menuW).coerceIn(margin, boxW - margin - menuW)
        }
    val above = pillPos.y - gap - menuH
    val below = pillPos.y + pillH + gap
    val y =
        when {
            above >= 0 -> above
            below + menuH <= boxH -> below
            else -> gap
        }.coerceIn(0, (boxH - menuH).coerceAtLeast(0))
    return IntOffset(x, y)
}
