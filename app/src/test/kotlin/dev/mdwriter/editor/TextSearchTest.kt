package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.markdown.TextEdit
import org.junit.Test

/**
 * Pure-JVM table test for [TextSearch] (T17 step 2 — every row of the task's own table, plus the range helpers
 * and replacement functions).
 */
class TextSearchTest {
    // ---- findAll -------------------------------------------------------------------------------------------------

    @Test
    fun findAllCaseInsensitive() {
        val r = TextSearch.findAll("cat Cat CAT", "cat", matchCase = false)
        assertThat(r.toList()).isEqualTo(listOf(0, 3, 4, 7, 8, 11))
    }

    @Test
    fun findAllCaseSensitive() {
        val r = TextSearch.findAll("cat Cat CAT", "cat", matchCase = true)
        assertThat(r.toList()).isEqualTo(listOf(0, 3))
    }

    @Test
    fun findAllNonOverlappingLeftToRight() {
        val r = TextSearch.findAll("aaaa", "aa", matchCase = true)
        assertThat(r.toList()).isEqualTo(listOf(0, 2, 2, 4))
    }

    @Test
    fun findAllSurrogatePairQuery() {
        val r = TextSearch.findAll("a👍b👍", "👍", matchCase = true)
        assertThat(r.toList()).isEqualTo(listOf(1, 3, 4, 6))
    }

    @Test
    fun findAllAccentedCaseInsensitiveMatchesAllThree() {
        val r = TextSearch.findAll("Café CAFÉ café", "café", matchCase = false)
        assertThat(r.size / 2).isEqualTo(3)
    }

    @Test
    fun findAllAccentedCaseSensitiveMatchesOnlyExactCase() {
        val r = TextSearch.findAll("Café CAFÉ café", "café", matchCase = true)
        assertThat(r.toList()).isEqualTo(listOf(10, 14))
    }

    @Test
    fun findAllNoFullCaseFoldingForSharpS() {
        // "Straße" never case-folds to "strasse" (that needs full Unicode case folding, not per-char) — only the
        // all-uppercase "STRASSE" matches case-insensitively.
        val r = TextSearch.findAll("Straße STRASSE", "strasse", matchCase = false)
        assertThat(r.toList()).isEqualTo(listOf(7, 14))
    }

    @Test
    fun findAllEmptyQueryIsEmpty() {
        assertThat(TextSearch.findAll("anything", "", matchCase = false)).isEmpty()
    }

    @Test
    fun findAllQueryLongerThanTextIsEmpty() {
        assertThat(TextSearch.findAll("hi", "hello", matchCase = false)).isEmpty()
    }

    @Test
    fun findAllRespectsLimit() {
        val r = TextSearch.findAll("aaaa", "a", matchCase = true, limit = 3)
        assertThat(r.size / 2).isEqualTo(3)
    }

    @Test
    fun findAllMatchesAcrossNewline() {
        val r = TextSearch.findAll("a\nb a", "a", matchCase = true)
        assertThat(r.toList()).isEqualTo(listOf(0, 1, 4, 5))
    }

    // ---- indexAtOrAfter --------------------------------------------------------------------------------------------

    private val abc = intArrayOf(0, 3, 4, 7, 8, 11) // "cat Cat CAT" case-insensitive matches

    @Test
    fun indexAtOrAfterExactStart() {
        assertThat(TextSearch.indexAtOrAfter(abc, 4)).isEqualTo(1)
    }

    @Test
    fun indexAtOrAfterBetweenMatches() {
        assertThat(TextSearch.indexAtOrAfter(abc, 3)).isEqualTo(1)
    }

    @Test
    fun indexAtOrAfterPastLastMatchWrapsToZero() {
        assertThat(TextSearch.indexAtOrAfter(abc, 12)).isEqualTo(0)
    }

    @Test
    fun indexAtOrAfterEmptyIsNegativeOne() {
        assertThat(TextSearch.indexAtOrAfter(IntArray(0), 0)).isEqualTo(-1)
    }

    // ---- shift -----------------------------------------------------------------------------------------------------

    @Test
    fun shiftInsertBeforeAMatch() {
        val r = TextSearch.shift(intArrayOf(10, 15), start = 0, removed = 0, added = 3)
        assertThat(r.toList()).isEqualTo(listOf(13, 18))
    }

    @Test
    fun shiftInsertAtMatchStartMovesTheMatch() {
        val r = TextSearch.shift(intArrayOf(10, 15), start = 10, removed = 0, added = 2)
        assertThat(r.toList()).isEqualTo(listOf(12, 17))
    }

    @Test
    fun shiftInsertAtMatchEndKeepsTheMatch() {
        val r = TextSearch.shift(intArrayOf(10, 15), start = 15, removed = 0, added = 2)
        assertThat(r.toList()).isEqualTo(listOf(10, 15))
    }

    @Test
    fun shiftInsertStrictlyInsideAMatchDropsIt() {
        val r = TextSearch.shift(intArrayOf(10, 15), start = 12, removed = 0, added = 1)
        assertThat(r).isEmpty()
    }

    @Test
    fun shiftDeletionSpanningAMatchDropsIt() {
        val r = TextSearch.shift(intArrayOf(10, 15), start = 8, removed = 10, added = 0)
        assertThat(r).isEmpty()
    }

