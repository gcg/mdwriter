package dev.mdwriter.editor

import android.content.Intent
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.MainActivity
import dev.mdwriter.editor.spans.HangRoomSpan
import dev.mdwriter.editor.spans.MdStyleSpan
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented: opening [dev.mdwriter.debug.SampleDocs.SMALL] on a phone-width (Compact, 02 §3) portrait screen
 * produces the styling 02 §4 promises (Acceptance 3).
 */
@RunWith(AndroidJUnit4::class)
class InstallStylingDeviceTest {
    private fun findScrollView(view: View): EditorScrollView? {
        if (view is EditorScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findScrollView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun waitUntil(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(50)
        }
        check(condition()) { "condition not met within ${timeoutMs}ms" }
    }

    @Test
    fun installedDocumentIsStyledPerDesignSpec() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
                putExtra("sample", "small")
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var scrollView: EditorScrollView? = null
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val sv = findScrollView(activity.window.decorView)
                    if (sv != null && sv.editText.layout != null && sv.editText.length() > 0) {
                        scrollView = sv
                        ready = true
                    }
                }
                ready
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                val et = scrollView!!.editText
                val text = et.text!!

                // The editable installed by EditorController.install is an MdEditable (01 §4.3).
                assertThat(text).isInstanceOf(MdEditable::class.java)

                val layout = et.layout!!
                val ex = et.lineSpacingExtra

                fun boxHeight(line: Int): Float = (layout.getLineBottom(line) - layout.getLineTop(line)) - ex

                val bodyLine = layout.getLineForOffset(text.indexOf("A paragraph with"))
                val h1Line = layout.getLineForOffset(text.indexOf("Heading one"))
                val h2Line = layout.getLineForOffset(text.indexOf("Heading two"))
                val hBody = boxHeight(bodyLine)

                assertThat(boxHeight(h1Line).toDouble()).isAtLeast(1.55 * hBody)
                assertThat(boxHeight(h2Line).toDouble()).isAtLeast(1.35 * hBody)

                // Every MdStyleSpan is SPAN_EXCLUSIVE_EXCLUSIVE (01 §10 rule 7).
                val spanned = text as Spanned
                val styleSpans = spanned.getSpans(0, text.length, MdStyleSpan::class.java)
                assertThat(styleSpans).isNotEmpty()
                for (sp in styleSpans) {
                    assertThat(spanned.getSpanFlags(sp)).isEqualTo(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                // No HangRoomSpan at 448 dp (Compact width class: hang = 0, 02 §3).
                assertThat(spanned.getSpans(0, text.length, HangRoomSpan::class.java)).isEmpty()

                // The quote's wrapped (2nd visual) line indents further left than its first visual line.
                // NOTE: Layout.getLineLeft() is the alignment-based left edge and does NOT reflect a
                // LeadingMarginSpan's indent for an ALIGN_NORMAL/LTR paragraph; getParagraphLeft() does.
                val quoteOffset = text.indexOf("A quote that is long enough")
                val quoteLine = layout.getLineForOffset(quoteOffset)
                assertThat(layout.getParagraphLeft(quoteLine + 1))
                    .isGreaterThan(layout.getParagraphLeft(quoteLine))
            }
        }
    }
}
