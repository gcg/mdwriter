package dev.mdwriter.editor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.debug.EditorPerfActivity
import dev.mdwriter.editor.spans.toEditorColors
import dev.mdwriter.ui.theme.DarkWriterColors
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task T15, Acceptance 2/3: Focus Mode's draw-time overlay ([FocusOverlay], hard rule 5 — no spans) actually dims
 * the right pixels on a real device. Reuses [EditorPerfActivity] (T07's debug-only harness, already used by
 * [EditorScrollDeviceTest]/[InstallStylingDeviceTest]) with a literal `text`/`selection` extra rather than hosting
 * a bare [EditorScrollView] directly — a real, already-verified window/controller, not a new harness.
 */
@RunWith(AndroidJUnit4::class)
class FocusOverlayDeviceTest {
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

    private fun launch(
        text: String,
        selection: Int,
    ): ActivityScenario<EditorPerfActivity> {
        val intent =
            Intent(ApplicationProvider.getApplicationContext(), EditorPerfActivity::class.java).apply {
                putExtra("text", text)
                putExtra("selection", selection)
                putExtra("perfEdits", 0)
            }
        return ActivityScenario.launch(intent)
    }

    /** Darkest/brightest red-channel value (0-255) among the pixels between the horizontal position of [start]
     * and [end] on [line], within [totalPaddingLeft]/[totalPaddingTop] of [bmp]'s own coordinate space. */
    private fun scanChannelRange(
        bmp: Bitmap,
        et: MarkdownEditText,
        start: Int,
        end: Int,
        line: Int = 0,
    ): IntRange {
        val layout = et.layout!!
        val x0 =
            (
                minOf(
                    layout.getPrimaryHorizontal(start),
                    layout.getPrimaryHorizontal(end),
                ) + et.totalPaddingLeft
            ).toInt()
        val x1 =
            (
                maxOf(
                    layout.getPrimaryHorizontal(start),
                    layout.getPrimaryHorizontal(end),
                ) + et.totalPaddingLeft
            ).toInt()
        val y0 = layout.getLineTop(line) + et.totalPaddingTop
        val y1 = layout.getLineBottom(line, false) + et.totalPaddingTop
        var min = 255
        var max = 0
        for (x in x0 until x1) {
            for (y in y0 until y1) {
                if (x < 0 || x >= bmp.width || y < 0 || y >= bmp.height) continue
                val r = (bmp.getPixel(x, y) shr 16) and 0xFF
                if (r < min) min = r
                if (r > max) max = r
            }
        }
        return min..max
    }

