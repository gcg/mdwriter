package dev.mdwriter.data.document

import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.data.storage.StorageError

sealed interface SaveResult {
    data class Saved(
        val newBaseline: FileStat,
    ) : SaveResult

    data class Conflict(
        val onDisk: FileStat,
    ) : SaveResult

    data class Failed(
        val error: StorageError,
    ) : SaveResult
}

/** 01 §6.4: the save indicator in [dev.mdwriter.ui.editor.EditorUiState]. */
sealed interface SaveState {
    data object Clean : SaveState

    data object Dirty : SaveState

    data object Saving : SaveState

    data class Error(
        val message: String,
    ) : SaveState
}
