package dev.mdwriter.editor

import android.text.Spanned
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.spans.MdStyleSpan
import dev.mdwriter.editor.spans.SpanKind
import org.junit.Test
import org.junit.runner.RunWith

/** T19 Acceptance 6: `==mark==` gets MARK spans only while the highlight syntax is on; text/selection survive. */
@RunWith(AndroidJUnit4::class)
class HighlightToggleTest {
    private fun markCount(a: dev.mdwriter.debug.EditorPerfActivity): Int {
        val t = a.controller.editText.text as Spanned
        return t.getSpans(0, t.length, MdStyleSpan::class.java).count { it.kind == SpanKind.MARK }
    }

    @Test
    fun highlightSyntaxTogglesMarkSpans() {
        val text = "plain ==mark== text"
        val scenario = EditorTestHost.launch(text = text, selection = 3)
        EditorTestHost.awaitIdle(scenario)
        scenario.onActivity { a -> assertThat(markCount(a)).isEqualTo(0) }
        for (on in listOf(true, false)) {
            scenario.onActivity { a ->
                a.controller.style.highlightSyntax = on
                a.controller.setStyle(a.controller.style)
            }
            EditorTestHost.awaitIdle(scenario)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { a ->
                assertThat(markCount(a) > 0).isEqualTo(on)
                assertThat(a.controller.snapshot()).isEqualTo(text)
                assertThat(a.controller.editText.selectionStart).isEqualTo(3)
            }
        }
    }
}
