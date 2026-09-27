package dev.mdwriter.editor

import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.theme.BlackWriterColors
import dev.mdwriter.ui.theme.DarkWriterColors
import dev.mdwriter.ui.theme.LightWriterColors
import dev.mdwriter.ui.theme.WriterColors
import org.junit.Test

/** Task T15, step 3. Pure-black's alpha computes to 0.63 by the formula, not the design doc's "≈0.61" — the
 * formula wins (recorded as a deviation in STATUS.md). */
class FocusOverlayColorTest {
    @Test
    fun lightPalette() {
        val argb =
            focusOverlayArgb(
                LightWriterColors.bg.toArgb(),
                LightWriterColors.text.toArgb(),
                LightWriterColors.focusDim.toArgb(),
            )
        assertThat(argb).isEqualTo(0xC0F7F7F7.toInt())
    }

    @Test
    fun darkPalette() {
        val argb =
            focusOverlayArgb(
                DarkWriterColors.bg.toArgb(),
                DarkWriterColors.text.toArgb(),
                DarkWriterColors.focusDim.toArgb(),
            )
        assertThat(argb).isEqualTo(0xA01A1A1A.toInt())
    }

    @Test
    fun blackPalette() {
        val argb =
            focusOverlayArgb(
                BlackWriterColors.bg.toArgb(),
                BlackWriterColors.text.toArgb(),
                BlackWriterColors.focusDim.toArgb(),
            )
        assertThat(argb).isEqualTo(0xA1000000.toInt())
    }

    @Test
    fun blendedResultMatchesFocusDimWithinTwo() {
        for (colors in listOf(LightWriterColors, DarkWriterColors, BlackWriterColors)) {
            assertBlendMatchesFocusDim(colors)
        }
    }

    private fun assertBlendMatchesFocusDim(colors: WriterColors) {
        val bg = colors.bg.toArgb()
        val text = colors.text.toArgb()
        val dim = colors.focusDim.toArgb()
        val argb = focusOverlayArgb(bg, text, dim)
        val alpha = ((argb ushr 24) and 0xFF) / 255f

        fun channel(
            c: Int,
            shift: Int,
        ) = (c shr shift) and 0xFF
        for (shift in intArrayOf(16, 8, 0)) {
            val blended = channel(bg, shift) * alpha + channel(text, shift) * (1 - alpha)
            assertThat(Math.abs(blended - channel(dim, shift))).isLessThan(2.01f)
        }
    }
}
