package dev.mdwriter.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThemeResolutionTest {
    @Test
    fun `Light theme mode always gives the light palette`() {
        for (pureBlack in listOf(false, true)) {
            for (systemDark in listOf(false, true)) {
                assertThat(writerColorsFor(ThemeMode.Light, pureBlack, systemDark)).isEqualTo(LightWriterColors)
            }
        }
    }

    @Test
    fun `Dark theme mode gives dark or black depending on pureBlack`() {
        for (systemDark in listOf(false, true)) {
            assertThat(writerColorsFor(ThemeMode.Dark, pureBlack = false, systemDark = systemDark))
                .isEqualTo(DarkWriterColors)
            assertThat(writerColorsFor(ThemeMode.Dark, pureBlack = true, systemDark = systemDark))
                .isEqualTo(BlackWriterColors)
        }
    }

    @Test
    fun `System theme mode follows systemDark`() {
        assertThat(
            writerColorsFor(ThemeMode.System, pureBlack = false, systemDark = false),
        ).isEqualTo(LightWriterColors)
        assertThat(writerColorsFor(ThemeMode.System, pureBlack = true, systemDark = false)).isEqualTo(LightWriterColors)
        assertThat(writerColorsFor(ThemeMode.System, pureBlack = false, systemDark = true)).isEqualTo(DarkWriterColors)
        assertThat(writerColorsFor(ThemeMode.System, pureBlack = true, systemDark = true)).isEqualTo(BlackWriterColors)
    }

    @Test
    fun `material colour scheme mapping follows 02 section 2`() {
        for (colors in listOf(LightWriterColors, DarkWriterColors, BlackWriterColors)) {
            val scheme = colors.toMaterialColorScheme()
            assertThat(scheme.background).isEqualTo(colors.bg)
            assertThat(scheme.onBackground).isEqualTo(colors.text)
            assertThat(scheme.onSurface).isEqualTo(colors.text)
            assertThat(scheme.surface).isEqualTo(colors.surface)
            assertThat(scheme.surfaceBright).isEqualTo(colors.surface)
            assertThat(scheme.surfaceDim).isEqualTo(colors.surface)
            assertThat(scheme.surfaceContainerLowest).isEqualTo(colors.surface)
            assertThat(scheme.surfaceContainerLow).isEqualTo(colors.surface)
            assertThat(scheme.surfaceContainer).isEqualTo(colors.surface)
            assertThat(scheme.surfaceContainerHigh).isEqualTo(colors.surface)
            assertThat(scheme.surfaceContainerHighest).isEqualTo(colors.surface)
            assertThat(scheme.onSurfaceVariant).isEqualTo(colors.textSecondary)
            assertThat(scheme.outline).isEqualTo(colors.divider)
            assertThat(scheme.outlineVariant).isEqualTo(colors.divider)
            assertThat(scheme.primary).isEqualTo(colors.accent)
            assertThat(scheme.onPrimary).isEqualTo(Color.White)
            assertThat(scheme.secondary).isEqualTo(colors.text)
            assertThat(scheme.scrim).isEqualTo(colors.scrim)
            assertThat(scheme.error).isEqualTo(colors.danger)
            assertThat(scheme.surfaceTint).isEqualTo(Color.Transparent)
        }
    }
}
