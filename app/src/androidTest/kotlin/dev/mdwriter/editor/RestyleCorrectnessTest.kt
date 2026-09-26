package dev.mdwriter.editor

import android.content.Intent
import android.text.Spanned
import android.view.inputmethod.EditorInfo
import androidx.core.view.doOnPreDraw
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.debug.EditorPerfActivity
import dev.mdwriter.editor.spans.MdStyleSpan
import dev.mdwriter.editor.spans.PaintTextMeasurer
import dev.mdwriter.editor.spans.SpanFactory
import dev.mdwriter.editor.spans.SpanKind
import dev.mdwriter.editor.spans.SpanMaterializer
import dev.mdwriter.markdown.MarkdownHighlighter
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Instrumented: after each scripted edit sequence, the LIVE set of [MdStyleSpan]s must exactly match a document
 * built [fresh][fresh] from scratch (same text, same [dev.mdwriter.editor.spans.EditorStyle], a brand-new
 * highlighter) — the incremental reconcile never drifts from what a full rebuild would produce. Rule 5 (never
 * touch composing/selection/system spans) is exercised directly by [imeComposition]. [headingGrowsInFirstFrame]
 * additionally proves same-frame restyle (Acceptance 6): the heading span exists on the very draw that follows
 * the character that completed it.
 */
@RunWith(AndroidJUnit4::class)
class RestyleCorrectnessTest {
    private data class Key(
        val kind: Int,
        val arg: Int,
        val start: Int,
        val end: Int,
    )

