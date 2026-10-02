package dev.mdwriter.hardening

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.MainActivity
import dev.mdwriter.editor.EditorTestHost
import dev.mdwriter.ui.root.ShortcutCatalog
import dev.mdwriter.ui.root.ShortcutTarget
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardShortcutsTest {
    @Test
    fun editorShortcutsAreHandled() {
        val scenario = EditorTestHost.launch(text = "scratch", selection = 0)
        scenario.onActivity { a ->
            val et = a.controller.editText
            ShortcutCatalog.all.filter { it.target == ShortcutTarget.Editor }.forEach {
                val ev = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, it.keyCode, 0, it.meta)
                assertThat(et.onKeyShortcut(it.keyCode, ev)).isTrue()
            }
        }
    }

    @Test
    fun providedShortcutsContainEveryCatalogEntry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val data = mutableListOf<KeyboardShortcutGroup>()
                a.onProvideKeyboardShortcuts(data, null, -1)
                val provided = data.flatMap { g -> g.items.map { it.keycode to it.modifiers } }
                ShortcutCatalog.all.forEach { assertThat(provided).contains(it.keyCode to it.meta) }
            }
        }
    }
}
