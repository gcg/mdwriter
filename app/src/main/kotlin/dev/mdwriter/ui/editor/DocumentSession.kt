package dev.mdwriter.ui.editor

import dev.mdwriter.data.library.DocRef
import kotlinx.coroutines.flow.StateFlow

/** What the library needs from the open-document session. Implemented by [EditorViewModel]. */
interface DocumentSession {
    val current: StateFlow<DocRef?>

    /** Awaits the pending save of the current document (`AutosaveCoordinator.flush`). */
    suspend fun flush()

    /** Leaves the current doc (flush; auto-name / delete-if-empty unless [leaveCurrent] = false), then opens [ref].
     *  IME is shown after the install iff [showIme]. */
    suspend fun open(
        ref: DocRef,
        showIme: Boolean,
        leaveCurrent: Boolean = true,
    )

    /** The library renamed or moved the open document: re-key autosave, recovery, positions, ui state. */
    fun onCurrentRefChanged(
        old: DocRef,
        new: DocRef,
    )
}
