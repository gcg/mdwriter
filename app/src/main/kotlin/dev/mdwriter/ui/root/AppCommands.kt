package dev.mdwriter.ui.root

import android.view.KeyEvent

/** App-level keyboard shortcuts (01 §6.2), dispatched from `MainActivity.onKeyShortcut` into a `commands` flow that
 * `MdWriterRoot` collects. T16 adds `Preview` (Ctrl+R), T17 adds `Find` (Ctrl+F). */
enum class AppCommand { NewNote, ToggleLibrary }

/** Pure key map — no `KeyEvent` instance is ever touched, only its `public static final int` constants (inlined by
 * the Kotlin compiler), so this needs no Robolectric. */
object AppShortcuts {
    fun map(
        keyCode: Int,
        ctrl: Boolean,
        shift: Boolean = false,
        alt: Boolean = false,
    ): AppCommand? {
        if (!ctrl || alt) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_N -> if (shift) null else AppCommand.NewNote
            KeyEvent.KEYCODE_O, KeyEvent.KEYCODE_L -> if (shift) null else AppCommand.ToggleLibrary
            else -> null
        }
    }
}
