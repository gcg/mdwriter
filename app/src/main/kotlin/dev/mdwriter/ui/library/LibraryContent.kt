package dev.mdwriter.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.R
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.settings.SortOrder
import dev.mdwriter.ui.theme.WriterDimens
import dev.mdwriter.ui.theme.WriterTheme
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Wires [LibraryViewModel] to the stateless [LibraryContent] (only this composable touches the ViewModel, per the
 * task's own step 6). Hosts the "Move…" dialog (needs an async [LibraryViewModel.folderTree] fetch) and drives
 * `now` (relative-date reference instant), refreshed every minute while the drawer is composed.
 */
@Composable
fun LibraryDrawer(
    vm: LibraryViewModel,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }
    var movingEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var moveTree by remember { mutableStateOf<List<dev.mdwriter.data.library.FolderNode>>(emptyList()) }
    var creatingFolder by remember { mutableStateOf(false) }

    LaunchedEffect(movingEntry) {
        val entry = movingEntry
        if (entry != null) moveTree = vm.folderTree()
    }

    LibraryContent(
        state = state,
        now = now,
        modifier = modifier,
        onSearchToggle = {
            if ((state as? LibraryUiState.Content)?.searching ==
                true
            ) {
                vm.closeSearch()
            } else {
                vm.startSearch()
            }
        },
        onQueryChange = vm::setQuery,
        onNewNote = vm::newNote,
        onSortChange = vm::setSort,
        onNewFolder = { creatingFolder = true },
        onCrumbClick = vm::goTo,
        onLocationClick = { vm.goTo(0) },
        onOpen = vm::open,
        onOpenFolder = vm::openFolder,
        onRename = vm::rename,
        onDuplicate = vm::duplicate,
        onMove = { entry -> movingEntry = entry },
        onDelete = vm::delete,
    )

    val entryToMove = movingEntry
    if (entryToMove != null) {
        val currentFolder = (state as? LibraryUiState.Content)?.crumbs?.lastOrNull()?.folder
        MoveDialog(
            tree = moveTree,
            currentFolder = currentFolder,
            onSelect = { target ->
                vm.move(entryToMove, target)
                movingEntry = null
            },
            onDismiss = { movingEntry = null },
        )
    }
    if (creatingFolder) {
        NameDialog(
            title = stringResource(R.string.library_new_folder_title),
            confirmLabel = stringResource(R.string.library_new_folder_title),
            initialValue = "",
            currentName = "",
            siblingsLower = ((state as? LibraryUiState.Content)?.folders.orEmpty().map { it.name.lowercase() }).toSet(),
            ext = null,
            onConfirm = {
                vm.createFolder(it)
                creatingFolder = false
            },
            onDismiss = { creatingFolder = false },
        )
    }
}