    private fun waitUntil(
        timeoutMs: Long,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(20)
        }
        check(condition()) { "condition not met within ${timeoutMs}ms" }
    }

    private fun launchEmpty(): ActivityScenario<EditorPerfActivity> {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), EditorPerfActivity::class.java).apply {
                putExtra("perfEdits", 0)
            }
        val scenario = ActivityScenario.launch<EditorPerfActivity>(intent)
        waitUntil(10_000) {
            var ready = false
            scenario.onActivity { ready = it.controller.editText.layout != null }
            ready
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        return scenario
    }

    private fun awaitIdle(scenario: ActivityScenario<EditorPerfActivity>) {
        waitUntil(5_000) {
            var idle = false
            scenario.onActivity { idle = it.controller.isRestyleIdle }
            idle
        }
    }

    /** Types [s] one character at a time starting at [startOffset], moving the selection along like real input. */
    private fun typeAt(
        scenario: ActivityScenario<EditorPerfActivity>,
        startOffset: Int,
        s: String,
    ): Int {
        var pos = startOffset
        for (c in s) {
            scenario.onActivity { activity ->
                val et = activity.controller.editText
                et.text!!.insert(pos, c.toString())
                et.setSelection(pos + 1)
            }
            pos++
        }
        return pos
    }

    private fun spanKeys(e: Spanned): List<Key> =
        e
            .getSpans(0, e.length, MdStyleSpan::class.java)
            .map { Key(it.kind, it.arg, e.getSpanStart(it), e.getSpanEnd(it)) }
            .sortedWith(compareBy({ it.start }, { it.end }, { it.kind }, { it.arg }))

    /** Same [text], built from scratch with a NEW highlighter but the SAME [dev.mdwriter.editor.spans.EditorStyle]. */
    private fun fresh(
        scenario: ActivityScenario<EditorPerfActivity>,
        text: String,
    ): List<Key> {
        var result: List<Key> = emptyList()
        scenario.onActivity { activity ->
            val style = activity.controller.style
            val hl = MarkdownHighlighter(enableHighlight = style.highlightSyntax, enableFrontMatter = true)
            val ssb =
                buildStyledDocument(text, hl, SpanFactory(PaintTextMeasurer(style)), SpanMaterializer(style), null)
            result = spanKeys(ssb)
        }
        return result
    }

    private fun assertConsistentWithFreshBuild(scenario: ActivityScenario<EditorPerfActivity>) {
        var text = ""
        var liveKeys: List<Key> = emptyList()
        scenario.onActivity { activity ->
            val e = activity.controller.editText.text!!
            text = e.toString()
            liveKeys = spanKeys(e)
        }
        assertThat(liveKeys).isEqualTo(fresh(scenario, text))
    }

    @Test
    fun typeAtxHeading() {
        val scenario = launchEmpty()
        typeAt(scenario, 0, "# Hi")
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun typeStrong() {
        val scenario = launchEmpty()
        typeAt(scenario, 0, "**b**")
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun openAndCloseFence() {
        val scenario = launchEmpty()
        val paragraph = "A paragraph.\n"
        typeAt(scenario, 0, paragraph)
        awaitIdle(scenario)

        // Insert an opening fence + newline ABOVE the paragraph: it should immediately band as CODE_BLOCK while
        // the fence is still open (before this test ever closes it).
        typeAt(scenario, 0, "```\n")
        awaitIdle(scenario)

        var bandedWhileOpen = false
        var afterParagraphOffset = 0
        scenario.onActivity { activity ->
            val e = activity.controller.editText.text!!
            val paraStart = e.indexOf("A paragraph.")
            bandedWhileOpen =
                e.getSpans(paraStart, paraStart + 1, MdStyleSpan::class.java).any { it.kind == SpanKind.CODE_BLOCK }
            afterParagraphOffset = paraStart + "A paragraph.\n".length
        }
        assertThat(bandedWhileOpen).isTrue()

        // Close the fence right after the paragraph line: the paragraph's content is still inside a (now
        // complete) fenced code block, so it stays banded — only `assertConsistentWithFreshBuild` needs to hold.
        typeAt(scenario, afterParagraphOffset, "```\n")
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun deleteHeadingMarker() {
        val scenario = launchEmpty()
        typeAt(scenario, 0, "# Hi")
        awaitIdle(scenario)
        scenario.onActivity { activity ->
            activity.controller.editText.text!!
                .delete(0, 2)
        } // remove "# "
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun newlineInsideStrong() {
        val scenario = launchEmpty()
        typeAt(scenario, 0, "**bold**")
        awaitIdle(scenario)
        scenario.onActivity { activity ->
            val e = activity.controller.editText.text!!
            e.insert(e.indexOf("bo") + 2, "\n") // split "**bold**" in the middle of its content
        }
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun pasteMultiLineBlock() {
        val scenario = launchEmpty()
        typeAt(scenario, 0, "before\n")
        awaitIdle(scenario)
        val block =
            buildString {
                for (i in 0 until 40) append("Line ").append(i).append(" with **bold** and `code`.\n")
            }
        scenario.onActivity { activity ->
            val e = activity.controller.editText.text!!
            e.replace(e.length, e.length, block) // one paste, not char-by-char
        }
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun imeComposition() {
        val scenario = launchEmpty()
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            val ic = checkNotNull(et.onCreateInputConnection(EditorInfo())) { "no InputConnection" }
            ic.setComposingText("Ti", 1)
            ic.setComposingText("Tit", 1)
            ic.finishComposingText()
        }
        awaitIdle(scenario)
        assertConsistentWithFreshBuild(scenario)
    }

    @Test
    fun headingGrowsInFirstFrame() {
        val scenario = launchEmpty()
        val pos = typeAt(scenario, 0, "# ") // marker + space, no content yet: line stays body height
        awaitIdle(scenario)

        var bodyHeight = 0
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            val layout = et.layout!!
            val ex = et.lineSpacingExtra
            bodyHeight = (layout.getLineBottom(0) - layout.getLineTop(0) - ex).roundToInt()
        }
        assertThat(bodyHeight).isGreaterThan(0)

        val latch = CountDownLatch(1)
        var heightAtFirstDraw = 0
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.text!!.insert(pos, "T") // completes the heading's content
            et.setSelection(pos + 1)
            et.doOnPreDraw {
                val layout = et.layout!!
                val ex = et.lineSpacingExtra
                heightAtFirstDraw = (layout.getLineBottom(0) - layout.getLineTop(0) - ex).roundToInt()
                latch.countDown()
            }
        }
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue()
        assertThat(heightAtFirstDraw.toDouble()).isAtLeast(1.55 * bodyHeight)
    }
}
