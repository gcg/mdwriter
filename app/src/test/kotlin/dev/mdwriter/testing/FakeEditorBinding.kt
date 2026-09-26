package dev.mdwriter.testing

import dev.mdwriter.data.document.Snapshot
import dev.mdwriter.ui.editor.EditorBinding

/** In-memory [EditorBinding] fake: no real `EditText`, so tests can drive `snapshot()`/`caret()`/`scrollY()`
 * directly. [install] mirrors what `EditorScreen` does when it receives `EditorEvent.Install`: it replaces the
 * live text and bumps [version] but does NOT emit an edit (matching `EditorController.install`'s own contract). */
class FakeEditorBinding : EditorBinding {
    var text: String = ""
        private set
    var version: Long = 0
        private set
    var caretValue: Int = 0
    var scrollYValue: Int = 0

    fun install(
        text: String,
        selection: Int,
        scrollY: Int,
    ) {
        this.text = text
        version++
        caretValue = selection
        scrollYValue = scrollY
    }

    /** Simulates a user edit: replaces the text and bumps [version] (mirrors `EditorController.edits`). */
    fun edit(newText: String) {
        text = newText
        version++
    }

    override fun snapshot(): Snapshot = Snapshot(version, text)

    override fun caret(): Int = caretValue

    override fun scrollY(): Int = scrollYValue
}
