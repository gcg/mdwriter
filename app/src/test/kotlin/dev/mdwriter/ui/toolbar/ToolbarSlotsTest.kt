package dev.mdwriter.ui.toolbar

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 02 §6: `n = min(9, floor((width - 32 dp) / 48 dp))`, priority order, "More" always last (T09 AC1). */
class ToolbarSlotsTest {
    @Test
    fun `slotCount matches 02 section 6`() {
        assertThat(ToolbarSlots.slotCount(448f)).isEqualTo(8)
        assertThat(ToolbarSlots.slotCount(360f)).isEqualTo(6)
        assertThat(ToolbarSlots.slotCount(464f)).isEqualTo(9)
        assertThat(ToolbarSlots.slotCount(600f)).isEqualTo(9)
        assertThat(ToolbarSlots.slotCount(280f)).isEqualTo(5)
        assertThat(ToolbarSlots.slotCount(100f)).isEqualTo(2)
    }

    @Test
    fun `compute at 448dp shows the full formatting group and all three clipboard buttons`() {
        val l = ToolbarSlots.compute(448f, highlightEnabled = false)
        assertThat(
            l.formatting,
        ).containsExactly(ToolbarItem.Bold, ToolbarItem.Italic, ToolbarItem.Heading, ToolbarItem.Link).inOrder()
        assertThat(l.clipboard).containsExactly(ToolbarItem.Cut, ToolbarItem.Copy, ToolbarItem.Paste).inOrder()
        assertThat(l.overflow).containsExactly(ToolbarItem.Code)
    }

    @Test
    fun `compute at 360dp keeps only Copy visible, overflows Paste, Cut, Code in priority order`() {
        val l = ToolbarSlots.compute(360f, highlightEnabled = false)
        assertThat(
            l.formatting,
        ).containsExactly(ToolbarItem.Bold, ToolbarItem.Italic, ToolbarItem.Heading, ToolbarItem.Link).inOrder()
        assertThat(l.clipboard).containsExactly(ToolbarItem.Copy)
        assertThat(l.overflow).containsExactly(ToolbarItem.Paste, ToolbarItem.Cut, ToolbarItem.Code).inOrder()
    }

    @Test
    fun `compute at 600dp fits everything, overflow empty and Code is in formatting`() {
        val l = ToolbarSlots.compute(600f, highlightEnabled = true)
        assertThat(l.overflow).isEmpty()
        assertThat(l.formatting).contains(ToolbarItem.Code)
    }

    @Test
    fun `MoreEntry Highlight only appears when highlighting is enabled`() {
        assertThat(ToolbarSlots.compute(448f, highlightEnabled = true).more).contains(MoreEntry.Highlight)
        assertThat(ToolbarSlots.compute(448f, highlightEnabled = false).more).doesNotContain(MoreEntry.Highlight)
    }

    @Test
    fun `hasDivider is true only when both groups are non-empty`() {
        assertThat(ToolbarSlots.compute(448f, highlightEnabled = false).hasDivider).isTrue()
    }

    @Test
    fun `buttonCount counts formatting plus clipboard plus one for More`() {
        val l = ToolbarSlots.compute(448f, highlightEnabled = false)
        assertThat(l.buttonCount).isEqualTo(l.formatting.size + l.clipboard.size + 1)
    }
}