    @Test
    fun shiftReplaceAfterAllMatchesIsUnchanged() {
        val r = TextSearch.shift(intArrayOf(10, 15), start = 20, removed = 2, added = 5)
        assertThat(r.toList()).isEqualTo(listOf(10, 15))
    }

    // ---- dropRange -------------------------------------------------------------------------------------------------

    @Test
    fun dropRangeDropsOnlyIntersectingMatches() {
        val r = TextSearch.dropRange(intArrayOf(0, 3, 4, 7, 8, 11), from = 4, to = 8)
        assertThat(r.toList()).isEqualTo(listOf(0, 3, 8, 11))
    }

    // ---- window ----------------------------------------------------------------------------------------------------

    /** 1000 non-overlapping one-char-apart matches: pair `i` is `[10i, 10i+1)`. */
    private fun bigRanges(): IntArray {
        val out = IntArray(2000)
        for (i in 0 until 1000) {
            out[2 * i] = 10 * i
            out[2 * i + 1] = 10 * i + 1
        }
        return out
    }

    @Test
    fun windowFocused900GivesFirst500AndLocal400() {
        val w = TextSearch.window(bigRanges(), focused = 900, max = 500)
        assertThat(w.first).isEqualTo(500)
        assertThat(w.focused).isEqualTo(400)
        assertThat(w.ranges.size / 2).isEqualTo(500)
    }

    @Test
    fun windowFocused10GivesFirst0() {
        val w = TextSearch.window(bigRanges(), focused = 10, max = 500)
        assertThat(w.first).isEqualTo(0)
        assertThat(w.focused).isEqualTo(10)
    }

    @Test
    fun windowFocusedMinusOneGivesLocalMinusOne() {
        val w = TextSearch.window(bigRanges(), focused = -1, max = 500)
        assertThat(w.focused).isEqualTo(-1)
    }

    @Test
    fun windowUnderMaxReturnsWholeArray() {
        val r = intArrayOf(0, 3, 4, 7)
        val w = TextSearch.window(r, focused = 1, max = 500)
        assertThat(w.ranges).isEqualTo(r)
        assertThat(w.first).isEqualTo(0)
        assertThat(w.focused).isEqualTo(1)
    }

    // ---- replaceOne / replaceAll -----------------------------------------------------------------------------------

    @Test
    fun replaceAllProducesOneEditCoveringFirstToLastMatch() {
        val r = TextSearch.findAll("aaa", "a", matchCase = true)
        val edit = TextSearch.replaceAll("aaa", r, "aa", caret = 3)
        checkNotNull(edit)
        val (text, sel) = edit.applyTo("aaa")
        assertThat(text).isEqualTo("aaaaaa")
        assertThat(edit.selStart).isEqualTo(6)
        assertThat(edit.selEnd).isEqualTo(6)
        assertThat(sel).isEqualTo(6..6)
    }

    @Test
    fun replaceAllWithNoMatchesReturnsNull() {
        assertThat(TextSearch.replaceAll("abc", IntArray(0), "x", caret = 0)).isNull()
    }

    @Test
    fun replaceAllMovesCaretBetweenMatchesByAccumulatedDelta() {
        // "cat cat cat", replace "cat" -> "dog" (same length: delta stays 0 per match). Caret sits between the
        // 1st and 2nd match (offset 3, inside the space) -> moves only by the already-applied deltas (0).
        val text = "cat cat cat"
        val r = TextSearch.findAll(text, "cat", matchCase = true)
        val edit = TextSearch.replaceAll(text, r, "dog", caret = 3)
        checkNotNull(edit)
        assertThat(edit.applyTo(text).first).isEqualTo("dog dog dog")
        assertThat(edit.selStart).isEqualTo(3)
    }

    @Test
    fun replaceAllCaretInsideAMatchLandsAtTheEndOfItsReplacement() {
        val text = "cat cat cat"
        val r = TextSearch.findAll(text, "cat", matchCase = true)
        // caret at offset 5, inside the second match [4,7). The first match [0,3) is fully before the caret and
        // contributes delta = repl.length - matchLength = 4 - 3 = 1; the caret's own match then maps to
        // s(4) + delta(1) + repl.length(4) = 9 -- the end of THIS match's own replacement.
        val edit = TextSearch.replaceAll(text, r, "dogs", caret = 5)
        checkNotNull(edit)
        assertThat(edit.selStart).isEqualTo(9)
    }

    @Test
    fun replaceCurrentNeverReselectsReplacedText() {
        val text = "cat cat cat"
        var r = TextSearch.findAll(text, "cat", matchCase = true)
        assertThat(r.toList()).isEqualTo(listOf(0, 3, 4, 7, 8, 11))
        // Replace match index 1 ("cat" at [4,7)) with "cats"; the applied edit is minimized to an insert of "s"
        // right at the match's own end (7,0,1) — exactly what EditorController.apply's TextEdit.minimize produces.
        val replaced = TextSearch.replaceOne(r, 1, "cats")
        assertThat(replaced).isEqualTo(TextEdit(4, 7, "cats", 8, 8))
        r = TextSearch.shift(r, 7, 0, 1)
        r = TextSearch.dropRange(r, 4, 8)
        assertThat(r.toList()).isEqualTo(listOf(0, 3, 9, 12))
        assertThat(TextSearch.indexAtOrAfter(r, 8)).isEqualTo(1)
    }
}
