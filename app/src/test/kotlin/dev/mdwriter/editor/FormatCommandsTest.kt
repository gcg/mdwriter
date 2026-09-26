package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.toolbar.ToolbarAction
import org.junit.Test

/** Pure-JVM test for [FormatCommands] (Acceptance 2). */
class FormatCommandsTest {
    @Test
    fun boldWrapsSelectionAndKeepsItSelected() {
        val text = "make this bold"
        val start = text.indexOf("this")
        val end = start + "this".length
        val edit = FormatCommands.edit(ToolbarAction.Bold, text, start, end, enableHighlight = false)!!
        val (newText, sel) = edit.applyTo(text)
        assertThat(newText).isEqualTo("make **this** bold")
        assertThat(newText.substring(sel.first, sel.last)).isEqualTo("this")
    }

    @Test
    fun headingCycleKeepsPlainTitleSelected() {
        val text = "Title"
        val edit = FormatCommands.edit(ToolbarAction.HeadingCycle, text, 0, text.length, enableHighlight = false)!!
        val (newText, sel) = edit.applyTo(text)
        assertThat(newText).isEqualTo("# Title")
        assertThat(newText.substring(sel.first, sel.last)).isEqualTo("Title")
    }

    @Test
    fun setHeadingLevelTwoKeepsTitleSelected() {
        val text = "Title"
        val edit =
            FormatCommands.edit(ToolbarAction.SetHeading(2), text, 0, text.length, enableHighlight = false)!!
        val (newText, sel) = edit.applyTo(text)
        assertThat(newText).isEqualTo("## Title")
        assertThat(newText.substring(sel.first, sel.last)).isEqualTo("Title")
    }

    @Test
    fun codeOnTwoLineSelectionProducesAFence() {
        val text = "line1\nline2"
        val edit = FormatCommands.edit(ToolbarAction.Code, text, 0, text.length, enableHighlight = false)!!
        assertThat(edit.start).isEqualTo(0)
        assertThat(edit.end).isEqualTo(text.length)
        assertThat(edit.replacement).isEqualTo("```\nline1\nline2\n```")
    }

    /**
     * [FormatCommands]'s private `mapPos` re-anchors a selection that spans OUTSIDE a heading edit's own
     * `[start, end)` (the "before"/"after" branches, both hit here by selecting the whole quoted line) and
     * one that lands STRICTLY inside it (the "else"/"inside" branch) — plus the boundary "insert-at" case
     * where a selection endpoint sits exactly at an empty `[start, end)` insertion point
     * ([headingCycleKeepsPlainTitleSelected] above already covers that: `selStart = 0 == ed.start == ed.end`).
     */
    @Test
    fun mapPosCoversBeforeInsideAndAfter() {
        val text = "> ### Title" // quote prefix ("> ") is OUTSIDE cycleHeading's own [contentStart, +4) edit range
        // Whole line selected: '>' (offset 0) is BEFORE the edit range; the doc end (offset 11) is AFTER it.
        val whole = FormatCommands.edit(ToolbarAction.HeadingCycle, text, 0, text.length, enableHighlight = false)!!
        val (newText, sel) = whole.applyTo(text)
        assertThat(newText).isEqualTo("> Title")
        assertThat(sel.first).isEqualTo(0)
        assertThat(sel.last).isEqualTo(newText.length)

        // A selection ending strictly INSIDE the removed "### " prefix maps to the edit's own insertion point.
        val partial = FormatCommands.edit(ToolbarAction.HeadingCycle, text, 1, 3, enableHighlight = false)!!
        assertThat(partial.selStart).isEqualTo(1) // offset 1 ('>' 's trailing space) is still BEFORE the edit
        assertThat(partial.selEnd).isEqualTo(2) // offset 3 (inside "### ") collapses to the edit's own start
    }

    @Test
    fun clipboardActionsReturnNull() {
        val text = "anything"
        for (action in listOf(ToolbarAction.Cut, ToolbarAction.Copy, ToolbarAction.Paste, ToolbarAction.SelectAll)) {
            assertThat(FormatCommands.edit(action, text, 0, text.length, enableHighlight = false)).isNull()
        }
    }
}
