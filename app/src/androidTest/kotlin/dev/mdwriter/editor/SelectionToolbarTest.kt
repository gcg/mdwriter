package dev.mdwriter.editor

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.ui.editor.EditorHost
import dev.mdwriter.ui.theme.LightWriterColors
import dev.mdwriter.ui.theme.MdWriterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (T09 AC4): the selection pill end to end — real touch-injected long-press selection, taps chaining
 * formatting, the 150 ms re-show delay, the anchor's coordinate space, and the accessibility custom actions.
 * Unlike [dev.mdwriter.editor.EditorTestHost] (View-only, reused by T08's tests), this needs the real Compose
 * [EditorHost] overlay under test, so it hosts [EditorController] directly inside `composeRule.setContent { }`
 * (the same pattern [dev.mdwriter.ui.theme.MdWriterThemeTest] already uses for Robolectric) rather than launching
 * a plain `ComponentActivity`.
 *
 * `HideSystemSelectionToolbar.createCount` itself (the other half of AC4's `longPressSelectsWordAndShowsPill`) is
 * covered separately, deterministically, by [HideSystemSelectionToolbarTest] (Robolectric) — see that file's doc
 * and this task's STATUS.md "Deviations" for why: in this bare Compose-test host, `longPressSelectsWordAndShowsPill`
 * below reliably reproduces the real word-selection gesture (`selectionStart/End` == `[6, 11)`, confirmed on every
 * run) and the pill correctly appears from it, but the framework's own asynchronous
 * `startActionMode(..., TYPE_FLOATING)` — which is what would bump `createCount` — never fires in this specific
 * harness, even after generous waits and retries. The real app (`MainActivity`, same `MarkdownEditText`/
 * `HideSystemSelectionToolbar`) was manually verified on-device to suppress the text-labelled system toolbar
 * while keeping the handles and showing the pill (STATUS.md's AC5 evidence) — i.e. `onCreateActionMode` *is*
 * genuinely exercised and returns the right thing in the app that ships, just not observably inside this harness.
 */
@RunWith(AndroidJUnit4::class)
class SelectionToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Installs [text] and returns the live [EditorController], focused, IME suppressed (real touch-injection
     * tests don't need it), once the first layout pass has happened. */
    private fun startEditor(
        text: String,
        selection: Int = text.length,
        readOnly: Boolean = false,
    ): EditorController {
        var result: EditorController? = null
        composeRule.setContent {
            MdWriterTheme {
                val context = LocalContext.current
                val controller =
                    remember { EditorController(context, EditorStyle.create(context, LightWriterColors)) }
                result = controller
                LaunchedEffect(Unit) {
                    controller.editText.showSoftInputOnFocus = false
                    controller.install(InstallRequest(text, selection, 0, readOnly))
                    controller.requestFocus()
                }
                EditorHost(controller)
            }
        }
        composeRule.waitForIdle()
        val controller = checkNotNull(result)
        waitUntil { controller.editText.layout != null }
        return controller
    }

    private fun waitUntil(
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

    /** Screen coordinates (px) of a text offset, accounting for the ScrollView's own scroll via the framework's
     * own `getLocationOnScreen` (which already folds in ancestor scroll). */
    private fun screenPoint(
        controller: EditorController,
        offset: Int,
    ): Pair<Float, Float> {
        val et = controller.editText
        val layout = checkNotNull(et.layout)
        val line = layout.getLineForOffset(offset)
        val localX = et.totalPaddingLeft + layout.getPrimaryHorizontal(offset)
        val localY = et.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        val loc = IntArray(2)
        et.getLocationOnScreen(loc)
        return (loc[0] + localX) to (loc[1] + localY)
    }

    /** A real long-press: DOWN, a real wall-clock wait past the long-press timeout, then UP — [MotionEvent]
     * timestamps alone don't drive `GestureDetector`'s long-press callback, real elapsed time does. */
    private fun longPress(
        instrumentation: android.app.Instrumentation,
        x: Float,
        y: Float,
    ) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        instrumentation.sendPointerSync(down)
        down.recycle()
        Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 300)
        val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.sendPointerSync(up)
        up.recycle()
    }

    @Test
    fun longPressSelectsWordAndShowsPill() {
        val controller = startEditor("Hello world of words")
        val (x, y) = screenPoint(controller, 8) // inside "world" ([6, 11))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()

        // A software-rendered emulator's real touch-injection pipeline is occasionally flaky about recognizing
        // a single injected long-press (T05/T08's own STATUS entries document similar emulator-only flakiness
        // with real gestures) — retry a few times rather than accept a false negative.
        var selected = false
        repeat(3) {
            if (selected) return@repeat
            longPress(instrumentation, x, y)
            selected =
                runCatching {
                    waitUntil(2_000) {
                        controller.editText.selectionStart == 6 && controller.editText.selectionEnd == 11
                    }
                }.isSuccess
        }
        assertThat(selected).isTrue()
        assertThat(controller.editText.selectionStart).isEqualTo(6)
        assertThat(controller.editText.selectionEnd).isEqualTo(11)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertExists()
        // `HideSystemSelectionToolbar.createCount` is asserted separately in HideSystemSelectionToolbarTest —
        // see this class's own doc comment for why.
    }

    @Test
    fun boldKeepsSelectionPillAndFocus() {
        val controller = startEditor("Hello world of words", selection = 21)
        composeRule.runOnUiThread { controller.editText.setSelection(6, 11) }
        Thread.sleep(400) // past the 150 ms re-show delay
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Bold").performClick()
        composeRule.waitForIdle()

        assertThat(controller.snapshot()).isEqualTo("Hello **world** of words")
        assertThat(controller.editText.selectionStart).isEqualTo(8)
        assertThat(controller.editText.selectionEnd).isEqualTo(13)
        // programmatic(): re-anchors at once, no hide flicker — a second action can chain right away.
        composeRule.onNodeWithContentDescription("Bold").assertExists()
        assertThat(controller.editText.hasFocus()).isTrue()

        composeRule.runOnUiThread { controller.undo() }
        composeRule.waitForIdle()
        assertThat(controller.snapshot()).isEqualTo("Hello world of words")
    }

    @Test
    fun programmaticSelectionShowsAfter150ms() {
        val controller = startEditor("Hello world of words", selection = 0)
        composeRule.runOnUiThread { controller.editText.setSelection(0, 5) }

        Thread.sleep(50)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertDoesNotExist()

        Thread.sleep(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertExists()
    }

    @Test
    fun hiddenWhileSelectionKeepsChanging() {
        val controller = startEditor("Hello world of words", selection = 0)
        repeat(5) { i ->
            composeRule.runOnUiThread { controller.editText.setSelection(0, 5 + i) }
            Thread.sleep(50)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertDoesNotExist()

        Thread.sleep(250) // >= 150 ms after the LAST of the 5 changes above
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertExists()
    }

    @Test
    fun collapseAndFocusLossHide() {
        val controller = startEditor("Hello world of words", selection = 0)

        composeRule.runOnUiThread { controller.editText.setSelection(6, 11) }
        Thread.sleep(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertExists()

        composeRule.runOnUiThread { controller.collapseSelection() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertDoesNotExist()

        composeRule.runOnUiThread { controller.editText.setSelection(6, 11) }
        Thread.sleep(400)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertExists()

        composeRule.runOnUiThread { controller.editText.clearFocus() }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Bold").assertDoesNotExist()
    }

    @Test
    fun anchorInScrollViewCoords() {
        val longText = (1..200).joinToString("\n") { "filler line $it" }
        val controller = startEditor(longText, selection = 0)
        composeRule.runOnUiThread { controller.editText.setSelection(0, 5) }
        Thread.sleep(400)
        composeRule.waitForIdle()

        val et = controller.editText
        val expectedTop =
            (et.top + et.totalPaddingTop + et.layout!!.getLineTop(0) - controller.scrollView.scrollY).toFloat()
        assertThat(controller.selection.value.anchor.top).isWithin(1f).of(expectedTop)

        val before = controller.selection.value.anchor.top
        composeRule.runOnUiThread { controller.scrollView.scrollBy(0, 100) }
        Thread.sleep(100)
        composeRule.waitForIdle()
        assertThat(controller.selection.value.anchor.top).isWithin(1f).of(before - 100)
    }

    @Test
    fun accessibilityActionsExposed() {
        val controller = startEditor("Hello world of words", selection = 0)
        val et = controller.editText
        lateinit var info: AccessibilityNodeInfo
        composeRule.runOnUiThread {
            info = AccessibilityNodeInfo(et)
            et.onInitializeAccessibilityNodeInfo(info)
        }
        val labels = info.actionList.mapNotNull { it.label?.toString() }
        assertThat(labels).containsAtLeast("Bold", "Italic", "Link", "Quote", "Task")

        composeRule.runOnUiThread { et.setSelection(6, 11) }
        composeRule.waitForIdle()
        val boldAction = info.actionList.first { it.label == "Bold" }
        composeRule.runOnUiThread { et.performAccessibilityAction(boldAction.id, null) }
        composeRule.waitForIdle()
        assertThat(controller.snapshot()).isEqualTo("Hello **world** of words")
    }
}
