package dev.mdwriter.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mdwriter.R
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme

/** A single-selection choice row (Focus: Off / Sentence / Paragraph, T15). */
data class OverflowChoice(
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

/** A boolean toggle row (Typewriter scrolling / Word count, T15). */
data class OverflowToggle(
    val checked: Boolean,
    val onChange: (Boolean) -> Unit,
)

/**
 * Everything the overflow menu can show (02 §9, T13). Fields owned by later tasks default to `null`/no-op so a
 * `null` field simply hides its row — later tasks only fill in their own field here, never redeclare this class.
 */
data class OverflowActions(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onFind: (() -> Unit)? = null, // T17 (search icon in the icon row)
    val onShare: (() -> Unit)? = null, // T18 (share icon in the icon row)
    val onNewNote: () -> Unit = {},
    val onPreview: (() -> Unit)? = null, // T16
    val focus: OverflowChoice? = null, // T15: Off / Sentence / Paragraph, inline radio sub-rows under "Focus ▸"
    val typewriter: OverflowToggle? = null, // T15
    val wordCount: OverflowToggle? = null, // T15
    val onSettings: (() -> Unit)? = null, // T19
)

/**
 * The editor's only menu (02 §9), anchored under the top-end glyph. Every item dismisses the menu before running
 * its action (step 6).
 */
@Composable
fun OverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: OverflowActions,
) {
    val colors = WriterTheme.colors
    val hairline = with(LocalDensity.current) { 1f / density }.dp

    fun act(block: () -> Unit): () -> Unit =
        {
            onDismiss()
            block()
        }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(WriterDimens.menuCornerRadius),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(hairline, colors.divider),
        modifier = Modifier.width(WriterDimens.overflowMenuWidth),
    ) {
        Row {
            IconButton(onClick = act(actions.onUndo), enabled = actions.canUndo) {
                Icon(
                    painterResource(R.drawable.ic_undo),
                    contentDescription = stringResource(R.string.tb_undo),
                    tint = if (actions.canUndo) colors.text else colors.textSecondary,
                )
            }
            IconButton(onClick = act(actions.onRedo), enabled = actions.canRedo) {
                Icon(
                    painterResource(R.drawable.ic_redo),
                    contentDescription = stringResource(R.string.tb_redo),
                    tint = if (actions.canRedo) colors.text else colors.textSecondary,
                )
            }
            actions.onFind?.let { onFind ->
                IconButton(onClick = act(onFind)) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.overflow_find),
                        tint = colors.text,
                    )
                }
            }
            actions.onShare?.let { onShare ->
                IconButton(onClick = act(onShare)) {
                    Icon(
                        painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.overflow_share),
                        tint = colors.text,
                    )
                }
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_new_note), color = colors.text) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit_square), null, tint = colors.text) },
            onClick = act(actions.onNewNote),
        )
        actions.onPreview?.let { onPreview ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overflow_preview), color = colors.text) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_preview), null, tint = colors.text) },
                onClick = act(onPreview),
            )
        }
        actions.focus?.let { focus ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overflow_focus), color = colors.text) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_center_focus_strong), null, tint = colors.text) },
                onClick = {},
                enabled = false,
            )
            focus.options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label, color = colors.text) },
                    leadingIcon = {
                        RadioButton(
                            selected = index == focus.selected,
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = colors.accent,
                                    unselectedColor = colors.textSecondary,
                                ),
                        )
                    },
                    onClick = act { focus.onSelect(index) },
                )
            }
        }
        actions.typewriter?.let { tw ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overflow_typewriter), color = colors.text) },
                trailingIcon = {
                    Switch(
                        checked = tw.checked,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
                    )
                },
                onClick = act { tw.onChange(!tw.checked) },
            )
        }
        actions.wordCount?.let { wc ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overflow_word_count), color = colors.text) },
                trailingIcon = {
                    Switch(
                        checked = wc.checked,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.accent),
                    )
                },
                onClick = act { wc.onChange(!wc.checked) },
            )
        }
        actions.onSettings?.let { onSettings ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.overflow_settings), color = colors.text) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_tune), null, tint = colors.text) },
                onClick = act(onSettings),
            )
        }
    }
}
