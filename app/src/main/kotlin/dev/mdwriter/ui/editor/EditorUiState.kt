package dev.mdwriter.ui.editor

import android.content.Intent
import dev.mdwriter.data.document.SaveState
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.editor.InstallRequest
import dev.mdwriter.markdown.Stats

/** 01 §6.4. `settingsOpen`/`RootSheet` stay minimal here (T19 wires the real sheet); `stats` is always `null`
 * until T15. */
data class EditorUiState(
    val doc: DocRef?,
    val title: String,
    val loading: Boolean,
    val readOnly: Boolean,
    val save: SaveState,
    val conflict: ConflictState?,
    val stats: Stats?,
    val statsSelection: Boolean = false,
    val chromeVisible: Boolean,
    val drawerOpen: Boolean,
    val previewOpen: Boolean,
    val findOpen: Boolean,
    val settingsOpen: Boolean,
) {
    companion object {
        val INITIAL =
            EditorUiState(
                doc = null,
                title = "",
                loading = true,
                readOnly = false,
                save = SaveState.Clean,
                conflict = null,
                stats = null,
                chromeVisible = true,
                drawerOpen = false,
                previewOpen = false,
                findOpen = false,
                settingsOpen = false,
            )
    }
}

/** "Changed on disk — Reload · Keep mine · Save both" / "File was moved or deleted" (01 §6.4). */
sealed interface ConflictState {
    data class ChangedOnDisk(
        val diskText: String,
        val diskBaseline: FileStat,
    ) : ConflictState

    data object Gone : ConflictState
}

enum class ConflictAction { Reload, KeepMine, SaveBoth, SaveAsNew, Close }

/** One-shot events from [EditorViewModel] to the UI, sent through a `Channel(BUFFERED)`. */
sealed interface EditorEvent {
    data class Install(
        val request: InstallRequest,
    ) : EditorEvent

    data class AfterOpen(
        val showIme: Boolean,
    ) : EditorEvent

    data class Message(
        val text: String,
    ) : EditorEvent

    /** T18: a share-out chooser intent, ready to hand to `Context.startActivity` (never built/started inside the
     * ViewModel itself — 01 §5, no ViewModel holds a Context). */
    data class ShareIntent(
        val intent: Intent,
    ) : EditorEvent
}
