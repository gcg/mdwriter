package dev.mdwriter.editor

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.toolbar.ToolbarAction
import org.junit.Test

/**
 * Pure-JVM test for [EditorShortcuts] (Acceptance 3). Touches only `KeyEvent`'s `public static final int`
 * constants (inlined by the Kotlin compiler), never a live `KeyEvent` instance — no Robolectric needed.
 */
class EditorShortcutsTest {
    private val ctrl = KeyEvent.META_CTRL_ON
    private val ctrlShift = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON

    @Test
    fun ctrlZIsUndo() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Z, ctrl)).isEqualTo(ShortcutCommand.Undo)
    }

    @Test
    fun ctrlShiftZIsRedo() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Z, ctrlShift)).isEqualTo(ShortcutCommand.Redo)
    }

    @Test
    fun ctrlYIsRedo() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Y, ctrl)).isEqualTo(ShortcutCommand.Redo)
    }

    @Test
    fun ctrlShiftYIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Y, ctrlShift)).isNull()
    }

    @Test
    fun ctrlBIsBold() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_B, ctrl))
            .isEqualTo(ShortcutCommand.Action(ToolbarAction.Bold))
    }

    @Test
    fun ctrlShiftBIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_B, ctrlShift)).isNull()
    }

    @Test
    fun ctrlIIsItalic() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_I, ctrl))
            .isEqualTo(ShortcutCommand.Action(ToolbarAction.Italic))
    }

    @Test
    fun ctrlKIsLink() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_K, ctrl))
            .isEqualTo(ShortcutCommand.Action(ToolbarAction.Link))
    }

    @Test
    fun ctrlShiftCIsCode() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_C, ctrlShift))
            .isEqualTo(ShortcutCommand.Action(ToolbarAction.Code))
    }

    @Test
    fun ctrlCPlainIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_C, ctrl)).isNull()
    }

    @Test
    fun ctrlShiftXIsStrike() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_X, ctrlShift))
            .isEqualTo(ShortcutCommand.Action(ToolbarAction.Strike))
    }

    @Test
    fun ctrlXPlainIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_X, ctrl)).isNull()
    }

    @Test
    fun ctrlVIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_V, ctrl)).isNull()
    }

    @Test
    fun ctrlShiftVIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_V, ctrlShift)).isNull()
    }

    @Test
    fun ctrlAIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_A, ctrl)).isNull()
    }

    @Test
    fun ctrlDigitsSetHeading() {
        for (n in 0..6) {
            val keyCode = KeyEvent.KEYCODE_0 + n
            assertThat(EditorShortcuts.map(keyCode, ctrl))
                .isEqualTo(ShortcutCommand.Action(ToolbarAction.SetHeading(n)))
        }
    }

    @Test
    fun ctrlShiftDigitIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_3, ctrlShift)).isNull()
    }

    @Test
    fun altHeldIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Z, ctrl or KeyEvent.META_ALT_ON)).isNull()
    }

    @Test
    fun metaHeldIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Z, ctrl or KeyEvent.META_META_ON)).isNull()
    }

    @Test
    fun noCtrlIsNull() {
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_Z, 0)).isNull()
        assertThat(EditorShortcuts.map(KeyEvent.KEYCODE_B, KeyEvent.META_SHIFT_ON)).isNull()
    }
}
