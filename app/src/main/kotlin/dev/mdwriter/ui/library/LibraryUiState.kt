package dev.mdwriter.ui.library

import dev.mdwriter.data.library.DocKey
import dev.mdwriter.data.library.EntryCaps
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryEntry
import dev.mdwriter.data.library.LocationId
import dev.mdwriter.data.library.LocationState
import dev.mdwriter.data.settings.SortOrder

/** One crumb of the breadcrumb; [name] is `""` for a location root — the UI substitutes the localized label
 * ("On this device"). */
data class Crumb(
    val folder: FolderRef,
    val name: String,
)

/** One row in the "Locations" section. [name] is `""` for [LocationId.Internal] (the UI substitutes "On this
 * device"); [state] is always [LocationState.Ready] for Internal. */
data class LocationItem(
    val id: LocationId,
    val folder: FolderRef,
    val name: String = "",
    val state: LocationState = LocationState.Ready,
)

/** A file row, ready to render: [entry] backs every action (rename/duplicate/move/delete) and its `lastModified`
 * is formatted at the call site via [RelativeDate] (needs `DateFormat.is24HourFormat(context)` and a
 * `stringResource`, neither available in the ViewModel); [title] already strips the extension unless
 * `showExtensions` is on; [excerpt] is the precomputed or lazily-fetched excerpt; [isOpen] drives the accent bar +
 * bold title. */
data class FileItem(
    val entry: LibraryEntry,
    val title: String,
    val excerpt: String?,
    val isOpen: Boolean,
    /** Folder path relative to the location root ("Drafts"); only set for search results. */
    val folderPath: String? = null,
)

data class PendingDelete(
    val entry: LibraryEntry,
    val id: Long,
)

/** One-shot events from [LibraryViewModel] to the UI. */
sealed interface LibraryEvent {
    data object CloseDrawer : LibraryEvent

    data class Message(
        val text: String,
    ) : LibraryEvent
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Content(
        val locations: List<LocationItem>,
        val crumbs: List<Crumb>,
        val sortOrder: SortOrder,
        val folders: List<LibraryEntry>,
        val files: List<FileItem>,
        val searching: Boolean,
        val query: String,
        val openDocKey: DocKey?,
        /** Whether the drawer is showing a location's root (T13's folder-up back handler, 01 §6.4 / §H). */
        val atRoot: Boolean,
        /** Caps of the CURRENT folder itself (T14): gates the new-note glyph / "New folder…" for a read-only
         * linked folder. Always [EntryCaps.ALL] under "On this device". */
        val currentFolderCaps: EntryCaps = EntryCaps.ALL,
    ) : LibraryUiState
}
