package dev.mdwriter.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mdwriter.R
import dev.mdwriter.data.storage.NoteFiles
import dev.mdwriter.ui.theme.WriterTheme
import dev.mdwriter.ui.theme.hairline

/**
 * Undoable-delete snackbar (02 §7: "Deleted 'X' · Undo", 5 s) + [LibraryEvent.Message]s, both state-driven from
 * [LibraryViewModel]: the 5 s undo timer lives in the ViewModel (testable); this composable only mirrors it and
 * relays the result back. [hostState] is owned by the caller (`MdWriterRoot`) and fed [LibraryEvent.Message]s from
 * its own single collector of [LibraryViewModel.events] — a `Channel`-backed flow only ever has ONE effective
 * subscriber, so a second, independent `vm.events.collect{}` here would race `MdWriterRoot`'s own collector for
 * `LibraryEvent.CloseDrawer` and silently drop some events (found via on-device testing: the drawer sometimes
 * never closed because `CloseDrawer` was consumed by this composable's own collector instead). Flat style:
 * `surface` container, `text` content/action, 12 dp shape, hairline border, no elevation.
 */
@Composable
fun DeleteUndoSnackbarHost(
    vm: LibraryViewModel,
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val pending by vm.pendingDelete.collectAsStateWithLifecycle()
    val currentVm by rememberUpdatedState(vm)
    val deletedTemplate = stringResource(R.string.library_deleted_undo)
    val undoLabel = stringResource(R.string.library_undo)

    LaunchedEffect(pending?.id) {
        val p = pending
        if (p != null) {
            val title = NoteFiles.baseName(p.entry.name)
            val result =
                hostState.showSnackbar(
                    message = deletedTemplate.format(title),
                    actionLabel = undoLabel,
                    withDismissAction = false,
                    duration = SnackbarDuration.Indefinite,
                )
            if (result == SnackbarResult.ActionPerformed) currentVm.undoDelete()
        } else {
            hostState.currentSnackbarData?.dismiss()
        }
    }

    val colors = WriterTheme.colors
    val hair = hairline()
    SnackbarHost(hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = colors.surface,
            contentColor = colors.text,
            actionColor = colors.text,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.border(hair, colors.divider, RoundedCornerShape(12.dp)),
        )
    }
}
