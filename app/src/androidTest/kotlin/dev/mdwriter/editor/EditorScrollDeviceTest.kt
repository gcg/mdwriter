package dev.mdwriter.editor

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.debug.EditorPerfActivity
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * Proves the scrolling architecture (01 §4.4, Acceptance 4): the EditText's own `scrollY` never moves — only
 * [EditorScrollView] scrolls — and the 50 % end-of-document scroll room is real EditText padding derived from
 * the window height.
 *
 * Launches the debug-only [EditorPerfActivity] harness (`sample`/`perfEdits` intent extras), not `MainActivity`:
 * `MainActivity` stopped reading a `sample` extra once T11 replaced the old `EditorDemo` composable with the real
 * `MdWriterRoot`/`DocumentSession` flow, which always opens whichever document the session resolves (welcome
 * note / last-open / a new note) — never an arbitrary 100k-char sample. `EditorPerfActivity` is this codebase's
 * established debug-only harness for installing an exact sample document directly on a real
 * [dev.mdwriter.editor.EditorController] (already used by `EditorTestHost`/`SmartEditingTest`/`UndoRedoTest`
 * etc.); `perfEdits = 0` disables its scripted-edit loop so it only installs and sits idle.
 */
@RunWith(AndroidJUnit4::class)
class EditorScrollDeviceTest {
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
    fun scrollingKeepsPaddingBandsDrawnAndEditTextNeverSelfScrolls() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), EditorPerfActivity::class.java).apply {
                putExtra("sample", "100k")
                putExtra("perfEdits", 0)
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<EditorPerfActivity>(intent).use { scenario ->
            var scrollView: EditorScrollView? = null
            var windowHeight = 0
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val sv = findScrollView(activity.window.decorView)
                    if (sv != null && sv.editText.length() > 50_000 && sv.editText.layout != null) {
                        scrollView = sv
                        windowHeight =
                            activity.windowManager.currentWindowMetrics.bounds
                                .height()
                        ready = true
                    }
                }
                ready
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { scrollView!!.fullScroll(View.FOCUS_DOWN) }
            instrumentation.waitForIdleSync()
            Thread.sleep(300)

            scenario.onActivity {
                val sv = scrollView!!
                val et = sv.editText
                assertThat(et.scrollY).isEqualTo(0)
                assertThat(et.paddingBottom).isEqualTo((0.5f * windowHeight).roundToInt())
                val layout = et.layout!!
                val lastLine = layout.lineCount - 1
                val y = sv.textYToViewport(layout.getLineTop(lastLine))
                val frac = y.toFloat() / sv.height
                assertThat(frac in 0.40f..0.60f).isTrue()
            }

            var contentHeight = 0
            scenario.onActivity {
                val et = scrollView!!.editText
                contentHeight = (et.layout?.height ?: 0) + et.totalPaddingTop + et.totalPaddingBottom
            }
            scenario.onActivity { scrollView!!.scrollTo(0, contentHeight / 2) }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertThat(scrollView!!.editText.scrollY).isEqualTo(0)
            }
        }
    }
}