    @Test
    fun sentenceModeLightPaletteDimsOutsideActiveSentence() {
        launch("One. Two. Three.", selection = 6).use { scenario ->
            var et: MarkdownEditText? = null
            waitUntil {
                scenario.onActivity {
                    et =
                        it.controller.editText.takeIf { e ->
                            e.layout != null
                        }
                }
                et != null
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var bmp: Bitmap
            scenario.onActivity {
                val e = et!!
                e.focusMode = FocusModeKind.Sentence
                bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
                e.draw(Canvas(bmp))

                val one = scanChannelRange(bmp, e, 0, 3)
                val two = scanChannelRange(bmp, e, 5, 8)
                val three = scanChannelRange(bmp, e, 10, 15)
                assertThat(one.first in 0xB4..0xCC).isTrue()
                assertThat(two.first <= 0x40).isTrue()
                assertThat(three.first in 0xB4..0xCC).isTrue()
            }
        }
    }

    @Test
    fun sentenceModeDarkPaletteDimsOutsideActiveSentence() {
        launch("One. Two. Three.", selection = 6).use { scenario ->
            var et: MarkdownEditText? = null
            waitUntil {
                scenario.onActivity {
                    et =
                        it.controller.editText.takeIf { e ->
                            e.layout != null
                        }
                }
                et != null
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity {
                val controller = it.controller
                controller.style.colors = DarkWriterColors.toEditorColors()
                controller.setStyle(controller.style)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var bmp: Bitmap
            scenario.onActivity {
                val e = et!!
                e.focusMode = FocusModeKind.Sentence
                bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
                e.draw(Canvas(bmp))

                val one = scanChannelRange(bmp, e, 0, 3)
                val two = scanChannelRange(bmp, e, 5, 8)
                assertThat(one.last in 0x52..0x6A).isTrue()
                assertThat(two.last >= 0xB8).isTrue()
            }
        }
    }

    @Test
    fun sentenceModeWrappedParagraphLightsOnlyTheActiveSentence() {
        val text =
            "mdwriter is a quiet place to write. There is no Save button: every word is saved as " +
                "you type, as a plain Markdown file on this phone."
        val firstSentenceEnd = text.indexOf(". ") + 1 // just past "mdwriter is a quiet place to write."
        launch(text, selection = 5).use { scenario ->
            var et: MarkdownEditText? = null
            waitUntil {
                scenario.onActivity { et = it.controller.editText.takeIf { e -> e.layout != null } }
                et != null
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var bmp: Bitmap
            var firstSentenceLine = -1
            var laterLine = -1
            var laterStart = -1
            var laterEnd = -1
            scenario.onActivity {
                val e = et!!
                e.focusMode = FocusModeKind.Sentence
                bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
                e.draw(Canvas(bmp))

                val layout = e.layout!!
                firstSentenceLine = layout.getLineForOffset(2)
                val savedOffset = text.indexOf("saved as you")
                laterLine = layout.getLineForOffset(savedOffset)
                laterStart = savedOffset
                laterEnd = savedOffset + "saved".length
                check(firstSentenceLine != laterLine) { "test text did not wrap as expected" }
            }
            scenario.onActivity {
                val e = et!!
                val active = scanChannelRange(bmp, e, 2, minOf(8, firstSentenceEnd), line = firstSentenceLine)
                val later = scanChannelRange(bmp, e, laterStart, laterEnd, line = laterLine)
                assertThat(active.first <= 0x40).isTrue()
                assertThat(later.first in 0xB4..0xCC).isTrue()
            }
        }
    }

    @Test
    fun sentenceModeStillCorrectWhenScrolled() {
        val filler = (1..40).joinToString("\n") { "Filler line $it" }
        val target =
            "mdwriter is a quiet place to write. There is no Save button: every word is saved as " +
                "you type, as a plain Markdown file on this phone."
        val text = "$filler\n\n$target"
        val targetStart = text.indexOf(target)
        launch(text, selection = targetStart + 5).use { scenario ->
            var et: MarkdownEditText? = null
            var sv: EditorScrollView? = null
            waitUntil {
                scenario.onActivity {
                    val e = it.controller.editText
                    if (e.layout != null) {
                        et = e
                        sv = it.controller.scrollView
                    }
                }
                et != null
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var bmp: Bitmap
            var firstSentenceLine = -1
            var laterLine = -1
            var laterStart = -1
            var laterEnd = -1
            scenario.onActivity {
                val e = et!!
                val scrollView = sv!!
                val layout = e.layout!!
                // Scroll so the target paragraph's first line sits roughly in the middle of the viewport —
                // neither at the very top nor requiring the whole document to be scanned from y=0.
                val firstLine = layout.getLineForOffset(targetStart)
                val targetY = e.top + e.totalPaddingTop + layout.getLineTop(firstLine)
                scrollView.scrollTo(0, (targetY - scrollView.viewportHeight() / 2).coerceAtLeast(0))
                e.focusMode = FocusModeKind.Sentence
                bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
                e.draw(Canvas(bmp))

                firstSentenceLine = layout.getLineForOffset(targetStart + 2)
                val savedOffset = text.indexOf("saved as you")
                laterLine = layout.getLineForOffset(savedOffset)
                laterStart = savedOffset
                laterEnd = savedOffset + "saved".length
                check(firstSentenceLine != laterLine) { "test text did not wrap as expected" }
            }
            scenario.onActivity {
                val e = et!!
                val active = scanChannelRange(bmp, e, targetStart + 2, targetStart + 8, line = firstSentenceLine)
                val later = scanChannelRange(bmp, e, laterStart, laterEnd, line = laterLine)
                assertThat(active.first <= 0x40).isTrue()
                assertThat(later.first in 0xB4..0xCC).isTrue()
            }
        }
    }

    @Test
    fun paragraphModeDimsOtherLinesOnly() {
        launch("a\nbb", selection = 3).use { scenario ->
            var et: MarkdownEditText? = null
            waitUntil {
                scenario.onActivity {
                    et =
                        it.controller.editText.takeIf { e ->
                            e.layout != null
                        }
                }
                et != null
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            lateinit var bmp: Bitmap
            scenario.onActivity {
                val e = et!!
                e.focusMode = FocusModeKind.Paragraph
                bmp = Bitmap.createBitmap(e.width, e.height, Bitmap.Config.ARGB_8888)
                e.draw(Canvas(bmp))

                val line1 = scanChannelRange(bmp, e, 0, 1, line = 0) // "a" — dimmed
                val line2 = scanChannelRange(bmp, e, 2, 4, line = 1) // "bb" — the caret's own line, undimmed
                assertThat(line1.first in 0xB4..0xCC).isTrue()
                assertThat(line2.first <= 0x40).isTrue()
            }
        }
    }
}
