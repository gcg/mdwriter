package dev.mdwriter.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.mdwriter.R
import dev.mdwriter.data.library.FolderNode
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.markdown.DocTitle
import dev.mdwriter.ui.theme.WriterTheme

/** Result of validating a proposed rename/new-name/new-folder-name; pure, easy to unit test. */
sealed interface NameCheck {
    data class Ok(
        val sanitizedBase: String,
    ) : NameCheck

    data object Empty : NameCheck

    data object Exists : NameCheck

    data object Unchanged : NameCheck
}

/**
 * [input] is the raw text the user typed (no extension). [currentName] is the base name being renamed FROM (used
 * to detect a no-op rename); pass `""` for a brand-new name (new folder / duplicate) where "unchanged" can't apply.
 * [siblingsLower] holds lower-cased sibling display names (incl. extension) already in the target folder, MINUS
 * the entry's own current name. [ext] is appended before the existence check; pass `null` for folder names.
 */
fun validateName(
    input: String,
    currentName: String,
    siblingsLower: Set<String>,
    ext: String?,
): NameCheck {
    if (input.isBlank()) return NameCheck.Empty
    val sanitized = DocTitle.sanitizeFileName(input)
    if (sanitized.isBlank()) return NameCheck.Empty
    if (currentName.isNotEmpty() && sanitized == currentName) return NameCheck.Unchanged
    val candidate = (if (ext != null) "$sanitized.$ext" else sanitized).lowercase()
    if (candidate in siblingsLower) return NameCheck.Exists
    return NameCheck.Ok(sanitized)
}

/** Rename / new-note / new-folder name prompt (02 §7: `AlertDialog`, `surface`, 12 dp radius, `TextButton`s in
 * `text` colour — never `accent` text). */
@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initialValue: String,
    currentName: String,
    siblingsLower: Set<String>,
    ext: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialValue) }
    val check = validateName(text, currentName, siblingsLower, ext)
    val colors = WriterTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
        title = { Text(title, color = colors.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = check is NameCheck.Exists,
                    label = { Text(stringResource(R.string.library_name_hint)) },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.text,
                            unfocusedTextColor = colors.text,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.divider,
                            cursorColor = colors.accent,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (check is NameCheck.Exists) {
                    Text(
                        stringResource(R.string.library_name_exists),
                        color = colors.danger,
                        style = WriterTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                } else if (check is NameCheck.Empty) {
                    Text(
                        stringResource(R.string.library_name_empty),
                        color = colors.textSecondary,
                        style = WriterTheme.typography.caption,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (check as? NameCheck.Ok)?.let { onConfirm(it.sanitizedBase) } },
                enabled = check is NameCheck.Ok,
            ) {
                Text(confirmLabel, color = colors.text)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel), color = colors.text)
            }
        },
    )
}

/** "Move…" folder picker: the whole [tree] (root first, depth-first), the entry's current folder disabled. */
@Composable
fun MoveDialog(
    tree: List<FolderNode>,
    currentFolder: FolderRef?,
    onSelect: (FolderRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WriterTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(12.dp),
        title = { Text(stringResource(R.string.library_move_title), color = colors.text) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(tree) { node ->
                    val enabled = node.folder != currentFolder
                    val label = node.name.ifEmpty { stringResource(R.string.library_on_this_device) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onSelect(node.folder) }
                            .padding(
                                start = (20 + node.depth * 20).dp,
                                end = 20.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_folder),
                                    contentDescription = null,
                                    tint = if (enabled) colors.textSecondary else colors.divider,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    label,
                                    color = if (enabled) colors.text else colors.textSecondary,
                                    style = WriterTheme.typography.rowTitle,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_cancel), color = colors.text)
            }
        },
    )
}
