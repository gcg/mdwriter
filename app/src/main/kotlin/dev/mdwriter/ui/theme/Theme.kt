package dev.mdwriter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import dev.mdwriter.util.permitDiskReads

enum class ThemeMode { System, Light, Dark }

fun writerColorsFor(
    themeMode: ThemeMode,
    pureBlack: Boolean,
    systemDark: Boolean,
): WriterColors {
    val dark =
        when (themeMode) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
    return when {
        !dark -> LightWriterColors
        pureBlack -> BlackWriterColors
        else -> DarkWriterColors
    }
}

/** 02 §2. surfaceTint = Transparent makes tonal elevation a no-op; containers the spec doesn't name use surfaceHover. */
fun WriterColors.toMaterialColorScheme(): ColorScheme =
    (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = surfaceHover,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = text,
        onSecondary = bg,
        secondaryContainer = surfaceHover,
        onSecondaryContainer = text,
        tertiary = accent,
        onTertiary = Color.White,
        tertiaryContainer = surfaceHover,
        onTertiaryContainer = text,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surfaceHover,
        onSurfaceVariant = textSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = bg,
        error = danger,
        onError = Color.White,
        errorContainer = surfaceHover,
        onErrorContainer = danger,
        outline = divider,
        outlineVariant = divider,
        scrim = scrim,
        surfaceBright = surface,
        surfaceDim = surface,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface,
    )

fun WriterTypography.toMaterialTypography(): Typography {
    val base = Typography()
    val family = body.fontFamily

    fun TextStyle.inFamily() = copy(fontFamily = family)
    return base.copy(
        displayLarge = base.displayLarge.inFamily(),
        displayMedium = base.displayMedium.inFamily(),
        displaySmall = base.displaySmall.inFamily(),
        headlineLarge = base.headlineLarge.inFamily(),
        headlineMedium = base.headlineMedium.inFamily(),
        headlineSmall = base.headlineSmall.inFamily(),
        titleLarge = drawerTitle,
        titleMedium = base.titleMedium.inFamily(),
        titleSmall = base.titleSmall.inFamily(),
        bodyLarge = rowTitle,
        bodyMedium = rowExcerpt,
        bodySmall = caption,
        labelLarge = menuItem,
        labelMedium = base.labelMedium.inFamily(),
        labelSmall = base.labelSmall.inFamily(),
    )
}

/** Accessors like MaterialTheme: `WriterTheme.colors.text`, `WriterTheme.typography.rowTitle`. */
object WriterTheme {
    val colors: WriterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWriterColors.current
    val typography: WriterTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalWriterTypography.current
}

/** App theme. UI chrome uses the editor's family (02 §1). T19 feeds the three parameters from Settings. */
@Composable
fun MdWriterTheme(
    themeMode: ThemeMode = ThemeMode.System,
    pureBlack: Boolean = false,
    font: WriterFont = WriterFont.Duo,
    content: @Composable () -> Unit,
) {
    // isSystemInDarkTheme() reads LocalConfiguration: it updates on `cmd uimode night` WITHOUT recreation (uiMode is
    // in configChanges). XML theme attributes do NOT update then, so never read colours from resources.
    val colors = writerColorsFor(themeMode, pureBlack, systemDark = isSystemInDarkTheme())
    val colorScheme = remember(colors) { colors.toMaterialColorScheme() }
    // First font-family build reads the bundled TTFs through Resources: a framework-internal disk read (T20).
    val writerType = remember(font) { permitDiskReads { writerTypography(font.fontFamily) } }
    val materialType = remember(writerType) { writerType.toMaterialTypography() }
    val selectionColors =
        remember(colors) {
            TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.selection)
        }
    SystemBarsAppearance(darkIcons = !colors.isDark)
    CompositionLocalProvider(LocalWriterColors provides colors, LocalWriterTypography provides writerType) {
        MaterialTheme(colorScheme = colorScheme, typography = materialType) {
            // inside MaterialTheme: M3 provides its own selection colours, ours must win
            CompositionLocalProvider(
                LocalTextSelectionColors provides selectionColors,
                LocalContentColor provides colors.text,
                content = content,
            )
        }
    }
}
