package dev.mdwriter.ui.root

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Acceptance 3. */
class AppShortcutsTest {
    @Test
    fun ctrlNIsNewNote() {
        assertThat(AppShortcuts.map(KeyEvent.KEYCODE_N, ctrl = true)).isEqualTo(AppCommand.NewNote)
    }

    @Test
    fun ctrlOAndCtrlLAreToggleLibrary() {
        assertThat(AppShortcuts.map(KeyEvent.KEYCODE_O, ctrl = true)).isEqualTo(AppCommand.ToggleLibrary)
        assertThat(AppShortcuts.map(KeyEvent.KEYCODE_L, ctrl = true)).isEqualTo(AppCommand.ToggleLibrary)
    }

    @Test
    fun nWithoutCtrlIsNull() {
        assertThat(AppShortcuts.map(KeyEvent.KEYCODE_N, ctrl = false)).isNull()
    }

    @Test
    fun ctrlAltNIsNull() {
        assertThat(AppShortcuts.map(KeyEvent.KEYCODE_N, ctrl = true, alt = true)).isNull()
    }

    @Test
    fun ctrlShiftNIsNull() {
        assertThat(AppShortcuts.map(KeyEvent.KEYCODE_N, ctrl = true, shift = true)).isNull()
    }
}
