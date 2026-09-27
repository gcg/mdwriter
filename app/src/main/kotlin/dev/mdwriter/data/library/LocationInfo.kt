package dev.mdwriter.data.library

/** Whether a linked [LocationId.Tree] currently has a live grant + a readable root document (T14). Never
 * auto-unlinked when it goes [Disconnected] — the grant might come back (platform §2.13: grants don't survive a
 * backup/restore), so only an explicit "Stop using this folder" removes it. */
enum class LocationState { Ready, Disconnected }

/** One row of the drawer's "Locations" section (02 §7). [name] is `""` for [LocationId.Internal] (the UI
 * substitutes the localized "On this device" label, same convention as [Crumb.name][dev.mdwriter.ui.library.Crumb]
 * in the T12 breadcrumb); for a [LocationId.Tree] it is the linked folder's own display name. */
data class LocationInfo(
    val id: LocationId,
    val name: String,
    val state: LocationState,
)
