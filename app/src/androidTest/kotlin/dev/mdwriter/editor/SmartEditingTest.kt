package dev.mdwriter.editor

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented: smart Enter/Backspace/Tab through the real [SmartInputConnection] (soft-keyboard path) and real
 * hardware `KeyEvent`s (`dispatchKeyEvent`, bypasses the `InputConnection` entirely) — Acceptance 4.
 */
@RunWith(AndroidJUnit4::class)
class SmartEditingTest {
    private fun dispatchKey(
        et: MarkdownEditText,
        keyCode: Int,
        metaState: Int = 0,
    ) {
        val down = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, metaState)
        val up = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState)
        et.dispatchKeyEvent(down)
        et.dispatchKeyEvent(up)
    }

    @Test
    fun commitNewlineContinuesList() {
        val scenario = EditorTestHost.launch(text = "- item", selection = "- item".length)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { ic.commitText("\n", 1) }
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            assertThat(et.text.toString()).isEqualTo("- item\n- ")
            assertThat(et.selectionStart).isEqualTo(9)
        }
    }

    @Test
    fun sendKeyEnterRenumbers() {
        val text = "1. one\n2. two"
        val scenario = EditorTestHost.launch(text = text, selection = "1. one".length)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity {
            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0))
            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0))
        }
        scenario.onActivity { activity ->
            assertThat(
                activity.controller.editText.text
                    .toString(),
            ).isEqualTo("1. one\n2. \n3. two")
        }
    }

    @Test
    fun hardwareEnterViaDispatchKeyEvent() {
        val scenario = EditorTestHost.launch(text = "- item", selection = "- item".length)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            dispatchKey(et, KeyEvent.KEYCODE_ENTER)
            assertThat(et.text.toString()).isEqualTo("- item\n- ")
        }
    }

    @Test
    fun enterOnEmptyItemEndsList() {
        val text = "- item\n- "
        val scenario = EditorTestHost.launch(text = text, selection = text.length)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { ic.commitText("\n", 1) }
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            assertThat(et.text.toString()).isEqualTo("- item\n")
            assertThat(et.selectionStart).isEqualTo(7)
        }
    }

    @Test
    fun enterClosesUnclosedFence() {
        val text = "```kotlin"
        val scenario = EditorTestHost.launch(text = text, selection = text.length)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { ic.commitText("\n", 1) }
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            assertThat(et.text.toString()).isEqualTo("```kotlin\n\n```")
            assertThat(et.selectionStart).isEqualTo(10)
        }
    }

    @Test
    fun enterWhileComposingIsPlainNewline() {
        val text = "- item"
        val scenario = EditorTestHost.launch(text = text, selection = text.length)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity {
            ic.setComposingText("foo", 1)
            ic.commitText("\n", 1)
        }
        scenario.onActivity { activity ->
            val out =
                activity.controller.editText.text
                    .toString()
            // Composing suppresses the smart continuation: no new "- " marker is introduced.
            assertThat(out).doesNotContain("- item\n- ")
            assertThat(out).isEqualTo("- item\n")
        }
    }

    @Test
    fun deleteSurroundingAtContentStartRemovesMarker() {
        val text = "- item"
        val scenario = EditorTestHost.launch(text = text, selection = 2)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { ic.deleteSurroundingText(1, 0) }
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            assertThat(et.text.toString()).isEqualTo("item")
            assertThat(et.selectionStart).isEqualTo(0)
        }
    }

    @Test
    fun keyDelSameAsDeleteSurrounding() {
        val text = "- item"
        val scenario = EditorTestHost.launch(text = text, selection = 2)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            dispatchKey(et, KeyEvent.KEYCODE_DEL)
            assertThat(et.text.toString()).isEqualTo("item")
            assertThat(et.selectionStart).isEqualTo(0)
        }
    }

    @Test
    fun tabIndentsAndShiftTabOutdents() {
        // A SECOND list item (with a previous sibling to nest under) — a lone first-ever "- item" indented with
        // no context at all would earn 4 leading spaces and read back as an indented code block, not a nested
        // list item (CommonMark), which isn't what Tab is for; a real second item nests cleanly under the first.
        val text = "- item\n- child"
        val caret = text.indexOf("child")
        val scenario = EditorTestHost.launch(text = text, selection = caret)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            dispatchKey(et, KeyEvent.KEYCODE_TAB)
            assertThat(et.text.toString()).isEqualTo("- item\n  - child")
            dispatchKey(et, KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON)
            assertThat(et.text.toString()).isEqualTo(text)
        }
    }

    @Test
    fun tabOutsideListInsertsTabAndKeepsFocus() {
        val text = "plain paragraph"
        val scenario = EditorTestHost.launch(text = text, selection = 5)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            dispatchKey(et, KeyEvent.KEYCODE_TAB)
            assertThat(et.text.toString()).isEqualTo("plain\t paragraph")
            assertThat(et.hasFocus()).isTrue() // Tab never advances focus (always consumed)
            // Shift+Tab outside a list is a no-op.
            dispatchKey(et, KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_ON)
            assertThat(et.text.toString()).isEqualTo("plain\t paragraph")
        }
    }

    @Test
    fun smartEnterIsOneUndoStep() {
        val text = "- item"
        val scenario = EditorTestHost.launch(text = text, selection = text.length)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { ic.commitText("\n", 1) }
        scenario.onActivity { activity ->
            assertThat(
                activity.controller.editText.text
                    .toString(),
            ).isEqualTo("- item\n- ")
            assertThat(activity.controller.canUndo.value).isTrue()
            activity.controller.undo()
            assertThat(
                activity.controller.editText.text
                    .toString(),
            ).isEqualTo(text)
            assertThat(activity.controller.canUndo.value).isFalse()
        }
    }

    @Test
    fun readOnlyBlocksTyping() {
        val text = "hello"
        val scenario = EditorTestHost.launch(text = text, selection = text.length, readOnly = true)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            ic.commitText("x", 1)
            dispatchKey(et, KeyEvent.KEYCODE_A)
            dispatchKey(et, KeyEvent.KEYCODE_ENTER)
            dispatchKey(et, KeyEvent.KEYCODE_DEL)
            assertThat(et.text.toString()).isEqualTo(text)
        }
    }
}
