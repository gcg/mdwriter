package dev.mdwriter.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** Exact rows from `plans/tasks/T04-markdown-commands.md` §F's `DocTitleTest` table. */
class DocTitleTest {
    @TestFactory
    fun fromContentRows(): List<DynamicTest> {
        val cases =
            listOf(
                "# Hello World\n\nBody" to "Hello World",
                "\n\n  \n## **Bold** title" to "Bold title",
                "> - [ ] Buy *milk*" to "Buy milk",
                "[Docs](https://x.y) and `code`" to "Docs and code",
                "---\ntitle: X\n---\nReal title" to "Real title",
                "---\n\nAfter rule" to "After rule",
                "```kotlin\nval x = 1\n```" to "val x = 1",
                "  Trailing spaces   " to "Trailing spaces",
                "Title with \\*escaped\\* stars" to "Title with *escaped* stars",
                "<b>Bold</b> tag" to "Bold tag",
            )
        return cases.mapIndexed { i, (input, expected) ->
            dynamicTest("${i + 1} ${input.replace("\n", "\\n")}") {
                assertEquals(expected, DocTitle.fromContent(input))
            }
        }
    }

    @Test
    fun fromContentNullCases() {
        for (input in listOf("", "\n   \n", "---", "- [ ] ")) {
            assertNull(DocTitle.fromContent(input), "input=${input.replace("\n", "\\n")}")
        }
    }

    @Test
    fun excerptRows() {
        assertEquals("First para line", DocTitle.excerpt("# Title\n\nFirst **para** line\nsecond"))
        assertNull(DocTitle.excerpt("Title only"))
        assertNull(DocTitle.excerpt(""))
        assertEquals("item one", DocTitle.excerpt("---\ntitle: X\n---\n# T\n- item one"))
        val long = DocTitle.excerpt("# T\n\n" + "word ".repeat(40))
        assertEquals(120, long?.length)
        assertTrue(long!!.endsWith("…"))
    }

    @TestFactory
    fun sanitizeFileNameRows(): List<DynamicTest> {
        val cases =
            listOf(
                "My: Note/Draft?" to "My NoteDraft",
                "  a\tb\n c  " to "a b c",
                "Title..." to "Title",
                "...hidden" to "hidden",
                "a <b> \"c\" |d|" to "a b c d",
                "Ünïcödé 日本語" to "Ünïcödé 日本語",
            )
        return cases.mapIndexed { i, (input, expected) ->
            dynamicTest("${i + 1} ${input.replace("\n", "\\n").replace("\t", "\\t")}") {
                assertEquals(expected, DocTitle.sanitizeFileName(input))
            }
        }
    }

    @Test
    fun sanitizeFileNameFallsBackToUntitled() {
        for (input in listOf("", "   ", "***", "\u0000\u0007")) {
            assertEquals("Untitled", DocTitle.sanitizeFileName(input), "input=$input")
        }
    }

    @Test
    fun sanitizeFileNameCapsLength() {
        assertEquals("a".repeat(80), DocTitle.sanitizeFileName("a".repeat(200)))
        assertEquals("a".repeat(79), DocTitle.sanitizeFileName("a".repeat(79) + "\uD83D\uDE00"))
        assertEquals("a".repeat(79), DocTitle.sanitizeFileName("a".repeat(79) + ". b"))
    }
}
