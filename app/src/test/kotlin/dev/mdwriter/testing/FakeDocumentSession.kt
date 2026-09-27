package dev.mdwriter.testing

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.key
import dev.mdwriter.ui.editor.DocumentSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [DocumentSession] fake: records every [open]/[flush]/[onCurrentRefChanged] call so
 * `LibraryViewModelTest` can assert on them directly, without a real `EditorViewModel`. */
class FakeDocumentSession(
    initial: DocRef? = null,
) : DocumentSession {
    data class OpenCall(
        val ref: DocRef,
        val showIme: Boolean,
        val leaveCurrent: Boolean,
    )

    private val _current = MutableStateFlow(initial)
    override val current: StateFlow<DocRef?> = _current.asStateFlow()

    val openCalls = mutableListOf<OpenCall>()
    var flushCalls = 0
        private set
    val currentRefChangedCalls = mutableListOf<Pair<DocRef, DocRef>>()

    override suspend fun flush() {
        flushCalls++
    }

    override suspend fun open(
        ref: DocRef,
        showIme: Boolean,
        leaveCurrent: Boolean,
    ) {
        openCalls += OpenCall(ref, showIme, leaveCurrent)
        _current.value = ref
    }

    override fun onCurrentRefChanged(
        old: DocRef,
        new: DocRef,
    ) {
        currentRefChangedCalls += old to new
        if (_current.value?.key() == old.key()) _current.value = new
    }

    fun setCurrent(ref: DocRef?) {
        _current.value = ref
    }
}
