package dev.mdwriter.editor

import android.content.Intent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.mdwriter.debug.EditorPerfActivity

/**
 * Shared instrumented-test fixture for the editor engine.
 *
 * Deviation from the task's own Reference §G sketch (an `AndroidComposeTestRule<*, ComponentActivity>` +
 * `startEditor` extension): T05–T07 never introduced a Compose test-rule pattern in this codebase (their own
 * `InstallStylingDeviceTest`/`RestyleCorrectnessTest` both launch a plain `ComponentActivity` — either
 * `MainActivity` or the debug-only [EditorPerfActivity] — via `ActivityScenario` and duplicate their own
 * `waitUntil`/`launchEmpty`/`awaitIdle` helpers, which T07's own STATUS entry flagged as ready to hoist here).
 * [EditorPerfActivity] already IS "the helper that exists" the task's file list asks to reuse, so this hoists
 * those three helpers plus an [ic] convenience instead of adding a second, parallel test-activity mechanism.
 */
internal object EditorTestHost {
    /** Launches [EditorPerfActivity] with an arbitrary starting document (`perfEdits = 0`: install only, no
     * scripted-edit harness) and waits until its first layout pass has happened. */
    fun launch(
        text: String = "",
        selection: Int = text.length,
        readOnly: Boolean = false,
    ): ActivityScenario<EditorPerfActivity> {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), EditorPerfActivity::class.java).apply {
                putExtra("text", text)
                putExtra("selection", selection)
                putExtra("readOnly", readOnly)
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

    fun waitUntil(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(20)
        }
        check(condition()) { "condition not met within ${timeoutMs}ms" }
    }

    fun awaitIdle(scenario: ActivityScenario<EditorPerfActivity>) {
        waitUntil(5_000) {
            var idle = false
            scenario.onActivity { idle = it.controller.isRestyleIdle }
            idle
        }
    }

    /** The focused editor's real IC ([MarkdownEditText.onCreateInputConnection] wraps it in a
     * [SmartInputConnection] once [SmartInput] is wired, i.e. always after [EditorController]'s `init`) — the
     * task's own AC4 setup line: "the IC is `editText.onCreateInputConnection(EditorInfo())`, a
     * `SmartInputConnection`". */
    fun ic(scenario: ActivityScenario<EditorPerfActivity>): InputConnection {
        var result: InputConnection? = null
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            result = checkNotNull(et.onCreateInputConnection(EditorInfo())) { "no InputConnection" }
        }
        return checkNotNull(result)
    }
}
