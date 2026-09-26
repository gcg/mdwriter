package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Ported from `test/StatsHarness.kt`'s `cases` list; expected tuples transcribed from §9's table. */
class StatsTest {
    private fun st(md: String): Stats {
        val h = MarkdownHighlighter()
        h.setText(md)
        return TextStats.compute(md, 0, md.length, h.spans())
    }

    private data class Case(
        val input: String,
        val words: Int,
        val chars: Int,
        val charsNoSpaces: Int,
        val sentences: Int,
        val tasks: Int,
        val tasksDone: Int,
        val readMin: Int,
    )

    private val cases =
        listOf(
            Case("Hello world", 2, 11, 10, 1, 0, 0, 1),
            Case("# Heading with **bold** text", 4, 28, 24, 1, 0, 0, 1),
            Case("[link text](https://example.com/a/b \"Title\")", 2, 44, 42, 1, 0, 0, 1),
            Case("- [x] done task\n- [ ] open task", 4, 30, 23, 1, 2, 1, 1),
            Case("don't stop e-mail 3.14 1,000 snake_case", 6, 39, 34, 1, 0, 0, 1),
            Case("日本語のテキスト", 8, 8, 8, 1, 0, 0, 1),
            Case("中文 English 混合", 5, 13, 11, 1, 0, 0, 1),
            Case("한국어 단어 세 개", 4, 10, 7, 1, 0, 0, 1),
            Case("Café naïve résumé", 3, 17, 15, 1, 0, 0, 1),
            Case("`inline code` and\n\n```kotlin\nval x = 1\n```", 6, 38, 33, 1, 0, 0, 1),
            Case("---\ntitle: Front matter words\n---\nBody only.", 2, 41, 37, 1, 0, 0, 1),
            Case("<!-- hidden comment --> visible", 0, 31, 27, 0, 0, 0, 0),
            Case("Emoji 👍🏽 test", 2, 13, 11, 1, 0, 0, 1),
            Case("Two sentences. Here! Right?", 4, 27, 24, 3, 0, 0, 1),
            Case("[^1] footnote\n\n[^1]: note text", 3, 28, 25, 1, 0, 0, 1),
            Case("&amp; &copy; entity", 1, 19, 17, 1, 0, 0, 1),
            Case("[ref]: http://x.y \"t\"", 0, 21, 19, 0, 0, 0, 0),
            Case("<div>\nInside div text\n</div>", 3, 26, 24, 1, 0, 0, 1),
            Case("สวัสดีครับ ภาษาไทย", 2, 18, 17, 1, 0, 0, 1),
        )

    @TestFactory
    fun harnessRows(): List<DynamicTest> =
        cases.mapIndexed { i, c ->
            dynamicTest("${i + 1} ${c.input.replace("\n", "\\n")}") {
                val s = st(c.input)
                assertEquals(
                    listOf(c.words, c.chars, c.charsNoSpaces, c.sentences, c.tasks, c.tasksDone, c.readMin),
                    listOf(
                        s.words,
                        s.chars,
                        s.charsNoSpaces,
                        s.sentences,
                        s.tasks,
                        s.tasksDone,
                        s.readingMinutesRounded(),
                    ),
                )
            }
        }

    @Test
    fun selectionSubRange() {
        val h = MarkdownHighlighter()
        h.setText("Hello brave world")
        assertEquals(1, TextStats.compute("Hello brave world", 6, 11, h.spans()).words)
    }

    @Test
    fun readingMinutesRounding() {
        assertEquals(1, Stats(238, 0, 0, 0, 0, 0).readingMinutesRounded())
        assertEquals(2, Stats(239, 0, 0, 0, 0, 0).readingMinutesRounded())
        assertEquals(0, Stats.ZERO.readingMinutesRounded())
    }
}
