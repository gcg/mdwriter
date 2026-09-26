package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Every row of `test/SmartHarness.kt`'s `main()` output, transcribed by hand from
 * `plans/research/markdown.md` §8's harness table (78 rows), plus the harness's toggle-twice and
 * `minimize()` properties. `⏎` in the research doc is `\n`; the table cells are the harness's
 * `show()` output with exactly one backtick stripped from each end.
 */
class SmartEditTest {
    private data class Row(
        val i: Int,
        val op: String,
        val before: String,
        val after: String,
        val f: (String, Int, Int) -> TextEdit?,
    )

    companion object {
        private val enter = { t: String, a: Int, _: Int -> SmartEdit.onEnter(t, a) }
        private val enterIndentedCode = {
            t: String,
            a: Int,
            _: Int,
            ->
            SmartEdit.onEnter(t, a, lineType = BlockType.INDENTED_CODE)
        }
        private val enterFencedCode = {
            t: String,
            a: Int,
            _: Int,
            ->
            SmartEdit.onEnter(t, a, lineType = BlockType.FENCED_CODE)
        }
        private val enterUnclosedFence = { t: String, a: Int, _: Int ->
            SmartEdit.onEnter(t, a, lineType = BlockType.FENCE_OPEN, fenceUnclosed = true)
        }
        private val enterClosedFence = { t: String, a: Int, _: Int ->
            SmartEdit.onEnter(t, a, lineType = BlockType.FENCE_OPEN, fenceUnclosed = false)
        }
        private val quote = { t: String, a: Int, b: Int -> SmartEdit.toggleQuote(t, a, b) }
        private val setHeading2 = { t: String, a: Int, _: Int -> SmartEdit.setHeading(t, a, 2) }
        private val setHeading0 = { t: String, a: Int, _: Int -> SmartEdit.setHeading(t, a, 0) }
        private val setHeading1 = { t: String, a: Int, _: Int -> SmartEdit.setHeading(t, a, 1) }
        private val backspace = { t: String, a: Int, _: Int -> SmartEdit.onBackspace(t, a) }
        private val highlight = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "==") }
        private val italic = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "*") }
        private val bold = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "**") }
        private val strike = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "~~") }
        private val codeWrap = { t: String, a: Int, b: Int -> SmartEdit.toggleWrap(t, a, b, "`") }
        private val link = { t: String, a: Int, b: Int -> SmartEdit.insertLink(t, a, b) }
        private val image = { t: String, a: Int, b: Int -> SmartEdit.insertLink(t, a, b, image = true) }
        private val heading = { t: String, a: Int, _: Int -> SmartEdit.cycleHeading(t, a) }
        private val indent = { t: String, a: Int, _: Int -> SmartEdit.indentListItem(t, a) }
        private val outdent = { t: String, a: Int, _: Int -> SmartEdit.outdentListItem(t, a) }
        private val task = { t: String, a: Int, _: Int -> SmartEdit.toggleTask(t, a) }

        private val ROWS =
            listOf(
                Row(1, "Enter", "- item|", "- item\n- |", enter),
                Row(2, "Enter", "* item|", "* item\n* |", enter),
                Row(3, "Enter", "+ item|", "+ item\n+ |", enter),
                Row(4, "Enter", "- it|em", "- it\n- |em", enter),
                Row(5, "Enter", "1. one|", "1. one\n2. |", enter),
                Row(6, "Enter", "9) nine|", "9) nine\n10) |", enter),
                Row(7, "Enter", "1. one|\n2. two\n3. three", "1. one\n2. |\n3. two\n4. three", enter),
                Row(8, "Enter", "- [ ] task|", "- [ ] task\n- [ ] |", enter),
                Row(9, "Enter", "- [x] done|", "- [x] done\n- [ ] |", enter),
                Row(10, "Enter", "> quote|", "> quote\n> |", enter),
                Row(11, "Enter", "> - quoted item|", "> - quoted item\n> - |", enter),
                Row(12, "Enter", "- |", "|", enter),
                Row(13, "Enter", "1. |", "|", enter),
                Row(14, "Enter", "- [ ] |", "|", enter),
                Row(15, "Enter", "  - |", "- |", enter),
                Row(16, "Enter", "- a\n  - |", "- a\n- |", enter),
                Row(17, "Enter", "> |", "|", enter),
                Row(18, "Enter", "> - |", "> |", enter),
                Row(19, "Enter", "|- item", "(default newline)", enter),
                Row(20, "Enter", "plain text|", "(default newline)", enter),
                Row(21, "Enter", "---|", "(default newline)", enter),
                Row(22, "Enter", "-not a list|", "(default newline)", enter),
                Row(23, "Enter(code line)", "    code|", "    code\n    |", enterIndentedCode),
                Row(24, "Enter(fenced, list)", "- ```\n  - not a list|", "- ```\n  - not a list\n  |", enterFencedCode),
                Row(25, "Enter(unclosed fence)", "```kotlin|", "```kotlin\n|\n```", enterUnclosedFence),
                Row(26, "Enter(unclosed fence in quote)", "> ~~~~|", "> ~~~~\n> |\n> ~~~~", enterUnclosedFence),
                Row(27, "Enter(closed fence)", "```kotlin|\n```", "(default newline)", enterClosedFence),
                Row(28, "Enter", "1. a\n1. b|", "1. a\n1. b\n1. |", enter),
                Row(29, "Enter", "  1. nested|", "  1. nested\n  2. |", enter),
                Row(30, "Enter", "- a|\n- b", "- a\n- |\n- b", enter),
                Row(31, "Quote", "some |text", "> some |text", quote),
                Row(32, "Quote", "> some |text", "some |text", quote),
                Row(33, "Quote", "«one\n\ntwo»", "> «one\n>\n> two»", quote),
                Row(34, "Quote", "«> one\n>\n> two»", "«one\n\ntwo»", quote),
                Row(35, "Quote", "> > dee|p", "> dee|p", quote),
                Row(36, "SetHeading(2)", "Title|", "## Title|", setHeading2),
                Row(37, "SetHeading(0)", "### Ti|tle", "Ti|tle", setHeading0),
                Row(38, "SetHeading(1)", "- ## item|", "- # item|", setHeading1),
                Row(39, "Backspace", "- |item", "|item", backspace),
                Row(40, "Backspace", "  - [ ] |task", "  |task", backspace),
                Row(41, "Backspace", "> > |q", "> |q", backspace),
                Row(42, "Backspace", "- it|em", "(default newline)", backspace),
                Row(43, "Highlight", "«key»", "==«key»==", highlight),
                Row(44, "Italic", "*«x»*", "«x»", italic),
                Row(45, "Bold", "*«x»*", "***«x»***", bold),
                Row(46, "Bold", "make «this» bold", "make **«this»** bold", bold),
                Row(47, "Bold", "make **«this»** bold", "make «this» bold", bold),
                Row(48, "Bold", "make «**this**» bold", "make «this» bold", bold),
                Row(49, "Bold", "make th|is bold", "make **«this»** bold", bold),
                Row(50, "Bold", "make «this » bold", "make **«this»**  bold", bold),
                Row(51, "Bold", "empty | here", "empty **|** here", bold),
                Row(52, "Bold", "***«x»***", "*«x»*", bold),
                Row(53, "Italic", "make «this» it", "make *«this»* it", italic),
                Row(54, "Italic", "make *«this»* it", "make «this» it", italic),
                Row(55, "Italic", "make **«this»** it", "make ***«this»*** it", italic),
                Row(56, "Italic", "***«x»***", "**«x»**", italic),
                Row(57, "Italic", "«line one\nline two»", "«*line one*\n*line two*»", italic),
                Row(58, "Strike", "«gone»", "~~«gone»~~", strike),
                Row(59, "Strike", "~~«gone»~~", "«gone»", strike),
                Row(60, "Code", "«val x»", "`«val x»`", codeWrap),
                Row(61, "Code", "«a `tick`»", "`` «a `tick`» ``", codeWrap),
                Row(62, "Link", "see «docs» here", "see [docs](|) here", link),
                Row(63, "Link", "«https://ia.net»", "[|](https://ia.net)", link),
                Row(64, "Link", "at | end", "at [](|) end", link),
                Row(65, "Image", "«diagram»", "![diagram](|)", image),
                Row(66, "Heading", "Title|", "# Title|", heading),
                Row(67, "Heading", "# Title|", "## Title|", heading),
                Row(68, "Heading", "## Title|", "### Title|", heading),
                Row(69, "Heading", "### Title|", "Title|", heading),
                Row(70, "Heading", "#### Title|", "Title|", heading),
                Row(71, "Heading", "> Quote| title", "> # Quote| title", heading),
                Row(72, "Indent", "- a\n- b|", "- a\n  - b|", indent),
                Row(73, "Indent", "1. a\n2. b|", "1. a\n   1. b|", indent),
                Row(74, "Indent", "10. a\n11. b|", "10. a\n    1. b|", indent),
                Row(75, "Outdent", "- a\n  - b|", "- a\n- b|", outdent),
                Row(76, "Outdent", "- b|", "(default newline)", outdent),
                Row(77, "Task", "- [ ] t|odo", "- [x] t|odo", task),
                Row(78, "Task", "- [x] t|odo", "- [ ] t|odo", task),
            )
    }

    @TestFactory
    fun harnessRows(): List<DynamicTest> =
        ROWS.map { row ->
            dynamicTest("${row.i} ${row.op} ${row.before}") {
                assertEquals(row.after, apply(row.before, row.f))
            }
        }

    /** Applying a toggle twice, starting from the selection the first call returned, is the identity. */
    @Test
    fun toggleTwiceIsIdentity() {
        val samples =
            listOf("a «b» c", "«word»", "x **«y»** z", "«**y**»", "*«i»*", "«multi\nline»", "one tw|o three")
        var bad = 0
        for (s in samples) {
            for (m in listOf("**", "*", "~~", "==", "`")) {
                val (t, a, b) = parseState(s)
                val e1 = SmartEdit.toggleWrap(t, a, b, m)
                val (t1, s1) = e1.applyTo(t)
                val e2 = SmartEdit.toggleWrap(t1, s1.first, s1.last, m)
                val (t2, _) = e2.applyTo(t1)
                if (t2 != t) bad++
            }
        }
        assertEquals(0, bad, "toggle-twice identity violations")
    }

    /** `edit.applyTo(text) == edit.minimize(text).applyTo(text)` for onEnter/toggleWrap/toggleQuote/cycleHeading. */
    @Test
    fun minimizeMatchesFullEdit() {
        var bad = 0
        for (s0 in listOf("1. one|\n2. two\n3. three", "make «this» bold", "> some |text", "### Ti|tle")) {
            val (t, a, b) = parseState(s0)
            val edits =
                listOfNotNull(
                    SmartEdit.onEnter(t, a),
                    SmartEdit.toggleWrap(t, a, b, "**"),
                    SmartEdit.toggleQuote(t, a, b),
                    SmartEdit.cycleHeading(t, a),
                )
            for (e in edits) {
                if (e.applyTo(t) != e.minimize(t).applyTo(t)) bad++
            }
        }
        assertEquals(0, bad, "minimize() violations")
    }
}
