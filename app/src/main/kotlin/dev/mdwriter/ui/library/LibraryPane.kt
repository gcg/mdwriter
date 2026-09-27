package dev.mdwriter.ui.library

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.mdwriter.data.export.ExportAllNotes
import dev.mdwriter.ui.editor.DocumentSession

/**
 * The permanent library pane shown on windows >= 840 dp (02 §7, T13): the exact same content as the modal
 * [LibraryDrawer] (search, breadcrumb, folder/file rows, file operations), just laid out in a fixed-width column
 * next to the editor instead of a `ModalNavigationDrawer` sheet — [LibraryDrawer] already takes a plain `modifier`
 * with no sheet-specific behaviour baked in, so this is a thin, width-only wrapper (never widen/duplicate the
 * drawer's own logic here).
 */
@Suppress("ktlint:compose:vm-forwarding-check")
@Composable
fun LibraryPane(
    vm: LibraryViewModel,
    exporter: ExportAllNotes,
    session: DocumentSession,
    modifier: Modifier = Modifier,
) {
    LibraryDrawer(vm, exporter, session, modifier.fillMaxHeight())
}
