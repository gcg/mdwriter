package dev.mdwriter.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mdwriter.R
import dev.mdwriter.ui.theme.EditorMetrics
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterFont
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.fontFamily
import kotlin.math.roundToInt

private val RowMinHeight = 56.dp

@Composable
fun SettingsGroupHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = WriterTheme.colors.textSecondary,
        fontSize = 13.sp,
        modifier = modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = colors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = colors.accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = colors.surfaceHover,
                    uncheckedThumbColor = colors.textSecondary,
                    uncheckedBorderColor = colors.divider,
                ),
        )
    }
}

/** A labelled single-choice row. [fontFamilies] (parallel to [options]) draws each label in its own face. */
@Composable
fun <T> SegmentedRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    fontFamilyOf: (T) -> FontFamily? = { null },
) {
    val colors = WriterTheme.colors
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = colors.text, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    icon = {},
                    colors =
                        SegmentedButtonDefaults.colors(
                            activeContainerColor = colors.surfaceHover,
                            activeContentColor = colors.text,
                            inactiveContainerColor = colors.surface,
                            inactiveContentColor = colors.textSecondary,
                            activeBorderColor = colors.divider,
                            inactiveBorderColor = colors.divider,
                        ),
                ) {
                    Text(labelOf(option), fontFamily = fontFamilyOf(option))
                }
            }
        }
    }
}

/**
 * 6-stop slider with a live sample line. The step is persisted ONLY when the drag ends ([onCommit]): every commit
 * is one full reflow of the editor.
 */
@Composable
fun TextSizeRow(
    step: Int,
    font: WriterFont,
    widthClass: WidthClass,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    var pending by remember(step) { mutableFloatStateOf(step.toFloat()) }
    val shown = pending.roundToInt().coerceIn(0, 5)
    val labels = listOf("XS", "S", "M", "L", "XL", "XXL")
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_text_size), color = colors.text, fontSize = 16.sp)
            Text(labels[shown], color = colors.textSecondary, fontSize = 13.sp)
        }
        Slider(
            value = pending,
            onValueChange = { pending = it },
            onValueChangeFinished = { onCommit(pending.roundToInt().coerceIn(0, 5)) },
            valueRange = 0f..5f,
            steps = 4,
            colors =
                SliderDefaults.colors(
                    thumbColor = colors.accent,
                    activeTrackColor = colors.accent,
                    inactiveTrackColor = colors.divider,
                    activeTickColor = colors.bg,
                    inactiveTickColor = colors.textSecondary,
                ),
        )
        Text(
            stringResource(R.string.settings_text_size_sample),
            color = colors.text,
            fontFamily = font.fontFamily,
            fontSize = EditorMetrics.bodyTextSizeSp(shown, widthClass).sp,
        )
    }
}

@Composable
fun ActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = RowMinHeight).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = WriterTheme.colors.text, fontSize = 16.sp)
    }
}
