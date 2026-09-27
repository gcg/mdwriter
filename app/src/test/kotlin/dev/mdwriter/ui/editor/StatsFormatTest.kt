package dev.mdwriter.ui.editor

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.markdown.Stats
import dev.mdwriter.markdown.TextStats
import org.junit.Test
import java.util.Locale

/** Task T15, Reference §D. */
class StatsFormatTest {
    private val locale = Locale.US

    @Test
    fun sampleSentenceWordsThenCharactersThenSentences() {
        val text = "don't stop e-mail 3.14 1,000 snake_case"
        val stats = TextStats.compute(text, 0, text.length, emptyList())
        assertThat(formatStats(stats, StatsDisplay.Words, isSelection = false, locale)).isEqualTo("6 words · 1 min")
        assertThat(formatStats(stats, StatsDisplay.Characters, isSelection = false, locale)).isEqualTo("39 characters")
        assertThat(formatStats(stats, StatsDisplay.Sentences, isSelection = false, locale)).isEqualTo("1 sentence")
    }

    @Test
    fun threeSentencePunctuationMarksGiveThreeSentences() {
        val text = "Two sentences. Here! Right?"
        val stats = TextStats.compute(text, 0, text.length, emptyList())
        assertThat(formatStats(stats, StatsDisplay.Sentences, isSelection = false, locale)).isEqualTo("3 sentences")
    }

    @Test
    fun largeWordCountRoundsReadingTimeUp() {
        val stats = Stats(words = 1204, chars = 0, charsNoSpaces = 0, sentences = 0, tasks = 0, tasksDone = 0)
        assertThat(formatStats(stats, StatsDisplay.Words, isSelection = false, locale)).isEqualTo("1,204 words · 6 min")
    }

    @Test
    fun selectionPrefix() {
        val stats = Stats(words = 42, chars = 7_113, charsNoSpaces = 0, sentences = 86, tasks = 0, tasksDone = 0)
        assertThat(formatStats(stats, StatsDisplay.Words, isSelection = true, locale)).isEqualTo("Selected: 42 words")
        assertThat(
            formatStats(stats, StatsDisplay.Characters, isSelection = true, locale),
        ).isEqualTo("Selected: 7,113 characters")
        assertThat(
            formatStats(stats, StatsDisplay.Sentences, isSelection = true, locale),
        ).isEqualTo("Selected: 86 sentences")
        assertThat(
            formatStats(stats.copy(words = 1204), StatsDisplay.ReadingTime, isSelection = true, locale),
        ).isEqualTo("Selected: 6 min read")
    }

    @Test
    fun zeroWordsShowsNoReadingTime() {
        val stats = Stats.ZERO
        assertThat(formatStats(stats, StatsDisplay.Words, isSelection = false, locale)).isEqualTo("0 words")
    }

    @Test
    fun singularForms() {
        val one = Stats(words = 1, chars = 1, charsNoSpaces = 1, sentences = 1, tasks = 0, tasksDone = 0)
        assertThat(formatStats(one, StatsDisplay.Words, isSelection = true, locale)).isEqualTo("Selected: 1 word")
        assertThat(formatStats(one, StatsDisplay.Characters, isSelection = false, locale)).isEqualTo("1 character")
        assertThat(formatStats(one, StatsDisplay.Sentences, isSelection = false, locale)).isEqualTo("1 sentence")
    }
}
