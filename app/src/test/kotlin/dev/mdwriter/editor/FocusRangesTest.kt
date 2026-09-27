package dev.mdwriter.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.text.BreakIterator
import java.util.Locale

/** Test-only sentence breaker so this whole test stays pure JVM (`android.icu` must never be required here —
 * 01 §2/pitfalls). Same contract as [IcuSentenceBreaker]. */
private class JavaSentenceBreaker : SentenceBreaker {
    private val bi = BreakIterator.getSentenceInstance(Locale.US)

    override fun boundaries(paragraph: String): IntArray {
        bi.setText(paragraph)
        val out = ArrayList<Int>()
        var b = bi.first()
        while (b != BreakIterator.DONE) {
            out += b
            b = bi.next()
        }
        return out.toIntArray()
    }
}

/** Task T15, step 2 — every case from the task's own table. */
class FocusRangesTest {
    private val br = JavaSentenceBreaker()
    private val text1 = "One. Two. Three."

    private fun sentence(
        t: String,
        selStart: Int,
        selEnd: Int = selStart,
    ) = FocusRanges.compute(t, selStart, selEnd, FocusModeKind.Sentence, br)

    private fun paragraph(
        t: String,
        selStart: Int,
        selEnd: Int = selStart,
    ) = FocusRanges.compute(t, selStart, selEnd, FocusModeKind.Paragraph, br)

    @Test
    fun sentenceCaret6() {
        assertThat(sentence(text1, 6)).isEqualTo(FocusRange(5, 10))
    }

    @Test
    fun sentenceCaret0() {
        assertThat(sentence(text1, 0)).isEqualTo(FocusRange(0, 5))
    }

    @Test
    fun sentenceCaret4() {
        assertThat(sentence(text1, 4)).isEqualTo(FocusRange(0, 5))
    }

    @Test
    fun sentenceCaret5() {
        assertThat(sentence(text1, 5)).isEqualTo(FocusRange(5, 10))
    }

    @Test
    fun sentenceCaret16() {
        assertThat(sentence(text1, 16)).isEqualTo(FocusRange(10, 16))
    }

    @Test
    fun sentenceSelect2To7() {
        assertThat(sentence(text1, 2, 7)).isEqualTo(FocusRange(0, 10))
    }

    @Test
    fun sentenceSelect2To10() {
        assertThat(sentence(text1, 2, 10)).isEqualTo(FocusRange(0, 10))
    }

    @Test
    fun sentenceSelectAcrossParagraphs() {
        val t = "A one. A two.\nB one. B two."
        assertThat(sentence(t, 2, 17)).isEqualTo(FocusRange(0, 21))
    }

    @Test
    fun paragraphCaret3() {
        assertThat(paragraph("a\nbb\nccc", 3)).isEqualTo(FocusRange(2, 4))
    }

    @Test
    fun paragraphEmptyLine() {
        assertThat(paragraph("a\n\nb", 2)).isEqualTo(FocusRange(2, 2))
    }

    @Test
    fun paragraphScanClampsAt10000() {
        val t = "x".repeat(25_000)
        assertThat(paragraph(t, 12_500)).isEqualTo(FocusRange(2_500, 22_500))
    }

    @Test
    fun paragraphMultiLineSelection() {
        val t = "one\ntwo\nthree\nfour"
        // selection spans "two" through "three" -> first line start ("two"'s) to last line end ("three"'s).
        val start = t.indexOf("two")
        val end = t.indexOf("three") + "three".length
        assertThat(paragraph(t, start + 1, end - 1)).isEqualTo(FocusRange(start, end))
    }

    @Test
    fun offReturnsNone() {
        assertThat(FocusRanges.compute(text1, 6, 6, FocusModeKind.Off, br)).isEqualTo(FocusRange.NONE)
        assertThat(FocusRanges.compute(text1, 2, 10, FocusModeKind.Off, br)).isEqualTo(FocusRange.NONE)
    }
}
