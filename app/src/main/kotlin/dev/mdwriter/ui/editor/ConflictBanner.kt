package dev.mdwriter.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mdwriter.ui.theme.LocalWriterColors
import dev.mdwriter.ui.theme.hairline

/**
 * Non-modal top banner (02 §… / T11 step 10): no scrim, no dialog, the editor stays editable underneath. Max
 * width is capped so it doesn't stretch full-bleed on medium/expanded screens (a future task may align this
 * exactly with the editor's measured text column).
 */
@Composable
fun ConflictBanner(
    conflict: ConflictState,
    onAction: (ConflictAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalWriterColors.current
    Surface(
        modifier =
            modifier
                .padding(16.dp)
                .widthIn(max = 640.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.surface,
        contentColor = colors.text,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(hairline(), colors.divider),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text =
                    when (conflict) {
                        is ConflictState.ChangedOnDisk -> "Changed on disk"
                        ConflictState.Gone -> "File was moved or deleted"
                    },
                fontSize = 14.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                when (conflict) {
                    is ConflictState.ChangedOnDisk -> {
                        TextButton(onClick = { onAction(ConflictAction.Reload) }) {
                            Text("Reload", color = colors.accent)
                        }
                        TextButton(onClick = { onAction(ConflictAction.KeepMine) }) {
                            Text("Keep mine", color = colors.accent)
                        }
                        TextButton(onClick = { onAction(ConflictAction.SaveBoth) }) {
                            Text("Save both", color = colors.accent)
                        }
                    }

                    ConflictState.Gone -> {
                        TextButton(onClick = { onAction(ConflictAction.SaveAsNew) }) {
                            Text("Save as new", color = colors.accent)
                        }
                        TextButton(onClick = { onAction(ConflictAction.Close) }) {
                            Text("Close", color = colors.accent)
                        }
                    }
                }
            }
        }
    }
}
