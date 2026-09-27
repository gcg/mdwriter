package dev.mdwriter.ui.preview

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.ui.theme.BlackWriterColors
import dev.mdwriter.ui.theme.DarkWriterColors
import dev.mdwriter.ui.theme.LightWriterColors
import dev.mdwriter.ui.theme.WriterFont
import org.junit.Test

/** Acceptance 4 (first half). */
class PreviewThemeTest {
    private fun build(
        colors: dev.mdwriter.ui.theme.WriterColors = LightWriterColors,
        pureBlack: Boolean = false,
        font: WriterFont = WriterFont.Duo,
        measureChars: Int? = null,
    ) = PreviewThemes.build(
        colors = colors,
        pureBlack = pureBlack,
        font = font,
        bodyCssPx = 17f,
        measureChars = measureChars,
        sideDp = 24f,
        topDp = 56f,
        density = 3f,
    )

    @Test
    fun darkPlusPureBlackIsDarkBlackClassWithOpaqueBlackBg() {
        val theme = build(colors = BlackWriterColors, pureBlack = true)
        assertThat(theme.themeClass).isEqualTo("dark black")
        assertThat(theme.cssVars["--bg"]).isEqualTo("rgba(0,0,0,1.0)")
    }

    @Test
    fun darkWithoutPureBlackIsPlainDark() {
        val theme = build(colors = DarkWriterColors, pureBlack = false)
        assertThat(theme.themeClass).isEqualTo("dark")
    }

    @Test
    fun lightIsLight() {
        val theme = build(colors = LightWriterColors)
        assertThat(theme.themeClass).isEqualTo("light")
    }

    @Test
    fun compactHasNoMeasure() {
        val theme = build(measureChars = null)
        assertThat(theme.cssVars["--measure"]).isEqualTo("none")
    }

    @Test
    fun mediumWithLineLength72Is72ch() {
        val theme = build(measureChars = 72)
        assertThat(theme.cssVars["--measure"]).isEqualTo("72ch")
    }

    @Test
    fun everyCssVarValueIsSafe() {
        val theme = build(colors = BlackWriterColors, pureBlack = true, font = WriterFont.Mono, measureChars = 64)
        theme.cssVars.values.forEach { assertThat(PreviewThemes.isSafeCssValue(it)).isTrue() }
    }

    @Test
    fun aValueContainingSemicolonFailsTheSafetyCheck() {
        assertThat(PreviewThemes.isSafeCssValue("rgba(0,0,0,1);color:red")).isFalse()
    }

    @Test
    fun fontBodyVarMatchesTheSelectedFont() {
        assertThat(build(font = WriterFont.Quattro).cssVars["--font-body"]).isEqualTo("'Quattro'")
        assertThat(build(font = WriterFont.Mono).cssVars["--font-mono"]).isEqualTo("'Mono'")
    }
}
