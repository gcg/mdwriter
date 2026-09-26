package dev.mdwriter.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Executable copy of 02-design-spec §2: every token's hex value, for all three palettes. */
class WriterColorsTest {
    private data class Expected(
        val bg: Long,
        val surface: Long,
        val surfaceHover: Long,
        val text: Long,
        val textSecondary: Long,
        val markup: Long,
        val focusDim: Long,
        val accent: Long,
        val selection: Long,
        val codeBg: Long,
        val highlightBg: Long,
        val highlightText: Long,
        val divider: Long,
        val scrim: Long,
        val danger: Long,
    )

    private val light =
        Expected(
            bg = 0xFFF7F7F7,
            surface = 0xFFFCFCFC,
            surfaceHover = 0xFFEFEFEF,
            text = 0xFF1A1A1A,
            textSecondary = 0xFF6E6E6E,
            markup = 0xFF868686,
            focusDim = 0xFFC0C0C0,
            accent = 0xFF00B2FF,
            selection = 0x4000B2FF,
            codeBg = 0xFFEDEDED,
            highlightBg = 0x59FFD900,
            highlightText = 0xFF1A1A1A,
            divider = 0xFFE2E2E2,
            scrim = 0x52000000,
            danger = 0xFFD93A2B,
        )
    private val dark =
        Expected(
            bg = 0xFF1A1A1A,
            surface = 0xFF141414,
            surfaceHover = 0xFF242424,
            text = 0xFFD0D0D0,
            textSecondary = 0xFF8C8C8C,
            markup = 0xFF7A7A7A,
            focusDim = 0xFF5E5E5E,
            accent = 0xFF00B2FF,
            selection = 0x4D00B2FF,
            codeBg = 0xFF242424,
            highlightBg = 0x2DFFD900,
            highlightText = 0xFFDAD094,
            divider = 0xFF2E2E2E,
            scrim = 0x99000000,
            danger = 0xFFFF6B5E,
        )
    private val black =
        Expected(
            bg = 0xFF000000,
            surface = 0xFF0A0A0A,
            surfaceHover = 0xFF161616,
            text = 0xFFC8C8C8,
            textSecondary = 0xFF8A8A8A,
            markup = 0xFF707070,
            focusDim = 0xFF4A4A4A,
            accent = 0xFF00B2FF,
            selection = 0x5900B2FF,
            codeBg = 0xFF141414,
            highlightBg = 0x2DFFD900,
            highlightText = 0xFFDAD094,
            divider = 0xFF1F1F1F,
            scrim = 0xB3000000,
            danger = 0xFFFF6B5E,
        )

    private fun assertMatches(
        colors: WriterColors,
        e: Expected,
    ) {
        assertThat(colors.bg.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.bg)
        assertThat(colors.surface.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.surface)
        assertThat(colors.surfaceHover.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.surfaceHover)
        assertThat(colors.text.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.text)
        assertThat(colors.textSecondary.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.textSecondary)
        assertThat(colors.markup.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.markup)
        assertThat(colors.focusDim.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.focusDim)
        assertThat(colors.accent.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.accent)
        assertThat(colors.selection.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.selection)
        assertThat(colors.codeBg.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.codeBg)
        assertThat(colors.highlightBg.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.highlightBg)
        assertThat(colors.highlightText.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.highlightText)
        assertThat(colors.divider.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.divider)
        assertThat(colors.scrim.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.scrim)
        assertThat(colors.danger.toArgb().toLong() and 0xFFFFFFFFL).isEqualTo(e.danger)
    }

    @Test
    fun `light palette matches 02 spec`() = assertMatches(LightWriterColors, light)

    @Test
    fun `dark palette matches 02 spec`() = assertMatches(DarkWriterColors, dark)

    @Test
    fun `black palette matches 02 spec`() = assertMatches(BlackWriterColors, black)

    @Test
    fun `accent is the same electric blue in every palette`() {
        val expected = Color(0xFF00B2FF).toArgb()
        assertThat(LightWriterColors.accent.toArgb()).isEqualTo(expected)
        assertThat(DarkWriterColors.accent.toArgb()).isEqualTo(expected)
        assertThat(BlackWriterColors.accent.toArgb()).isEqualTo(expected)
    }

    @Test
    fun `light highlightText equals light text`() {
        assertThat(LightWriterColors.highlightText).isEqualTo(LightWriterColors.text)
    }

    @Test
    fun `isDark flags are correct`() {
        assertThat(LightWriterColors.isDark).isFalse()
        assertThat(DarkWriterColors.isDark).isTrue()
        assertThat(BlackWriterColors.isDark).isTrue()
    }

    @Test
    fun `focus overlay alpha matches the formula`() {
        assertThat(LightWriterColors.focusOverlayAlpha).isWithin(0.01f).of(0.75f)
        assertThat(DarkWriterColors.focusOverlayAlpha).isWithin(0.01f).of(0.63f)
        assertThat(BlackWriterColors.focusOverlayAlpha).isWithin(0.01f).of(0.63f)
    }

    @Test
    fun `focus overlay alpha reconstructs focusDim within one 8-bit step`() {
        for (colors in listOf(LightWriterColors, DarkWriterColors, BlackWriterColors)) {
            val a = colors.focusOverlayAlpha
            val reconstructed = colors.text.red + a * (colors.bg.red - colors.text.red)
            assertThat(reconstructed).isWithin(1f / 255f).of(colors.focusDim.red)
        }
    }
}
