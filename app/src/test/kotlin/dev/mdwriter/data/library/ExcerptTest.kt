package dev.mdwriter.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Rows from the T12 task spec (§B). `Excerpt.fromPrefix` is a one-line delegate to `DocTitle.excerpt` (T04) —
 * if a row here ever disagreed with `DocTitleTest`, T04 would win (per the task file). */
class ExcerptTest {
    @Test
    fun stripsHeadingMarkerAndBoldOnSecondLine() {
        assertThat(Excerpt.fromPrefix("# Title\n\nIt had been **raining**.")).isEqualTo("It had been raining.")
    }

    @Test
    fun stripsTaskMarkerAndItalic() {
        assertThat(Excerpt.fromPrefix("Title\n- [ ] call *Ana*")).isEqualTo("call Ana")
    }

    @Test
    fun skipsFrontMatterAndStripsQuoteMarker() {
        assertThat(Excerpt.fromPrefix("---\ntitle: x\n---\n# T\n> quoted")).isEqualTo("quoted")
    }

    @Test
    fun singleLineHasNoExcerpt() {
        assertThat(Excerpt.fromPrefix("Only one line")).isNull()
    }

    @Test
    fun skipsFenceLineAndTakesCodeBody() {
        assertThat(Excerpt.fromPrefix("T\n```\ncode here")).isEqualTo("code here")
    }

    @Test
    fun stripsLinkMarkupKeepsPlainWords() {
        assertThat(
            Excerpt.fromPrefix("T\nsee [notes](https://e.com) and snake_case_word"),
        ).isEqualTo("see notes and snake_case_word")
    }
}
