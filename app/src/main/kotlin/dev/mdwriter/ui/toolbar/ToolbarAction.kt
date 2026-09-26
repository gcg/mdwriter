package dev.mdwriter.ui.toolbar

/**
 * Every formatting/clipboard command: keyboard shortcuts ([dev.mdwriter.editor.SmartInput], T08), the selection
 * pill and accessibility custom actions (T09). Plain Kotlin, no Compose — the one `ui` package the editor engine
 * may import (01 §3), so that [dev.mdwriter.editor.EditorController.perform] can take one of these directly.
 */
sealed interface ToolbarAction {
    data object Bold : ToolbarAction

    data object Italic : ToolbarAction

    data object Strike : ToolbarAction

    data object Highlight : ToolbarAction

    data object Code : ToolbarAction

    data object CodeBlock : ToolbarAction

    data object Link : ToolbarAction

    data object HeadingCycle : ToolbarAction

    data class SetHeading(
        val level: Int,
    ) : ToolbarAction {
        init {
            require(level in 0..6)
        }
    }

    data object Quote : ToolbarAction

    data object BulletList : ToolbarAction

    data object NumberedList : ToolbarAction

    data object TaskList : ToolbarAction

    data object ClearFormatting : ToolbarAction

    data object Cut : ToolbarAction

    data object Copy : ToolbarAction

    data object Paste : ToolbarAction

    data object SelectAll : ToolbarAction
}

/** Inline wraps are no-ops on verbatim lines (code/HTML/front matter); Code counts only for single-line selections. */
val ToolbarAction.isInlineWrap: Boolean
    get() =
        this == ToolbarAction.Bold || this == ToolbarAction.Italic ||
            this == ToolbarAction.Strike || this == ToolbarAction.Highlight || this == ToolbarAction.Link
