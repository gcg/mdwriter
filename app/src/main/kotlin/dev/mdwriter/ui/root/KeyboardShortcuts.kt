package dev.mdwriter.ui.root

import android.content.Context
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import dev.mdwriter.R

enum class ShortcutGroup(
    @StringRes val title: Int,
) {
    File(R.string.kbd_file),
    Edit(R.string.kbd_edit),
    Format(R.string.kbd_format),
    View(R.string.kbd_view),
}

/** Who handles it: `MarkdownEditText.onKeyShortcut` (via `EditorShortcuts`) or
 * `MainActivity.onKeyShortcut` (`AppShortcuts`). */
enum class ShortcutTarget { Editor, Activity }

data class ShortcutSpec(
    @StringRes val label: Int,
    val keyCode: Int,
    val meta: Int,
    val group: ShortcutGroup,
    val target: ShortcutTarget,
    /** Heading level for the "Heading %d" label, else null. */
    val labelArg: Int? = null,
)

/** Who handles it: `MarkdownEditText.onKeyShortcut` (via `EditorShortcuts`) or
 * `MainActivity.onKeyShortcut` (`AppShortcuts`). */
object ShortcutCatalog {
    private const val CTRL = KeyEvent.META_CTRL_ON
    private const val CTRL_SHIFT = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON

    val all: List<ShortcutSpec> =
        buildList {
            add(
                ShortcutSpec(
                    R.string.shortcut_new_note,
                    KeyEvent.KEYCODE_N,
                    CTRL,
                    ShortcutGroup.File,
                    ShortcutTarget.Activity,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.shortcut_toggle_library,
                    KeyEvent.KEYCODE_L,
                    CTRL,
                    ShortcutGroup.File,
                    ShortcutTarget.Activity,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.tb_undo,
                    KeyEvent.KEYCODE_Z,
                    CTRL,
                    ShortcutGroup.Edit,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.tb_redo,
                    KeyEvent.KEYCODE_Z,
                    CTRL_SHIFT,
                    ShortcutGroup.Edit,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.shortcut_find,
                    KeyEvent.KEYCODE_F,
                    CTRL,
                    ShortcutGroup.Edit,
                    ShortcutTarget.Activity,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.kbd_bold,
                    KeyEvent.KEYCODE_B,
                    CTRL,
                    ShortcutGroup.Format,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.kbd_italic,
                    KeyEvent.KEYCODE_I,
                    CTRL,
                    ShortcutGroup.Format,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.kbd_link,
                    KeyEvent.KEYCODE_K,
                    CTRL,
                    ShortcutGroup.Format,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.kbd_code,
                    KeyEvent.KEYCODE_C,
                    CTRL_SHIFT,
                    ShortcutGroup.Format,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.kbd_strike,
                    KeyEvent.KEYCODE_X,
                    CTRL_SHIFT,
                    ShortcutGroup.Format,
                    ShortcutTarget.Editor,
                ),
            )
            add(
                ShortcutSpec(
                    R.string.kbd_body_text,
                    KeyEvent.KEYCODE_0,
                    CTRL,
                    ShortcutGroup.Format,
                    ShortcutTarget.Editor,
                ),
            )
            for (level in 1..6) {
                add(
                    ShortcutSpec(
                        R.string.kbd_heading,
                        KeyEvent.KEYCODE_0 + level,
                        CTRL,
                        ShortcutGroup.Format,
                        ShortcutTarget.Editor,
                        labelArg = level,
                    ),
                )
            }
            add(
                ShortcutSpec(
                    R.string.shortcut_preview,
                    KeyEvent.KEYCODE_R,
                    CTRL,
                    ShortcutGroup.View,
                    ShortcutTarget.Activity,
                ),
            )
        }
}

fun Context.keyboardShortcutGroups(): List<KeyboardShortcutGroup> =
    ShortcutGroup.entries.mapNotNull { g ->
        val items =
            ShortcutCatalog.all.filter { it.group == g }.map {
                val label = it.labelArg?.let { n -> getString(it.label, n) } ?: getString(it.label)
                KeyboardShortcutInfo(label, it.keyCode, it.meta)
            }
        if (items.isEmpty()) null else KeyboardShortcutGroup(getString(g.title), items)
    }
