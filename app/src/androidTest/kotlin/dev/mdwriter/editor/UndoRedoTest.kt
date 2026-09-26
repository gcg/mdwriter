package dev.mdwriter.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented: undo/redo end to end through [MarkdownEditText] and [EditorController] (Acceptance 5). */
@RunWith(AndroidJUnit4::class)
class UndoRedoTest {
    private fun ctrlEvent(keyCode: Int): KeyEvent =
        KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_CTRL_ON)

    private fun ctrlShiftEvent(keyCode: Int): KeyEvent =
        KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

    @Test
    fun typedWordsUndoByWord() {
        val scenario = EditorTestHost.launch(text = "", selection = 0)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity {
            for (c in "one two") ic.commitText(c.toString(), 1)
        }
        scenario.onActivity { activity ->
            assertThat(
                activity.controller.editText.text
                    .toString(),
            ).isEqualTo("one two")
            activity.controller.undo()
            assertThat(
                activity.controller.editText.text
                    .toString(),
            ).isEqualTo("one ")
        }
    }

    @Test
    fun ctrlBThenCtrlZ() {
        val text = "word here"
        val scenario = EditorTestHost.launch(text = text, selection = 0)
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.requestFocus()
            et.setSelection(0, 4) // «word»
            et.onKeyShortcut(KeyEvent.KEYCODE_B, ctrlEvent(KeyEvent.KEYCODE_B))
            assertThat(et.text.toString()).isEqualTo("**word** here")
            assertThat(et.text.toString().substring(et.selectionStart, et.selectionEnd)).isEqualTo("word")

            et.onKeyShortcut(KeyEvent.KEYCODE_Z, ctrlEvent(KeyEvent.KEYCODE_Z))
            assertThat(et.text.toString()).isEqualTo(text)
            assertThat(et.selectionStart).isEqualTo(0)
            assertThat(et.selectionEnd).isEqualTo(4)

            et.onKeyShortcut(KeyEvent.KEYCODE_Z, ctrlShiftEvent(KeyEvent.KEYCODE_Z))
            assertThat(et.text.toString()).isEqualTo("**word** here")
        }
    }

    @Test
    fun canUndoFlowsTrack() {
        val scenario = EditorTestHost.launch(text = "", selection = 0)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { activity ->
            assertThat(activity.controller.canUndo.value).isFalse()
            assertThat(activity.controller.canRedo.value).isFalse()
        }
        scenario.onActivity { ic.commitText("a", 1) }
        scenario.onActivity { activity ->
            assertThat(activity.controller.canUndo.value).isTrue()
            assertThat(activity.controller.canRedo.value).isFalse()
            activity.controller.undo()
            assertThat(activity.controller.canUndo.value).isFalse()
            assertThat(activity.controller.canRedo.value).isTrue()
        }
    }

    @Test
    fun platformUndoRoutesToOurs() {
        val scenario = EditorTestHost.launch(text = "", selection = 0)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity {
            for (c in "one two") ic.commitText(c.toString(), 1)
        }
        scenario.onActivity { activity ->
            val et = activity.controller.editText
            et.onTextContextMenuItem(android.R.id.undo)
            assertThat(et.text.toString()).isEqualTo("one ")
        }
    }

    @Test
    fun pasteIsOneStep() {
        val scenario = EditorTestHost.launch(text = "before ", selection = "before ".length)
        scenario.onActivity { activity ->
            val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
            val cm = ctx.getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText(null, "pasted text"))
            val et = activity.controller.editText
            et.requestFocus()
            et.setSelection(et.length())
            et.onTextContextMenuItem(android.R.id.paste)
            assertThat(et.text.toString()).isEqualTo("before pasted text")
            activity.controller.undo()
            assertThat(et.text.toString()).isEqualTo("before ")
        }
    }

    @Test
    fun installClearsHistory() {
        val scenario = EditorTestHost.launch(text = "", selection = 0)
        val ic = EditorTestHost.ic(scenario)
        scenario.onActivity { ic.commitText("a", 1) }
        scenario.onActivity { activity -> assertThat(activity.controller.canUndo.value).isTrue() }
        scenario.onActivity { activity ->
            runBlocking {
                activity.controller.install(
                    InstallRequest(text = "fresh", selection = 5, scrollY = 0, readOnly = false),
                )
            }
            assertThat(activity.controller.canUndo.value).isFalse()
            assertThat(
                activity.controller.editText.text
                    .toString(),
            ).isEqualTo("fresh")
        }
    }
}
