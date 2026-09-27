package dev.mdwriter.ui.gesture

import android.graphics.Point
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.MainActivity
import dev.mdwriter.MdWriterApp
import dev.mdwriter.R
import dev.mdwriter.editor.EditorScrollView
import dev.mdwriter.editor.MarkdownEditText
import dev.mdwriter.ui.gesture.testing.TouchInjector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device swipe-gesture matrix (T13 Acceptance 5), driving the REAL app (`MainActivity`/`MdWriterRoot`, not a
 * debug harness) with real injected [android.view.MotionEvent]s ([TouchInjector]) so `EditText`'s own long-press
 * and "cursor drag from anywhere" timers fire exactly as they would for a real touch.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SwipeNavTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var scrollView: EditorScrollView
    private lateinit var editText: MarkdownEditText
    private var swipeNavigationBefore = true

    @Before
    fun setUp() {
        composeRule.waitForIdle()
        var sv: EditorScrollView? = null
        composeRule.activity.runOnUiThread { sv = findViewByType(composeRule.activity.window.decorView) }
        EditorTestHelpers.waitUntil { sv != null }
        scrollView = checkNotNull(sv)
        editText = scrollView.editText

        val container = (composeRule.activity.application as MdWriterApp).container
        swipeNavigationBefore = runBlocking { container.settings.current().swipeNavigation }
        if (!swipeNavigationBefore) {
            runBlocking { container.settings.update { it.copy(swipeNavigation = true) } }
        }

        composeRule.activity.runOnUiThread {
            editText.readOnly = false
            editText.setText(sampleDocument())
            editText.clearFocus()
            editText.setSelection(0)
        }
        composeRule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        closeDrawerIfOpen()
    }

    @After
    fun tearDown() {
        val container = (composeRule.activity.application as MdWriterApp).container
        runBlocking { container.settings.update { it.copy(swipeNavigation = swipeNavigationBefore) } }
        closeDrawerIfOpen()
    }

    // ---- case 1 -----------------------------------------------------------------------------------------------

    @Test
    fun unfocusedSwipeStartToEndOpensDrawer() {
        composeRule.activity.runOnUiThread { editText.clearFocus() }
        composeRule.waitForIdle()
        swipeStartToEnd()
        assertLibraryVisible()
    }

    // ---- case 2 -----------------------------------------------------------------------------------------------

    @Test
    fun focusedWithImeUpSwipeOpensDrawerHidesImeAndPreservesCaret() {
        composeRule.activity.runOnUiThread {
            editText.requestFocus()
            editText.setSelection(20)
            editText.windowInsetsController?.show(
                android.view.WindowInsets.Type
                    .ime(),
            )
        }
        composeRule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        val caretBefore = editText.selectionStart

        swipeStartToEnd()
        assertLibraryVisible()

        val insets = ViewCompat.getRootWindowInsets(editText)
        assertThat(insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false).isFalse()

        closeDrawerIfOpen()
        composeRule.waitForIdle()
        assertThat(editText.selectionStart).isEqualTo(caretBefore)
    }

    // ---- case 3 -----------------------------------------------------------------------------------------------

    @Test
    fun activeSelectionBlocksSwipe() {
        composeRule.activity.runOnUiThread {
            editText.requestFocus()
            editText.setSelection(5, 15)
        }
        composeRule.waitForIdle()
        swipeStartToEnd()
        assertLibraryNotVisible()
    }

    // ---- case 4 -----------------------------------------------------------------------------------------------

    @Test
    fun longPressThenDragSelectsInsteadOfSwiping() {
        composeRule.activity.runOnUiThread { editText.requestFocus() }
        composeRule.waitForIdle()
        // NOTE: `editText.height` is the height of the WHOLE document (it is a `wrap_content` child of the
        // scrolling `EditorScrollView`, per 01 §4.4) — a real bug found on-device: using a fraction of it here
        // produced an off-screen y coordinate that the system silently dropped (no pointer event ever reached the
        // gesture detector). Use the visible viewport (`scrollView.height`) instead.
        val bounds = screenBounds()
        val x = (bounds.width() * 0.5).toInt()
        val y = (bounds.height() * 0.35).toInt()
        TouchInjector.longPressThenDrag(Point(x, y), dx = dpToPx(150), holdMs = 800)
        composeRule.waitForIdle()
        assertLibraryNotVisible()
        assertThat(editText.hasSelection()).isTrue()
    }

    // ---- case 5 -----------------------------------------------------------------------------------------------

    @Test
    fun verticalDragScrollsInsteadOfSwiping() {
        composeRule.activity.runOnUiThread {
            editText.clearFocus()
            scrollView.scrollTo(0, 0)
        }
        composeRule.waitForIdle()
        val bounds = screenBounds()
        val x = (bounds.width() * 0.5).toInt()
        val yStart = (bounds.height() * 0.70).toInt()
        TouchInjector.verticalDrag(Point(x, yStart), dy = -dpToPx(400), driftX = dpToPx(30), durationMs = 400)
        composeRule.waitForIdle()
        Thread.sleep(SETTLE_MS)
        assertLibraryNotVisible()
        assertThat(scrollView.scrollY).isGreaterThan(0)
    }

    // ---- case 6 -----------------------------------------------------------------------------------------------

    @Test
    fun twoFingerHorizontalSwipeDoesNothing() {
        composeRule.activity.runOnUiThread { editText.clearFocus() }
        composeRule.waitForIdle()
        val bounds = screenBounds()
        val x0 = (bounds.width() * 0.2).toInt()
        val y = (bounds.height() * 0.45).toInt()
        TouchInjector.twoFingerSwipe(Point(x0, y), dx = (bounds.width() * 0.6).toInt())
        composeRule.waitForIdle()
        assertLibraryNotVisible()
    }

    // ---- case 7 -----------------------------------------------------------------------------------------------

    @Test
    fun endToStartSwipeDoesNothingNoPreviewYet() {
        composeRule.activity.runOnUiThread { editText.clearFocus() }
        composeRule.waitForIdle()
        swipeEndToStart()
        assertLibraryNotVisible()
    }

    // ---- case 8 -----------------------------------------------------------------------------------------------

    @Test
    fun swipeNavigationOffDisablesCase1() {
        val container = (composeRule.activity.application as MdWriterApp).container
        runBlocking { container.settings.update { it.copy(swipeNavigation = false) } }
        composeRule.activity.runOnUiThread { editText.clearFocus() }
        composeRule.waitForIdle()
        swipeStartToEnd()
        assertLibraryNotVisible()
        runBlocking { container.settings.update { it.copy(swipeNavigation = true) } }
    }

    // ---- case 9 -----------------------------------------------------------------------------------------------

    @Test
    fun ctrlLOpensDrawerWhenFocusedAndUnfocused() {
        composeRule.activity.runOnUiThread { editText.clearFocus() }
        composeRule.waitForIdle()
        sendCtrlL()
        assertLibraryVisible()
        closeDrawerIfOpen()

        composeRule.activity.runOnUiThread { editText.requestFocus() }
        composeRule.waitForIdle()
        sendCtrlL()
        assertLibraryVisible()
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private fun swipeStartToEnd() {
        val bounds = screenBounds()
        val y = (bounds.height() * 0.45).toInt()
        val from = Point((bounds.width() * 0.2).toInt(), y)
        val to = Point((bounds.width() * 0.8).toInt(), y)
        TouchInjector.swipe(from, to, durationMs = 200)
        composeRule.waitForIdle()
    }

    private fun swipeEndToStart() {
        val bounds = screenBounds()
        val y = (bounds.height() * 0.45).toInt()
        val from = Point((bounds.width() * 0.8).toInt(), y)
        val to = Point((bounds.width() * 0.2).toInt(), y)
        TouchInjector.swipe(from, to, durationMs = 200)
        composeRule.waitForIdle()
    }

    private fun sendCtrlL() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = android.os.SystemClock.uptimeMillis()
        instrumentation.sendKeySync(
            KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON),
        )
        instrumentation.sendKeySync(
            KeyEvent(downTime, downTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_L, 0, KeyEvent.META_CTRL_ON),
        )
        composeRule.waitForIdle()
    }

    // NOTE: the drawer's content (`LibraryDrawer`, "Library" header included) stays COMPOSED at all times inside
    // `ModalNavigationDrawer` — only translated off-screen while closed — so `assertExists()`/`assertDoesNotExist()`
    // alone can NEVER tell open from closed (both would report "exists"). Only `isDisplayed()`/`assertIsDisplayed()`
    // /`assertIsNotDisplayed()` (which check actual on-screen bounds) can. Using the wrong pair here was a real bug
    // found on-device: `closeDrawerIfOpen()` mis-detected "open" every time (drawer or not) and fired a needless
    // back-press with nothing actually open, which took the whole app home per the T13 back-priority contract.
    private fun closeDrawerIfOpen() {
        val libraryTitle = composeRule.activity.getString(R.string.library_title)
        val open = composeRule.onNodeWithText(libraryTitle).isDisplayed()
        if (open) {
            composeRule.activity.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            Thread.sleep(SETTLE_MS)
        }
    }

    private fun assertLibraryVisible() {
        val libraryTitle = composeRule.activity.getString(R.string.library_title)
        composeRule.onNodeWithText(libraryTitle).assertIsDisplayed()
    }

    private fun assertLibraryNotVisible() {
        val libraryTitle = composeRule.activity.getString(R.string.library_title)
        composeRule.onNodeWithText(libraryTitle).assertIsNotDisplayed()
    }

    private fun screenBounds() = composeRule.activity.windowManager.currentWindowMetrics.bounds

    private fun dpToPx(dp: Int): Int = (dp * composeRule.activity.resources.displayMetrics.density).toInt()

    private fun sampleDocument(): String = (1..60).joinToString("\n") { "Line $it of the swipe-nav test document." }

    private fun findViewByType(root: View): EditorScrollView? {
        if (root is EditorScrollView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findViewByType(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private companion object {
        const val SETTLE_MS = 400L
    }
}

/** Tiny local wait helper (mirrors `EditorTestHost.waitUntil`, kept private to this UI-package test so it doesn't
 * need `internal` access into `dev.mdwriter.editor`). */
private object EditorTestHelpers {
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
}