/** Stateless drawer content (02 §7). Every parameter is state + a lambda — no ViewModel reference. */
@Composable
fun LibraryContent(
    state: LibraryUiState,
    now: Instant,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNewNote: () -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onNewFolder: () -> Unit,
    onCrumbClick: (Int) -> Unit,
    onLocationClick: () -> Unit,
    onOpen: (LibraryEntry) -> Unit,
    onOpenFolder: (LibraryEntry) -> Unit,
    onRename: (LibraryEntry, String) -> Unit,
    onDuplicate: (LibraryEntry) -> Unit,
    onMove: (LibraryEntry) -> Unit,
    onDelete: (LibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Column(modifier.fillMaxSize().background(colors.surface)) {
        val content = state as? LibraryUiState.Content
        DrawerHeader(
            searching = content?.searching == true,
            query = content?.query.orEmpty(),
            onSearchToggle = onSearchToggle,
            onQueryChange = onQueryChange,
            onNewNote = onNewNote,
        )
        if (content == null) return@Column
        if (!content.searching) {
            Text(
                stringResource(R.string.library_locations),
                color = colors.textSecondary,
                style = WriterTheme.typography.rowExcerpt,
                modifier = Modifier.padding(start = WriterDimens.rowPaddingHorizontal, top = 8.dp, bottom = 4.dp),
            )
            LocationRow(
                selected =
                    content.crumbs
                        .firstOrNull()
                        ?.folder
                        ?.location == LocationId.Internal,
                onClick = onLocationClick,
            )
            HairlineDivider()
            BreadcrumbRow(
                crumbs = content.crumbs,
                sortOrder = content.sortOrder,
                onCrumbClick = onCrumbClick,
                onSortChange = onSortChange,
                onNewFolder = onNewFolder,
            )
        }
        if (content.folders.isEmpty() && content.files.isEmpty()) {
            EmptyLibrary(onNewNote = onNewNote, modifier = Modifier.fillMaxWidth().padding(top = 48.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (!content.searching) {
                    items(content.folders, key = { "f:" + (it.folder?.id ?: it.name) }) { entry ->
                        FolderRow(entry = entry, onClick = { onOpenFolder(entry) })
                    }
                }
                items(content.files, key = { it.entry.doc?.let { d -> d.hashCode() } ?: it.entry.name }) { item ->
                    FileRow(
                        item = item,
                        now = now,
                        onClick = { onOpen(item.entry) },
                        onRename = { newBase -> onRename(item.entry, newBase) },
                        onDuplicate = { onDuplicate(item.entry) },
                        onMove = { onMove(item.entry) },
                        onDelete = { onDelete(item.entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(
    searching: Boolean,
    query: String,
    onSearchToggle: () -> Unit,
    onQueryChange: (String) -> Unit,
    onNewNote: () -> Unit,
) {
    val colors = WriterTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(WriterDimens.drawerHeaderHeight)
            .padding(horizontal = WriterDimens.rowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searching) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = WriterTheme.typography.rowTitle.copy(color = colors.text),
                    cursorBrush =
                        androidx.compose.ui.graphics
                            .SolidColor(colors.accent),
                )
            }
            IconButtonPlain(
                iconRes = R.drawable.ic_close,
                contentDescription = stringResource(R.string.library_close_search),
                tint = colors.textSecondary,
                onClick = onSearchToggle,
            )
        } else {
            Text(
                stringResource(R.string.library_title),
                color = colors.text,
                style = WriterTheme.typography.drawerTitle,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButtonPlain(
                iconRes = R.drawable.ic_search,
                contentDescription = stringResource(R.string.library_search),
                tint = colors.textSecondary,
                onClick = onSearchToggle,
            )
            IconButtonPlain(
                iconRes = R.drawable.ic_edit_square,
                contentDescription = stringResource(R.string.library_new_note),
                tint = colors.accent,
                onClick = onNewNote,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun IconButtonPlain(
    iconRes: Int,
    contentDescription: String?,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(WriterDimens.touchTarget)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(WriterDimens.icon),
        )
    }
}

@Composable
private fun HairlineDivider() {
    val colors = WriterTheme.colors
    val hair = with(LocalDensity.current) { 1.toDp() }
    Box(Modifier.fillMaxWidth().height(hair).background(colors.divider))
}

@Composable
private fun BreadcrumbRow(
    crumbs: List<Crumb>,
    sortOrder: SortOrder,
    onCrumbClick: (Int) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onNewFolder: () -> Unit,
) {
    val colors = WriterTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = WriterDimens.rowPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val onThisDevice = stringResource(R.string.library_on_this_device)
        Row(Modifier.weight(1f)) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text(" › ", color = colors.textSecondary, style = WriterTheme.typography.rowExcerpt)
                }
                Text(
                    crumb.name.ifEmpty { onThisDevice },
                    color = if (index == crumbs.lastIndex) colors.text else colors.textSecondary,
                    style = WriterTheme.typography.rowExcerpt,
                    modifier = Modifier.clickable { onCrumbClick(index) },
                )
            }
        }
        Box {
            IconButtonPlain(
                iconRes = R.drawable.ic_sort,
                contentDescription = stringResource(R.string.library_sort),
                tint = colors.textSecondary,
                onClick = { menuOpen = true },
            )
            FolderMenu(
                expanded = menuOpen,
                sortOrder = sortOrder,
                onDismiss = { menuOpen = false },
                onSortChange = {
                    onSortChange(it)
                    menuOpen = false
                },
                onNewFolder = {
                    menuOpen = false
                    onNewFolder()
                },
            )
        }
    }
}

/** The breadcrumb's `ic_sort` button (02 §7): sort group + "New folder…" — the only entry point for it. */
@Composable
private fun FolderMenu(
    expanded: Boolean,
    sortOrder: SortOrder,
    onDismiss: () -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onNewFolder: () -> Unit,
) {
    val colors = WriterTheme.colors
    val byName = sortOrder == SortOrder.NameAToZ || sortOrder == SortOrder.NameZToA
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = colors.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        SortMenuItem(stringResource(R.string.library_sort_date_modified), !byName, colors) {
            onSortChange(
                if (sortOrder ==
                    SortOrder.ModifiedOldestFirst
                ) {
                    SortOrder.ModifiedOldestFirst
                } else {
                    SortOrder.ModifiedNewestFirst
                },
            )
        }
        SortMenuItem(stringResource(R.string.library_sort_name), byName, colors) {
            onSortChange(if (sortOrder == SortOrder.NameZToA) SortOrder.NameZToA else SortOrder.NameAToZ)
        }
        HairlineDivider()
        if (!byName) {
            SortMenuItem(
                stringResource(R.string.library_sort_newest_first),
                sortOrder == SortOrder.ModifiedNewestFirst,
                colors,
            ) {
                onSortChange(SortOrder.ModifiedNewestFirst)
            }
            SortMenuItem(
                stringResource(R.string.library_sort_oldest_first),
                sortOrder == SortOrder.ModifiedOldestFirst,
                colors,
            ) {
                onSortChange(SortOrder.ModifiedOldestFirst)
            }
        } else {
            SortMenuItem(stringResource(R.string.library_sort_a_to_z), sortOrder == SortOrder.NameAToZ, colors) {
                onSortChange(SortOrder.NameAToZ)
            }
            SortMenuItem(stringResource(R.string.library_sort_z_to_a), sortOrder == SortOrder.NameZToA, colors) {
                onSortChange(SortOrder.NameZToA)
            }
        }
        HairlineDivider()
        DropdownMenuItem(
            text = { Text(stringResource(R.string.library_new_folder), color = colors.text) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_create_new_folder), null, tint = colors.text) },
            onClick = onNewFolder,
        )
    }
}

@Composable
private fun SortMenuItem(
    label: String,
    checked: Boolean,
    colors: dev.mdwriter.ui.theme.WriterColors,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = colors.text) },
        leadingIcon = {
            if (checked) {
                Icon(painterResource(R.drawable.ic_check), null, tint = colors.text)
            } else {
                Box(Modifier.size(24.dp))
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun EmptyLibrary(
    onNewNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WriterTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(R.string.library_no_notes),
            color = colors.textSecondary,
            style = WriterTheme.typography.rowTitle,
        )
        TextButton(onClick = onNewNote, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_square),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.library_new_note),
                color = colors.text,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
