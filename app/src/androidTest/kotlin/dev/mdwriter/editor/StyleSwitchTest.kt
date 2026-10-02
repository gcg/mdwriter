package dev.mdwriter.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.mdwriter.debug.EditorPerfActivity
import dev.mdwriter.editor.spans.FontSet
import dev.mdwriter.editor.spans.toEditorColors
import dev.mdwriter.ui.theme.DarkWriterColors
import dev.mdwriter.ui.theme.WriterFont
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** T19 Acceptance 5: live style switches keep text, selection, undo history and (roughly) the scroll anchor. */
@RunWith(AndroidJUnit4::class)
class StyleSwitchTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()

    private fun doc() = (1..300).joinToString("\n") { "Line $it of the style switch test document with some words" }

    private fun firstVisibleOffset(a: EditorPerfActivity): Int {
        val et = a.controller.editText
        val lay = et.layout ?: return -1
        val y = (a.controller.scrollView.scrollY - et.totalPaddingTop).coerceAtLeast(0)
        return lay.getLineStart(lay.getLineForVertical(y))
    }

    @Test
    fun styleSwitchesKeepTextCaretScrollAndUndo() {
        val text = doc()
        val caret = text.indexOf("Line 150")
        val scenario = EditorTestHost.launch(text = text, selection = caret)
        scenario.onActivity { a ->
            val et = a.controller.editText
            val line140 = et.layout.getLineTop(139) + et.totalPaddingTop
            a.controller.scrollView.scrollTo(0, line140)
        }
        inst.waitForIdleSync()
        val ic = EditorTestHost.ic(scenario)
        inst.runOnMainSync { ic.commitText("abc", 1) }
        var before = ""
        var selBefore = 0 to 0
        var offsetBefore = 0
        scenario.onActivity { a ->
            before = a.controller.snapshot()
            selBefore = a.controller.editText.selectionStart to a.controller.editText.selectionEnd
            offsetBefore = firstVisibleOffset(a)
        }

        val sizes = mutableMapOf<String, Float>()
        val steps: List<Pair<String, (EditorPerfActivity) -> Unit>> =
            listOf(
                "dark" to { a -> a.controller.style.colors = DarkWriterColors.toEditorColors() },
                "mono" to { a ->
                    a.controller.style.font = WriterFont.Mono
                    a.controller.style.fonts = FontSet.load(a, WriterFont.Mono)
                },
                "xxl" to { a -> a.controller.style.textSizeStep = 5 },
                "xs" to { a -> a.controller.style.textSizeStep = 0 },
            )
        for ((name, change) in steps) {
            scenario.onActivity { a ->
                change(a)
                a.controller.setStyle(a.controller.style)
            }
            inst.waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity { a ->
                val et = a.controller.editText
                assertWithMessage(name).that(a.controller.snapshot()).isEqualTo(before)
                assertWithMessage(name).that(et.selectionStart to et.selectionEnd).isEqualTo(selBefore)
                val lineLen = 60
                assertWithMessage(
                    "$name scrollY=${a.controller.scrollView.scrollY} h=${et.height} caretTop=${et.layout.getLineTop(
                        et.layout.getLineForOffset(et.selectionStart),
                    )} off=${firstVisibleOffset(a)} before=$offsetBefore",
                ).that(
                    abs(firstVisibleOffset(a) - offsetBefore),
                ).isAtMost(lineLen)
                sizes[name] = et.textSize
            }
        }
        assertThat(sizes.getValue("xxl") / sizes.getValue("xs")).isWithin(0.16f).of(24f / 15f)
        scenario.onActivity { a -> a.controller.undo() }
        scenario.onActivity { a -> assertThat(a.controller.snapshot()).isEqualTo(text) }
    }
}
