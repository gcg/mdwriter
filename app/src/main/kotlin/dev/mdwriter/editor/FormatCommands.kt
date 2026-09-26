package dev.mdwriter.editor

import dev.mdwriter.markdown.ListKind
import dev.mdwriter.markdown.SmartEdit
import dev.mdwriter.markdown.TextEdit
import dev.mdwriter.ui.toolbar.ToolbarAction

/**
 * Pure `ToolbarAction -> TextEdit?` mapping (01 §6.1's "Toolbar mapping" table). No Android import; every case
 * delegates to a single [SmartEdit] function. `null` = clipboard action (the caller applies it directly, never
 * through [EditorController.apply]) — see [EditorController.perform].
 */
internal object FormatCommands {
    fun edit(
        action: ToolbarAction,
        text: String,
        selStart: Int,
        selEnd: Int,
        enableHighlight: Boolean,
    ): TextEdit? {
        val s = minOf(selStart, selEnd)
        val e = maxOf(selStart, selEnd)
        return when (action) {
            ToolbarAction.Bold -> SmartEdit.toggleWrap(text, s, e, "**")
            ToolbarAction.Italic -> SmartEdit.toggleWrap(text, s, e, "*")
            ToolbarAction.Strike -> SmartEdit.toggleWrap(text, s, e, "~~")
            ToolbarAction.Highlight -> SmartEdit.toggleWrap(text, s, e, "==")
            ToolbarAction.Code -> SmartEdit.codeToggle(text, s, e)
            ToolbarAction.CodeBlock -> SmartEdit.toggleCodeBlock(text, s, e)
            ToolbarAction.Link -> SmartEdit.insertLink(text, s, e)
            ToolbarAction.Quote -> SmartEdit.toggleQuote(text, s, e)
            ToolbarAction.BulletList -> SmartEdit.toggleList(text, s, e, ListKind.BULLET)
            ToolbarAction.NumberedList -> SmartEdit.toggleList(text, s, e, ListKind.ORDERED)
            ToolbarAction.TaskList -> SmartEdit.toggleList(text, s, e, ListKind.TASK)
            ToolbarAction.ClearFormatting -> SmartEdit.clearFormatting(text, s, e, enableHighlight)
            ToolbarAction.HeadingCycle -> keepSelection(SmartEdit.cycleHeading(text, s), s, e)
            is ToolbarAction.SetHeading -> keepSelection(SmartEdit.setHeading(text, s, action.level), s, e)
            ToolbarAction.Cut, ToolbarAction.Copy, ToolbarAction.Paste, ToolbarAction.SelectAll -> null
        }
    }

    /** Heading commands only take a single cursor position; re-anchor the ORIGINAL [s, e) selection (if any)
     * through the edit's own start/end/replacement shift, so a selected title stays selected. */
    private fun keepSelection(
        ed: TextEdit,
        s: Int,
        e: Int,
    ): TextEdit = if (s == e) ed else ed.copy(selStart = mapPos(ed, s), selEnd = mapPos(ed, e))

    private fun mapPos(
        ed: TextEdit,
        p: Int,
    ): Int =
        when {
            p < ed.start -> p
            p >= ed.end -> p + ed.replacement.length - (ed.end - ed.start)
            else -> ed.start + ed.replacement.length
        }
}
