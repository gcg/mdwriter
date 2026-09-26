package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Exact rows from `plans/tasks/T04-markdown-commands.md` §F for [SmartEdit.toggleList],
 * [SmartEdit.toggleCodeBlock], [SmartEdit.codeToggle] and [SmartEdit.clearFormatting], plus the
 * two harness-style properties (toggle-twice identity, `minimize()`).
 */
class SmartEditTogglesTest {
    private data class Row(
        val op: String,
        val before: String,
        val after: String,
        val f: (String, Int, Int) -> TextEdit?,
    )

    companion object {
        private fun bullet(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.toggleList(t, a, b, ListKind.BULLET)

        private fun ordered(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.toggleList(t, a, b, ListKind.ORDERED)

        private fun taskList(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.toggleList(t, a, b, ListKind.TASK)

        private fun codeBlock(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.toggleCodeBlock(t, a, b)

        private fun codeToggle(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.codeToggle(t, a, b)

        private fun clear(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.clearFormatting(t, a, b)

        private fun clearHl(
            t: String,
            a: Int,
            b: Int,
        ) = SmartEdit.clearFormatting(t, a, b, enableHighlight = true)

        private val ROWS =
            listOf(
                // toggleList BULLET
                Row("toggleList BULLET", "«one\ntwo»", "- «one\n- two»", ::bullet),
                Row("toggleList BULLET", "- «one\n- two»", "«one\ntwo»", ::bullet),
                Row("toggleList BULLET", "ta|sk", "- ta|sk", ::bullet),
                Row("toggleList BULLET", "|", "- |", ::bullet),
                Row("toggleList BULLET", "- |", "|", ::bullet),
                Row("toggleList BULLET", "> «quoted»", "> - «quoted»", ::bullet),
                Row("toggleList BULLET", "  - a\n  - «b»", "  - a\n  «b»", ::bullet),
                Row("toggleList BULLET", "«- a\nb»", "«- a\n- b»", ::bullet),
                Row("toggleList BULLET", "«one\n»two", "- «one\n»two", ::bullet),
                // toggleList ORDERED
                Row("toggleList ORDERED", "«a\n\nb»", "1. «a\n\n2. b»", ::ordered),
                Row("toggleList ORDERED", "- «a\n- b»", "1. «a\n2. b»", ::ordered),
                Row("toggleList ORDERED", "1) «a»", "«a»", ::ordered),
                // toggleList TASK
                Row("toggleList TASK", "1. «a\n2. b»", "- [ ] «a\n- [ ] b»", ::taskList),
                Row("toggleList TASK", "- [x] «done»", "«done»", ::taskList),
                // toggleCodeBlock
                Row("toggleCodeBlock", "«val x = 1\nval y = 2»", "```\n«val x = 1\nval y = 2»\n```", ::codeBlock),
                Row("toggleCodeBlock", "```\n«val x = 1\nval y = 2»\n```", "«val x = 1\nval y = 2»", ::codeBlock),
                Row("toggleCodeBlock", "«```\nval x\n```»", "«val x»", ::codeBlock),
                Row("toggleCodeBlock", "«a ``` b»", "````\n«a ``` b»\n````", ::codeBlock),
                Row("toggleCodeBlock", "|", "```\n|\n```", ::codeBlock),
                Row("toggleCodeBlock", "~~~\n«x»\n~~~", "«x»", ::codeBlock),
                Row("toggleCodeBlock", "```\na\n«b»\n```", "a\n«b»", ::codeBlock),
                // codeToggle
                Row("codeToggle", "«val x»", "`«val x»`", ::codeToggle),
                Row("codeToggle", "`«val x»`", "«val x»", ::codeToggle),
                Row("codeToggle", "«a\nb»", "```\n«a\nb»\n```", ::codeToggle),
                Row("codeToggle", "```\n«x»\n```", "«x»", ::codeToggle),
                // clearFormatting (default enableHighlight = false)
                Row("clearFormatting", "«**bold** and *it*»", "«bold and it»", ::clear),
                Row("clearFormatting", "make **«this»** plain", "make «this» plain", ::clear),
                Row("clearFormatting", "«[docs](https://x.y \"T\")»", "«docs»", ::clear),
                Row("clearFormatting", "«# Title»", "«Title»", ::clear),
                Row("clearFormatting", "«- [x] done ~~old~~»", "«done old»", ::clear),
                Row("clearFormatting", "> «quote `code`»", "«quote code»", ::clear),
                Row("clearFormatting", "snake_ca«se»", "snake_ca«se»", ::clear),
                Row("clearFormatting", "# Ti|tle", "Ti|tle", ::clear),
                Row("clearFormatting", "«_a_ __b__»", "«a b»", ::clear),
                Row("clearFormatting", "«![alt](i.png)»", "«alt»", ::clear),
                Row("clearFormatting", "«<https://x.y>»", "«https://x.y»", ::clear),
                Row("clearFormatting (enableHighlight=true)", "==«mark»==", "«mark»", ::clearHl),
                Row("clearFormatting (enableHighlight=false)", "==«mark»==", "==«mark»==", ::clear),
            )
    }

    @TestFactory
    fun rows(): List<DynamicTest> =
        ROWS.mapIndexed { i, row ->
            dynamicTest("${i + 1} ${row.op} ${row.before}") {
                assertEquals(row.after, apply(row.before, row.f))
            }
        }

    /** Applying a toggle twice, using the selection returned by the first call, gives back the original text. */
    @Test
    fun toggleTwiceIsIdentity() {
        var bad = 0

        fun check(
            s: String,
            f: (String, Int, Int) -> TextEdit?,
        ) {
            val (t, a, b) = parseState(s)
            val e1 = f(t, a, b) ?: return
            val (t1, s1) = e1.applyTo(t)
            val e2 = f(t1, s1.first, s1.last) ?: return
            val (t2, _) = e2.applyTo(t1)
            if (t2 != t) bad++
        }
        for (s in listOf(
            "«one\ntwo\nthree»",
            "«a\n\nb»",
            "ta|sk",
            "|",
            "> «quoted\n> lines»",
            "  «indented»",
            "«# Heading\npara»",
        )) {
            for (kind in ListKind.entries) check(s) { t, a, b -> SmartEdit.toggleList(t, a, b, kind) }
        }
        for (s in listOf("«val x = 1\nval y = 2»", "«a ``` b»", "|", "x|y", "«one»\ntwo")) {
            check(s, ::codeBlock)
            check(s, ::codeToggle)
        }
        assertEquals(0, bad, "toggle-twice identity violations")
    }

    /** `e.applyTo(t) == e.minimize(t).applyTo(t)` for every row's edit. */
    @Test
    fun minimizeMatchesFullEdit() {
        var bad = 0
        for (row in ROWS) {
            val (t, a, b) = parseState(row.before)
            val e = row.f(t, a, b) ?: continue
            if (e.applyTo(t) != e.minimize(t).applyTo(t)) bad++
        }
        assertEquals(0, bad, "minimize() violations")
    }
}
