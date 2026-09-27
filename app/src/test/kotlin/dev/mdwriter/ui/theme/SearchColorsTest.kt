package dev.mdwriter.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.pow

/**
 * T17 step 3: the two new search-highlight tokens ([WriterColors.searchMatch]/[WriterColors.searchMatchFocused])
 * must (a) let body text stay legible over the focused highlight, and (b) look visibly different from the
 * unfocused highlight, in every palette.
 */
class SearchColorsTest {
    private fun srgbToLinear(c: Float): Float = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun relativeLuminance(c: Color): Float =
        0.2126f * srgbToLinear(c.red) + 0.7152f * srgbToLinear(c.green) + 0.0722f * srgbToLinear(c.blue)

    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** Straight-alpha composite of a translucent [fg] over an opaque [bg] (both already `Color`, 0f..1f channels). */
    private fun compositeOver(
        fg: Color,
        bg: Color,
    ): Color {
        val a = fg.alpha
        return Color(
            red = fg.red * a + bg.red * (1f - a),
            green = fg.green * a + bg.green * (1f - a),
            blue = fg.blue * a + bg.blue * (1f - a),
            alpha = 1f,
        )
    }

    private fun check(colors: WriterColors) {
        val focusedOnBg = compositeOver(colors.searchMatchFocused, colors.bg)
        val matchOnBg = compositeOver(colors.searchMatch, colors.bg)
        assertThat(contrastRatio(colors.text, focusedOnBg)).isAtLeast(4.5f)
        assertThat(kotlin.math.abs(relativeLuminance(focusedOnBg) - relativeLuminance(matchOnBg))).isAtLeast(0.03f)
    }

    @Test
    fun `light palette search colours are legible and distinguishable`() = check(LightWriterColors)

    @Test
    fun `dark palette search colours are legible and distinguishable`() = check(DarkWriterColors)

    @Test
    fun `black palette search colours are legible and distinguishable`() = check(BlackWriterColors)
}
