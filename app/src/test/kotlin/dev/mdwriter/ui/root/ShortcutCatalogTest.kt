package dev.mdwriter.ui.root

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ShortcutCatalogTest {
    @Test
    fun noDuplicateKeyAndMeta() {
        val keys = ShortcutCatalog.all.map { it.keyCode to it.meta }
        assertThat(keys).containsNoDuplicates()
    }

    @Test
    fun everyShortcutUsesCtrl() {
        ShortcutCatalog.all.forEach { assertThat(it.meta and KeyEvent.META_CTRL_ON).isNotEqualTo(0) }
    }

    @Test
    fun everyGroupIsUsed() {
        assertThat(ShortcutCatalog.all.map { it.group }.toSet()).containsExactlyElementsIn(ShortcutGroup.entries)
    }

    @Test
    fun activityShortcutsAreMappedByAppShortcuts() {
        ShortcutCatalog.all.filter { it.target == ShortcutTarget.Activity }.forEach {
            val shift = it.meta and KeyEvent.META_SHIFT_ON != 0
            assertThat(AppShortcuts.map(it.keyCode, ctrl = true, shift = shift)).isNotNull()
        }
    }
}
