package dev.mdwriter.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.mdwriter.R
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Pressed row background = `surfaceHover` (02 §7); a plain ripple-backed clickable, no extra decoration. */
@Composable
fun Modifier.clickableRow(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
}

/** 02 §7 "Locations": `phone_android` 20 dp + label, bold when selected. Only Internal until T14. */
@Composable
fun LocationRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(WriterDimens.locationRowHeight)
            .clickableRow(onClick)
            .padding(horizontal = WriterDimens.rowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_phone_android),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WriterDimens.folderIcon),
        )
        Text(
            stringResource(R.string.library_on_this_device),
            color = colors.text,
            style = WriterTheme.typography.rowTitle,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** 02 §7 "Locations" row for a linked folder (T14): `folder`/`folder_open` icon + name, bold when selected. Ready
 * rows are a plain tap target with a long-press "Stop using this folder" menu; a Disconnected row shows the name
 * in `textSecondary` with a second "Disconnected" line and a trailing "Reconnect" `TextButton`. */
@Composable
fun TreeLocationRow(
    name: String,
    selected: Boolean,
    disconnected: Boolean,
    onClick: () -> Unit,
    onStopUsing: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = WriterDimens.locationRowHeight)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                ).padding(horizontal = WriterDimens.rowPaddingHorizontal, vertical = if (disconnected) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (disconnected) R.drawable.ic_folder_open else R.drawable.ic_folder),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(WriterDimens.folderIcon),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    name,
                    color = if (disconnected) colors.textSecondary else colors.text,
                    style = WriterTheme.typography.rowTitle,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
                if (disconnected) {
                    Text(
                        stringResource(R.string.library_disconnected),
                        color = colors.textSecondary,
                        style = WriterTheme.typography.caption,
                    )
                }
            }
            if (disconnected) {
                TextButton(onClick = onReconnect) {
                    Text(stringResource(R.string.library_reconnect), color = colors.accent)
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = colors.surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_stop_using_folder), color = colors.text) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_folder), null, tint = colors.text) },
                onClick = {
                    menuOpen = false
                    onStopUsing()
                },
            )
        }
    }
}

/** 02 §7 "Locations" bottom row: `create_new_folder` + "Use a folder…", `textSecondary`. */
@Composable
fun UseAFolderRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(WriterDimens.locationRowHeight)
            .clickableRow(onClick)
            .padding(horizontal = WriterDimens.rowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_create_new_folder),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WriterDimens.folderIcon),
        )
        Text(
            stringResource(R.string.library_use_a_folder),
            color = colors.textSecondary,
            style = WriterTheme.typography.rowTitle,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** 02 §7 folder row: `folder` 20 dp + name 16 sp, 48 dp tall. */
@Composable
fun FolderRow(
    entry: LibraryEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(WriterDimens.folderRowHeight)
            .clickableRow(onClick)
            .padding(horizontal = WriterDimens.rowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(WriterDimens.folderIcon),
        )
        Text(
            entry.name,
            color = colors.text,
            style = WriterTheme.typography.rowTitle,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * 02 §7 file row (72 dp): title bold + 3 dp accent bar when it's the open document, relative date end-aligned,
 * excerpt below. Long-press opens [RowMenu] (rename/duplicate/move/delete).
 */
@Composable
fun FileRow(
    item: FileItem,
    now: Instant,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    rowMenuExtras: @Composable (onClose: () -> Unit) -> Unit = {},
) {
    val colors = WriterTheme.colors
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val use24h =
        android.text.format.DateFormat
            .is24HourFormat(context)
    // LocalConfiguration (not Locale.getDefault()) so this recomposes on a locale change (lint NonObservableLocale).
    val locale: Locale =
        androidx.compose.ui.platform.LocalConfiguration.current.locales
            .get(0)
    val dateText =
        RelativeDate.format(
            item.entry.lastModified,
            now,
            ZoneId.systemDefault(),
            locale,
            use24h,
            stringResource(R.string.library_yesterday),
        )
    Box(
        modifier
            .fillMaxWidth()
            .height(WriterDimens.fileRowHeight)
            .testTag(if (item.isOpen) "activeFileBar" else "fileRow")
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuOpen = true
                },
            ),
    ) {
        if (item.isOpen) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(WriterDimens.activeFileBarWidth)
                    .background(colors.accent),
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = WriterDimens.rowPaddingHorizontal,
                    end = WriterDimens.rowPaddingHorizontal,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    item.title,
                    color = colors.text,
                    style = WriterTheme.typography.rowTitle,
                    fontWeight = if (item.isOpen) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    dateText,
                    color = colors.textSecondary,
                    style = WriterTheme.typography.caption,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            val excerptLine = item.folderPath?.let { "$it — ${item.excerpt.orEmpty()}" } ?: item.excerpt
            if (!excerptLine.isNullOrEmpty()) {
                Text(
                    excerptLine,
                    color = colors.textSecondary,
                    style = WriterTheme.typography.rowExcerpt,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        RowMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onRename = {
                menuOpen = false
                renaming = true
            },
            onDuplicate = {
                menuOpen = false
                onDuplicate()
            },
            onMove = {
                menuOpen = false
                onMove()
            },
            onDelete = {
                menuOpen = false
                onDelete()
            },
            rowMenuExtras = rowMenuExtras,
        )
    }
    if (renaming) {
        val base = NoteFiles.baseName(item.entry.name)
        val ext = NoteFiles.extensionOf(item.entry.name).ifEmpty { null }
        NameDialog(
            title = stringResource(R.string.library_rename_title),
            confirmLabel = stringResource(R.string.library_rename),
            initialValue = base,
            currentName = base,
            siblingsLower = emptySet(), // the store enforces real uniqueness; this is only an immediate hint
            ext = ext,
            onConfirm = {
                renaming = false
                onRename(it)
            },
            onDismiss = { renaming = false },
        )
    }
}

/** Long-press row menu (02 §7): Rename · Duplicate · Move… · [rowMenuExtras — T18 inserts Share here] · Delete. */
@Composable
fun RowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    rowMenuExtras: @Composable (onClose: () -> Unit) -> Unit = {},
) {
    val colors = WriterTheme.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_rename), color = colors.text) },
            leadingIcon = {
                Icon(painterResource(R.drawable.ic_drive_file_rename_outline), null, tint = colors.text)
            },
            onClick = onRename,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_duplicate), color = colors.text) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), null, tint = colors.text) },
            onClick = onDuplicate,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_move), color = colors.text) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_drive_folder_upload), null, tint = colors.text) },
            onClick = onMove,
        )
        rowMenuExtras(onDismiss)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_delete), color = colors.danger) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null, tint = colors.danger) },
            onClick = onDelete,
        )
    }
}
