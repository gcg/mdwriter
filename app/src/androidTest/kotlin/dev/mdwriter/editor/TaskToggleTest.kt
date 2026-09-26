package dev.mdwriter.editor

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.spans.TaskSpan
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented: tapping a `[ ]`/`[x]` marker toggles the task (Acceptance 6), via real
 * `dispatchTouchEvent(ACTION_DOWN)`/`dispatchTouchEvent(ACTION_UP)` 50 ms apart at the marker's centre. */
@RunWith(AndroidJUnit4::class)
class TaskToggleTest {
    private fun taskCenter(et: MarkdownEditText): Pair<Float, Float> {
        val t = et.text!!
        val span = t.getSpans(0, t.length, TaskSpan::class.java).first()
        val start = t.getSpanStart(span)
        val end = t.getSpanEnd(span)
        val layout = et.layout!!
        val line = layout.getLineForOffset(start)
        val x = et.totalPaddingLeft + (layout.getPrimaryHorizontal(start) + layout.getPrimaryHorizontal(end)) / 2f
        val y = et.totalPaddingTop + (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
        return x to y
    }

    private fun tap(
        et: MarkdownEditText,
        x: Float,
        y: Float,
    ) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
        try {
            et.dispatchTouchEvent(down)
            et.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    @Test
    fun tappingTaskTogglesItCaretUnchangedAndUndoRestores() {
        val text = "- [ ] task"
        val scenario = EditorTestHost.launch(text = text, selection = 3)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            et.setSelection(3)
            val caretBefore = et.selectionStart
            val (x, y) = taskCenter(et)
            tap(et, x, y)
            assertThat(et.text.toString()).isEqualTo("- [x] task")
            assertThat(et.selectionStart).isEqualTo(caretBefore)
            activity.controller.undo()
            assertThat(et.text.toString()).isEqualTo(text)
        }
    }

    @Test
    fun tappingTaskWithCaretOffscreenDoesNotScroll() {
        // A long document: the task marker is on the first line, the caret starts far below, off-screen.
        val sb = StringBuilder("- [ ] task\n")
        repeat(200) { sb.append("filler line\n") }
        val text = sb.toString()
        val scenario = EditorTestHost.launch(text = text, selection = text.length)
        EditorTestHost.awaitIdle(scenario)
        scenario.onActivity { activity ->
            val scrollView = activity.controller.scrollView
            scrollView.scrollTo(0, 0) // the task marker itself is on screen; the caret (line 201) is not
            val et = activity.controller.editText
            val scrollYBefore = scrollView.scrollY
            val (x, y) = taskCenter(et)
            tap(et, x, y)
            assertThat(
                et.text
                    .toString()
                    .lineSequence()
                    .first(),
            ).isEqualTo("- [x] task")
            assertThat(scrollView.scrollY).isEqualTo(scrollYBefore)
        }
    }
}
