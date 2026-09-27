package dev.mdwriter.editor

import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.debug.EditorPerfActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task T15, Acceptance 2/4: typewriter scrolling keeps the caret line at 45 % of the viewport through a run of
 * scripted commits, the way a real IME's `commitText` would drive it. Reuses [EditorPerfActivity] (see
 * [FocusOverlayDeviceTest]'s KDoc for why, over hosting a bare [EditorScrollView] directly).
 */
@RunWith(AndroidJUnit4::class)
class TypewriterDeviceTest {
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
    fun typewriterKeepsCaretLineNear45PercentThroughThirtyCommits() {
        val lines = (0 until 60).joinToString("\n") { "Line $it" }
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), EditorPerfActivity::class.java).apply {
                putExtra("text", lines)
                putExtra("selection", lines.length)
                putExtra("perfEdits", 0)
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<EditorPerfActivity>(intent).use { scenario ->
            var et: MarkdownEditText? = null
            var sv: EditorScrollView? = null
            waitUntil {
                scenario.onActivity { a ->
                    val e = a.controller.editText
                    if (e.layout != null) {
                        et = e
                        sv = a.controller.scrollView
                    }
                }
                et != null
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                et!!.requestFocus()
                it.controller.typewriter = true
            }
            instrumentation.waitForIdleSync()
            Thread.sleep(300) // let the initial 150 ms re-centre animation settle

            var viewportH = 0
            scenario.onActivity { viewportH = sv!!.viewportHeight() }

            repeat(30) { i ->
                scenario.onActivity {
                    val ic = et!!.onCreateInputConnection(EditorInfo())
                    ic?.commitText("Line $i\n", 1)
                }
                instrumentation.waitForIdleSync()
                Thread.sleep(200)

                scenario.onActivity {
                    val e = et!!
                    val scrollView = sv!!
                    val layout = e.layout!!
                    val caretLine = layout.getLineForOffset(e.selectionEnd)
                    val lineCentre = (layout.getLineTop(caretLine) + layout.getLineBottom(caretLine, false)) / 2
                    val centre = e.top + e.totalPaddingTop + lineCentre - scrollView.scrollY
                    val pitch = (layout.getLineTop(1) - layout.getLineTop(0)).toFloat()
                    val target = 0.45f * viewportH
                    assertThat(centre.toFloat()).isWithin(pitch).of(target)
                }
            }
        }
    }
}
