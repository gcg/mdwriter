package dev.mdwriter.editor

import android.view.ActionMode
import android.view.View
import android.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Robolectric: direct, deterministic coverage of hard rule 10 (01 §10.10) — the part of T09 AC4's
 * `longPressSelectsWordAndShowsPill` that a real device's asynchronous `startActionMode(..., TYPE_FLOATING)`
 * timing made unreliable to observe end-to-end inside the bare Compose-test host used by
 * [dev.mdwriter.editor.SelectionToolbarTest] (selection itself, and the pill, are covered there; see that file's
 * class doc / STATUS.md's Deviations for why the `createCount` half moved here). A real (non-mock)
 * [PopupMenu]-backed [android.view.Menu] and a minimal fake [ActionMode] stand in for the framework's own,
 * since `HideSystemSelectionToolbar` never calls back into `mode` itself.
 */
@RunWith(AndroidJUnit4::class)
class HideSystemSelectionToolbarTest {
    private fun realMenu(): android.view.Menu {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return PopupMenu(context, View(context)).menu
    }

    private val fakeMode =
        object : ActionMode() {
            override fun finish() = Unit

            override fun getCustomView(): View? = null

            override fun getMenu(): android.view.Menu = realMenu()

            override fun getMenuInflater(): android.view.MenuInflater =
                android.view.MenuInflater(ApplicationProvider.getApplicationContext())

            override fun getSubtitle(): CharSequence? = null

            override fun getTitle(): CharSequence? = null

            override fun invalidate() = Unit

            override fun setCustomView(view: View?) = Unit

            override fun setSubtitle(subtitle: CharSequence?) = Unit

            override fun setSubtitle(resId: Int) = Unit

            override fun setTitle(title: CharSequence?) = Unit

            override fun setTitle(resId: Int) = Unit
        }

    @Test
    fun `onCreateActionMode returns true, clears the menu, and counts the call`() {
        val before = HideSystemSelectionToolbar.createCount
        val menu = realMenu()
        menu.add("Cut")
        menu.add("Copy")
        assertThat(menu.size()).isEqualTo(2)

        val result = HideSystemSelectionToolbar.onCreateActionMode(fakeMode, menu)

        assertThat(result).isTrue() // returning false would kill the selection outright (hard rule 10)
        assertThat(menu.size()).isEqualTo(0)
        assertThat(HideSystemSelectionToolbar.createCount).isEqualTo(before + 1)
    }

    @Test
    fun `onPrepareActionMode also clears the menu (Editor re-adds items around both calls)`() {
        val menu = realMenu()
        menu.add("Select all")

        val result = HideSystemSelectionToolbar.onPrepareActionMode(fakeMode, menu)

        assertThat(result).isTrue()
        assertThat(menu.size()).isEqualTo(0)
    }

    @Test
    fun `onActionItemClicked never handles anything (no items are ever added)`() {
        val menu = realMenu()
        val item = menu.add("anything")
        assertThat(HideSystemSelectionToolbar.onActionItemClicked(fakeMode, item)).isFalse()
    }

    @Test
    fun `onDestroyActionMode is a no-op`() {
        HideSystemSelectionToolbar.onDestroyActionMode(fakeMode) // must not throw
    }
}
