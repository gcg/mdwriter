package dev.mdwriter.ui.editor

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Acceptance 2 (virtual time). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChromeVisibilityTest {
    @Test
    fun anEditHidesTheChrome() =
        runTest {
            val cv = ChromeVisibility(this)
            cv.onEdit()
            assertThat(cv.visible.value).isFalse()
        }

    @Test
    fun withImeHiddenItReturnsAt1500MsNotAt1499Ms() =
        runTest {
            val cv = ChromeVisibility(this)
            cv.onEdit()
            advanceTimeBy(1499)
            runCurrent()
            assertThat(cv.visible.value).isFalse()
            advanceTimeBy(1)
            runCurrent()
            assertThat(cv.visible.value).isTrue()
        }

    @Test
    fun withImeVisibleItStaysHiddenAfter10sAndAnImeHideShowsIt() =
        runTest {
            val cv = ChromeVisibility(this)
            cv.onImeVisibility(true)
            cv.onEdit()
            advanceTimeBy(10_000)
            runCurrent()
            assertThat(cv.visible.value).isFalse()
            cv.onImeVisibility(false)
            assertThat(cv.visible.value).isTrue()
        }

    @Test
    fun scrollingUp23DpKeepsHiddenAnd24DpShows() =
        runTest {
            val cv = ChromeVisibility(this)
            cv.onEdit()
            cv.onScroll(dyPx = -23f, thresholdPx = 24f)
            assertThat(cv.visible.value).isFalse()
            cv.onScroll(dyPx = -1f, thresholdPx = 24f)
            assertThat(cv.visible.value).isTrue()
        }

    @Test
    fun aDownwardScrollResetsTheAccumulation() =
        runTest {
            val cv = ChromeVisibility(this)
            cv.onEdit()
            cv.onScroll(dyPx = -20f, thresholdPx = 24f)
            cv.onScroll(dyPx = 5f, thresholdPx = 24f) // downward: resets
            cv.onScroll(dyPx = -20f, thresholdPx = 24f) // only 20 px accumulated again, not 40
            assertThat(cv.visible.value).isFalse()
        }

    @Test
    fun aTopTapShowsIt() =
        runTest {
            val cv = ChromeVisibility(this)
            cv.onEdit()
            cv.onTopTap()
            assertThat(cv.visible.value).isTrue()
        }
}
