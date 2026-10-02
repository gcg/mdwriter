package dev.mdwriter.hardening

import android.text.Spanned
import android.text.style.SuggestionSpan
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.debug.EditorPerfActivity
import dev.mdwriter.editor.EditorTestHost
import dev.mdwriter.editor.spans.MdStyleSpan
import dev.mdwriter.editor.spans.SpanKind
import org.junit.Test
import org.junit.runner.RunWith

/** T20 Reference G: real InputConnection (SmartInputConnection) composition against the styled editor. */
@RunWith(AndroidJUnit4::class)
class ImeCompositionTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun spans(a: EditorPerfActivity): List<Triple<Int, Int, Int>> {
        val t = a.controller.editText.text as Spanned
        return t
            .getSpans(0, t.length, MdStyleSpan::class.java)
            .map { Triple(it.kind, t.getSpanStart(it), t.getSpanEnd(it)) }
            .sortedWith(compareBy({ it.first }, { it.second }, { it.third }))
    }

    @Test
    fun composingWordKeepsHeadingStyle() {
        val scenario = EditorTestHost.launch(text = "# Title\n", selection = 8)
        EditorTestHost.awaitIdle(scenario)
        val ic = EditorTestHost.ic(scenario)
        var before = emptyList<Triple<Int, Int, Int>>()
        scenario.onActivity { before = spans(it).filter { s -> s.first == SpanKind.HEADING } }
        inst.runOnMainSync {
            listOf("h", "he", "hel", "hello").forEach { ic.setComposingText(it, 1) }
            ic.finishComposingText()
            ic.commitText(" ", 1)
        }
        scenario.onActivity { a ->
            assertThat(a.controller.snapshot()).endsWith("hello ")
            assertThat(spans(a).filter { it.first == SpanKind.HEADING }).isEqualTo(before)
        }
    }

    @Test
    fun autocorrectAboveStyledTextDoesNotShiftSpans() {
        val text = "teh cat\n\nSome **bold** text\n"
        val scenario = EditorTestHost.launch(text = text, selection = 0)
        EditorTestHost.awaitIdle(scenario)
        val ic = EditorTestHost.ic(scenario)
        var before = emptyList<Triple<Int, Int, Int>>()
        scenario.onActivity { before = spans(it).filter { s -> s.first == SpanKind.STRONG } }
        assertThat(before).isNotEmpty()
        inst.runOnMainSync {
            ic.setComposingRegion(0, 3)
            ic.commitText("the", 1)
        }
        EditorTestHost.awaitIdle(scenario)
        scenario.onActivity { a ->
            assertThat(a.controller.snapshot()).startsWith("the cat")
            val after = spans(a).filter { it.first == SpanKind.STRONG }
            assertThat(after).isEqualTo(before)
        }
    }

    @Test
    fun suggestionSpanSurvivesAnEditAbove() {
        val scenario = EditorTestHost.launch(text = "one\ntwo\nthree\n", selection = 14)
        EditorTestHost.awaitIdle(scenario)
        val ic = EditorTestHost.ic(scenario)
        inst.runOnMainSync {
            val word = android.text.SpannableString("wrold")
            word.setSpan(
                SuggestionSpan(inst.targetContext, arrayOf("world"), SuggestionSpan.FLAG_EASY_CORRECT),
                0,
                word.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            ic.commitText(word, 1)
            ic.finishComposingText()
            ic.setComposingRegion(0, 0)
        }
        var startBefore = -1
        scenario.onActivity { a ->
            val t = a.controller.editText.text as Spanned
            val ss = t.getSpans(0, t.length, SuggestionSpan::class.java)
            assertThat(ss).hasLength(1)
            startBefore = t.getSpanStart(ss[0])
            a.controller.editText.setSelection(0)
        }
        inst.runOnMainSync { ic.commitText("New line\n", 1) }
        EditorTestHost.awaitIdle(scenario)
        scenario.onActivity { a ->
            val t = a.controller.editText.text as Spanned
            val ss = t.getSpans(0, t.length, SuggestionSpan::class.java)
            assertThat(ss).hasLength(1)
            assertThat(t.getSpanStart(ss[0])).isEqualTo(startBefore + 9)
            assertThat(t.subSequence(t.getSpanStart(ss[0]), t.getSpanEnd(ss[0])).toString()).isEqualTo("wrold")
        }
    }

    @Test
    fun restyleFrameDuringCompositionKeepsComposingRegion() {
        val scenario = EditorTestHost.launch(text = "", selection = 0)
        val ic = EditorTestHost.ic(scenario)
        // requestFocus schedules an IMM restartInput that finishes composing on the old connection: let it settle.
        inst.waitForIdleSync()
        Thread.sleep(500)
        var immediate = -2
        inst.runOnMainSync {
            ic.setComposingText("**bo", 1)
            immediate =
                android.view.inputmethod.BaseInputConnection
                    .getComposingSpanStart(scenarioText(scenario))
        }
        assertThat(immediate).isAtLeast(0)
        inst.waitForIdleSync()
        EditorTestHost.awaitIdle(scenario)
        scenario.onActivity { a ->
            val t = a.controller.editText.text
            assertThat(
                android.view.inputmethod.BaseInputConnection
                    .getComposingSpanStart(t),
            ).isAtLeast(0)
            assertThat(t.toString()).isEqualTo("**bo")
        }
    }

    @Test
    fun batchEditEqualsFullRestyle() {
        val scenario = EditorTestHost.launch(text = "Some *x* text", selection = 4)
        EditorTestHost.awaitIdle(scenario)
        val ic = EditorTestHost.ic(scenario)
        inst.runOnMainSync {
            ic.beginBatchEdit()
            ic.commitText("a", 1)
            ic.deleteSurroundingText(1, 0)
            ic.commitText("b", 1)
            ic.endBatchEdit()
        }
        EditorTestHost.awaitIdle(scenario)
        var batched = emptyList<Triple<Int, Int, Int>>()
        scenario.onActivity { a ->
            batched = spans(a)
            a.controller.setStyle(a.controller.style) // full reflow + markAllDirty
        }
        EditorTestHost.awaitIdle(scenario)
        scenario.onActivity { a -> assertThat(spans(a)).isEqualTo(batched) }
    }

    private fun scenarioText(
        scenario: androidx.test.core.app.ActivityScenario<EditorPerfActivity>,
    ): android.text.Editable {
        var t: android.text.Editable? = null
        scenario.onActivity { t = it.controller.editText.text }
        return t!!
    }
}
