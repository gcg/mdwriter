package dev.mdwriter.ui.toolbar

import androidx.compose.ui.unit.IntOffset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Pure placement math (T09 AC2): [pillOffset] and [moreMenuOffset] never touch Android/Compose runtime. */
class PillPositionTest {
    @Test
    fun `pillOffset sits above the selection when there is room`() {
        val pos =
            pillOffset(
                selTop = 200f,
                selBottom = 240f,
                selCx = 300f,
                pillW = 400,
                pillH = 48,
                boxW = 1000,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.y).isEqualTo(200 - 8 - 48)
    }

    @Test
    fun `pillOffset flips below when selTop is less than pillH plus gap`() {
        val pos =
            pillOffset(
                selTop = 10f,
                selBottom = 30f,
                selCx = 300f,
                pillW = 400,
                pillH = 48,
                boxW = 1000,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.y).isEqualTo(30 + 28) // selBottom + gapBelow
    }

    @Test
    fun `pillOffset falls back to gapAbove when neither above nor below fits`() {
        // A selection that fills the whole viewport: no room above (selTop=0), no room below (selBottom=boxH).
        val pos =
            pillOffset(
                selTop = 0f,
                selBottom = 800f,
                selCx = 500f,
                pillW = 400,
                pillH = 48,
                boxW = 1000,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.y).isEqualTo(8)
    }

    @Test
    fun `pillOffset x is centred on the selection`() {
        val pos =
            pillOffset(
                selTop = 200f,
                selBottom = 240f,
                selCx = 500f,
                pillW = 400,
                pillH = 48,
                boxW = 1000,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.x).isEqualTo(500 - 400 / 2)
    }

    @Test
    fun `pillOffset x clamps to margin at the left edge`() {
        val pos =
            pillOffset(
                selTop = 200f,
                selBottom = 240f,
                selCx = 10f,
                pillW = 400,
                pillH = 48,
                boxW = 1000,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.x).isEqualTo(16)
    }

    @Test
    fun `pillOffset x clamps to margin at the right edge`() {
        val pos =
            pillOffset(
                selTop = 200f,
                selBottom = 240f,
                selCx = 990f,
                pillW = 400,
                pillH = 48,
                boxW = 1000,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.x).isEqualTo(1000 - 16 - 400)
    }

    @Test
    fun `pillOffset x is centred in a box narrower than the pill plus two margins`() {
        val pos =
            pillOffset(
                selTop = 200f,
                selBottom = 240f,
                selCx = 150f,
                pillW = 400,
                pillH = 48,
                boxW = 300,
                boxH = 800,
                gapAbove = 8,
                gapBelow = 28,
                margin = 16,
            )
        assertThat(pos.x).isEqualTo((300 - 400) / 2)
    }

    @Test
    fun `moreMenuOffset opens above the pill when there is room`() {
        val pos =
            moreMenuOffset(
                pillPos = IntOffset(300, 500),
                pillW = 400,
                pillH = 48,
                menuW = 240,
                menuH = 200,
                boxW = 1000,
                boxH = 800,
                gap = 4,
                margin = 16,
            )
        assertThat(pos.y).isEqualTo(500 - 4 - 200)
    }

    @Test
    fun `moreMenuOffset opens below the pill when there is no room above`() {
        val pos =
            moreMenuOffset(
                pillPos = IntOffset(300, 100),
                pillW = 400,
                pillH = 48,
                menuW = 240,
                menuH = 400,
                boxW = 1000,
                boxH = 800,
                gap = 4,
                margin = 16,
            )
        assertThat(pos.y).isEqualTo(100 + 48 + 4)
    }

    @Test
    fun `moreMenuOffset falls back to gap when neither direction fits`() {
        val pos =
            moreMenuOffset(
                pillPos = IntOffset(300, 10),
                pillW = 400,
                pillH = 48,
                menuW = 240,
                menuH = 790,
                boxW = 1000,
                boxH = 800,
                gap = 4,
                margin = 16,
            )
        assertThat(pos.y).isEqualTo(4)
    }

    @Test
    fun `moreMenuOffset x is end-aligned with the pill and clamped to the margin`() {
        val pos =
            moreMenuOffset(
                pillPos = IntOffset(300, 100),
                pillW = 400,
                pillH = 48,
                menuW = 240,
                menuH = 400,
                boxW = 1000,
                boxH = 800,
                gap = 4,
                margin = 16,
            )
        assertThat(pos.x).isEqualTo(300 + 400 - 240)
    }

    @Test
    fun `moreMenuOffset x clamps to the right margin`() {
        val pos =
            moreMenuOffset(
                pillPos = IntOffset(900, 100),
                pillW = 400,
                pillH = 48,
                menuW = 240,
                menuH = 400,
                boxW = 1000,
                boxH = 800,
                gap = 4,
                margin = 16,
            )
        assertThat(pos.x).isEqualTo(1000 - 16 - 240)
    }
}
