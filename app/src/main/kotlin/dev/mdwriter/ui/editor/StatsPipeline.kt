package dev.mdwriter.ui.editor

import dev.mdwriter.editor.StatsInput
import dev.mdwriter.markdown.Stats
import dev.mdwriter.markdown.TextStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** What [StatsPipeline] needs from a live document (T15, 01 §6.2/§7). `EditorScreen` adapts a real
 * [dev.mdwriter.editor.EditorController] to this; tests use a fake. */
interface StatsSource {
    val triggers: Flow<Unit>
    val version: Long
    val selection: Pair<Int, Int>

    fun snapshot(): StatsInput
}

data class DisplayStats(
    val stats: Stats,
    val isSelection: Boolean,
)

/** What the stats line shows; tapping cycles through these in order (02 §5). */
enum class StatsDisplay {
    Words,
    Characters,
    Sentences,
    ReadingTime,
    ;

    fun next(): StatsDisplay = entries[(ordinal + 1) % entries.size]
}

/**
 * Debounced (400 ms) stats computation (01 §7): a document-wide result is cached by [StatsInput.version] so an
 * unrelated caret move with the same version never recomputes; a selection always recomputes (cheap — selections
 * are short). Compute always runs on [compute] (`Dispatchers.Default` in production) — never main. A version bump
 * that lands *during* compute discards that stale result; a newer trigger re-fires [collectLatest] and cancels it
 * outright.
 */
class StatsPipeline(
    private val src: StatsSource,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
    private val debounceMs: Long = 400L,
) {
    private var cacheVersion = -1L
    private var cacheDoc: Stats? = null

    fun flow(): Flow<DisplayStats> =
        channelFlow {
            src.triggers.onStart { emit(Unit) }.collectLatest {
                delay(debounceMs)
                val (s, e) = src.selection
                if (s == e && src.version == cacheVersion) {
                    cacheDoc?.let { send(DisplayStats(it, false)) }
                    return@collectLatest
                }
                val input = src.snapshot()
                val stats =
                    withContext(compute) {
                        if (s == e) {
                            TextStats.compute(input.text, 0, input.text.length, input.spans)
                        } else {
                            TextStats.compute(input.text, minOf(s, e), maxOf(s, e), input.spans)
                        }
                    }
                if (input.version != src.version) return@collectLatest // stale: a newer edit re-triggers
                if (s == e) {
                    cacheVersion = input.version
                    cacheDoc = stats
                }
                send(DisplayStats(stats, s != e))
            }
        }
}

private fun plural(
    n: Int,
    singular: String,
    plural: String,
): String = if (n == 1) singular else plural

/** 02 §5: `"1,204 words · 6 min"` / `"0 words"` (no reading time) / `"Selected: 42 words"` / … */
fun formatStats(
    stats: Stats,
    display: StatsDisplay,
    isSelection: Boolean,
    locale: Locale = Locale.getDefault(),
): String {
    val nf = NumberFormat.getIntegerInstance(locale)
    val core =
        when (display) {
            StatsDisplay.Words -> {
                val words = "${nf.format(stats.words)} ${plural(stats.words, "word", "words")}"
                when {
                    isSelection -> words
                    stats.words == 0 -> "0 words"
                    else -> "$words · ${stats.readingMinutesRounded()} min"
                }
            }

            StatsDisplay.Characters -> {
                "${nf.format(stats.chars)} ${plural(stats.chars, "character", "characters")}"
            }

            StatsDisplay.Sentences -> {
                "${nf.format(stats.sentences)} ${plural(stats.sentences, "sentence", "sentences")}"
            }

            StatsDisplay.ReadingTime -> {
                "${stats.readingMinutesRounded()} min read"
            }
        }
    return if (isSelection) "Selected: $core" else core
}
