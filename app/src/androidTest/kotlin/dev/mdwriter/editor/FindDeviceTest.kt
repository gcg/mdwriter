package dev.mdwriter.editor

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.markdown.TextEdit
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented: [EditorController]'s find/replace API end to end (T17 Acceptance 2, step 8, cases 1-10). */
@RunWith(AndroidJUnit4::class)
class FindDeviceTest {
    @Test
    fun findReportsCountIndexAndHighlights() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val r = runBlocking { activity.controller.find("cat", false) }
            assertThat(r.count).isEqualTo(3)
            assertThat(r.index).isEqualTo(0)
            assertThat(
                activity.controller.editText
                    .getSearchResultHighlights()!!
                    .toList(),
            ).isEqualTo(listOf(0, 3, 4, 7, 8, 11))
            assertThat(activity.controller.editText.getFocusedSearchResultIndex()).isEqualTo(0)
        }
    }

    @Test
    fun findNextWrapsAndFindPreviousStepsBack() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val c = activity.controller
            runBlocking { c.find("cat", false) }
            c.findNext() // 0 -> 1
            c.findNext() // 1 -> 2
            c.findNext() // 2 -> 0 (wraps)
            assertThat(c.findResult.value.index).isEqualTo(0)
            c.findPrevious() // 0 -> 2 (wraps back)
            assertThat(c.findResult.value.index).isEqualTo(2)
        }
    }

    @Test
    fun matchCaseNarrowsTheCount() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val r = runBlocking { activity.controller.find("cat", true) }
            assertThat(r.count).isEqualTo(2)
        }
    }

    @Test
    fun highlightsShiftImmediatelyOnATextEditNoDelay() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val c = activity.controller
            runBlocking { c.find("cat", false) }
            c.apply(TextEdit(0, 0, "x", 1, 1))
            // Synchronous: FindSession's TextWatcher shifts the ranges as part of the very same `apply` call.
            assertThat(
                activity.controller.editText
                    .getSearchResultHighlights()!!
                    .toList(),
            ).isEqualTo(listOf(1, 4, 5, 8, 9, 12))
        }
    }

    @Test
    fun replaceCurrentReplacesOneMatchAsOneUndoStep() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val c = activity.controller
            runBlocking { c.find("cat", false) }
            val ok = c.replaceCurrent("dog")
            assertThat(ok).isTrue()
            assertThat(c.snapshot()).isEqualTo("dog cat Cat")
            val focused = focusedRangeOf(c)
            assertThat(focused).isEqualTo(4 to 7)
            c.undo()
            assertThat(c.snapshot()).isEqualTo("cat cat Cat")
        }
    }

    @Test
    fun replaceAllReplacesEveryMatchAsOneUndoStep() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val c = activity.controller
            runBlocking { c.find("cat", false) }
            val n = runBlocking { c.replaceAll("cow") }
            assertThat(n).isEqualTo(3)
            assertThat(c.snapshot()).isEqualTo("cow cow cow")
            c.undo() // ONE undo restores the whole thing
            assertThat(c.snapshot()).isEqualTo("cat cat Cat")
        }
    }

    @Test
    fun clearFindSelectsTheFocusedMatchAndClearsHighlights() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0)
        scenario.onActivity { activity ->
            val c = activity.controller
            runBlocking { c.find("cat", false) }
            c.clearFind(selectFocused = true)
            assertThat(c.editText.selectionStart).isEqualTo(0)
            assertThat(c.editText.selectionEnd).isEqualTo(3)
            assertThat(c.editText.getSearchResultHighlights()!!).isEmpty()
        }
    }

    @Test
    fun findScrollsAMatchOnLine1500IntoView() {
        val lines =
            (1..2000).map { n -> if (n == 1500) "UNIQUEWORD" else "line $n has some filler text on it" }
        val text = lines.joinToString("\n")
        val scenario = EditorTestHost.launch(text = text, selection = 0)
        scenario.onActivity { activity -> runBlocking { activity.controller.find("UNIQUEWORD", true) } }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync() // let bringPointIntoView's scroll land
        scenario.onActivity { activity ->
            val c = activity.controller
            assertThat(c.scrollView.scrollY).isGreaterThan(0)
            val layout = checkNotNull(c.editText.layout)
            val matchLine = layout.getLineForOffset(text.indexOf("UNIQUEWORD"))
            val lineTop = layout.getLineTop(matchLine)
            val viewport = Rect()
            c.scrollView.childViewport(viewport)
            assertThat(lineTop).isAtLeast(viewport.top)
            assertThat(lineTop).isAtMost(viewport.bottom)
        }
    }

    @Test
    fun readOnlyDocumentBlocksReplace() {
        val scenario = EditorTestHost.launch(text = "cat cat Cat", selection = 0, readOnly = true)
        scenario.onActivity { activity ->
            val c = activity.controller
            runBlocking { c.find("cat", false) }
            val ok = c.replaceCurrent("dog")
            assertThat(ok).isFalse()
            assertThat(c.snapshot()).isEqualTo("cat cat Cat")
        }
    }

    @Test
    fun findOn300kCharsFinishesUnder500msAndCapsTheHighlightWindow() {
        // A literal 300k-char Intent extra hits the platform's Binder transaction size limit and crashes the
        // whole instrumentation run (confirmed on-device) — `sample` generates the text INSIDE the activity
        // instead (same mechanism T07's own perf harness uses for this exact document size).
        val scenario = EditorTestHost.launch(text = null, sample = "300k")
        // EditorTestHost.launch's own wait only checks for a first (possibly still-empty) layout pass — the
        // 300k-char document is built off-main inside `install()` and can genuinely still be in flight at that
        // point (confirmed on-device: a `find` right after `launch` alone raced it and always saw 0 matches).
        EditorTestHost.waitUntil(10_000) {
            var big = false
            scenario.onActivity { big = it.controller.snapshot().length > 200_000 }
            big
        }
        scenario.onActivity { activity ->
            val c = activity.controller
            val t0 = System.currentTimeMillis()
            val r = runBlocking { c.find("e", false) }
            val elapsed = System.currentTimeMillis() - t0
            assertThat(elapsed).isLessThan(500)
            assertThat(r.count).isGreaterThan(TextSearch.HIGHLIGHT_WINDOW)
            assertThat(c.editText.getSearchResultHighlights()!!.size).isAtMost(2 * TextSearch.HIGHLIGHT_WINDOW)
        }
    }

    /** Reads the focused match's `[start, end)` off the live TextView highlight arrays (no internal API needed):
     * the focused index selects a pair inside [android.widget.TextView.getSearchResultHighlights]. */
    private fun focusedRangeOf(c: EditorController): Pair<Int, Int> {
        val idx = c.editText.getFocusedSearchResultIndex()
        val h = c.editText.getSearchResultHighlights()!!
        return h[2 * idx] to h[2 * idx + 1]
    }
}
