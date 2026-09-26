package dev.mdwriter.editor

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.debug.EditorPerfActivity
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Random

/**
 * Instrumented: 50 pseudo-random edits (seed 7) on a 100k-char document must leave the live `DynamicLayout` in
 * exactly the state a full reflow would produce — the incremental restyle never desyncs the on-screen layout
 * from a correct from-scratch build (`plans/research/editor-engine.md` §6.12, Acceptance 2).
 */
@RunWith(AndroidJUnit4::class)
class IncrementalLayoutEqualsFullReflowTest {
    private fun waitUntil(
        timeoutMs: Long = 10_000,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(30)
        }
        check(condition()) { "condition not met within ${timeoutMs}ms" }
    }

    private fun awaitIdle(scenario: ActivityScenario<EditorPerfActivity>) {
        waitUntil(timeoutMs = 5_000) {
            var idle = false
            scenario.onActivity { idle = it.controller.isRestyleIdle }
            idle
        }
    }

    @Test
    fun incrementalLayoutEqualsFullReflow() {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), EditorPerfActivity::class.java).apply {
                putExtra("sample", "100k")
                putExtra("perfEdits", 0)
            }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<EditorPerfActivity>(intent).use { scenario ->
            waitUntil {
                var ready = false
                scenario.onActivity {
                    ready =
                        it.controller.editText.layout != null && it.controller.editText.length() > 0
                }
                ready
            }
            instrumentation.waitForIdleSync()

            val rnd = Random(7)
            repeat(50) {
                scenario.onActivity { activity ->
                    val e = activity.controller.editText.text!!
                    val len = e.length
                    val at = if (len > 0) rnd.nextInt(len) else 0
                    when (rnd.nextInt(5)) {
                        0 -> {
                            e.insert(at, "a")
                        }

                        1 -> {
                            e.insert(at, "\n")
                        }

                        2 -> {
                            e.insert(at, "# ")
                        }

                        3 -> {
                            e.insert(at, "**")
                        }

                        else -> {
                            val delLen = (1 + rnd.nextInt(20)).coerceAtMost(len - at)
                            if (delLen > 0) e.delete(at, at + delLen)
                        }
                    }
                }
            }
            awaitIdle(scenario)
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val et = activity.controller.editText
                val layout = et.layout!!
                val n = layout.lineCount
                val starts = IntArray(n) { layout.getLineStart(it) }
                val tops = IntArray(n) { layout.getLineTop(it) }

                et.reflowAll() // forces a full reflow (T06); DynamicLayout mutates itself synchronously

                val l2 = et.layout!!
                assertThat(l2.lineCount).isEqualTo(n)
                for (i in 0 until n) {
                    assertThat(l2.getLineStart(i)).isEqualTo(starts[i])
                    assertThat(l2.getLineTop(i)).isEqualTo(tops[i])
                }
            }
        }
    }
}
