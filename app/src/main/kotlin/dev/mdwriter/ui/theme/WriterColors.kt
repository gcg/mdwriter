package dev.mdwriter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour tokens of 02-design-spec §2. The View-based editor converts them with `toArgb()`. */
@Immutable
data class WriterColors(
    val bg: Color,
    val surface: Color,
    val surfaceHover: Color,
    val text: Color,
    val textSecondary: Color,
    val markup: Color,
    val focusDim: Color,
    val accent: Color,
    val selection: Color,
    val codeBg: Color,
    val highlightBg: Color,
    val highlightText: Color,
    val divider: Color,
    val scrim: Color,
    val danger: Color,
    val isDark: Boolean,
) {
    /** Focus Mode overlay alpha (02 §2): bg drawn at this alpha over text gives focusDim (grey channel). */
    val focusOverlayAlpha: Float get() = (focusDim.red - text.red) / (bg.red - text.red)
}

val LightWriterColors =
    WriterColors(
        bg = Color(0xFFF7F7F7),
        surface = Color(0xFFFCFCFC),
        surfaceHover = Color(0xFFEFEFEF),
        text = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF6E6E6E),
        markup = Color(0xFF868686),
        focusDim = Color(0xFFC0C0C0),
        accent = Color(0xFF00B2FF),
        selection = Color(0x4000B2FF),
        codeBg = Color(0xFFEDEDED),
        highlightBg = Color(0x59FFD900),
        highlightText = Color(0xFF1A1A1A),
        divider = Color(0xFFE2E2E2),
        scrim = Color(0x52000000),
        danger = Color(0xFFD93A2B),
        isDark = false,
    )
val DarkWriterColors =
    WriterColors(
        bg = Color(0xFF1A1A1A),
        surface = Color(0xFF141414),
        surfaceHover = Color(0xFF242424),
        text = Color(0xFFD0D0D0),
        textSecondary = Color(0xFF8C8C8C),
        markup = Color(0xFF7A7A7A),
        focusDim = Color(0xFF5E5E5E),
        accent = Color(0xFF00B2FF),
        selection = Color(0x4D00B2FF),
        codeBg = Color(0xFF242424),
        highlightBg = Color(0x2DFFD900),
        highlightText = Color(0xFFDAD094),
        divider = Color(0xFF2E2E2E),
        scrim = Color(0x99000000),
        danger = Color(0xFFFF6B5E),
        isDark = true,
    )
val BlackWriterColors =
    WriterColors(
        bg = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        surfaceHover = Color(0xFF161616),
        text = Color(0xFFC8C8C8),
        textSecondary = Color(0xFF8A8A8A),
        markup = Color(0xFF707070),
        focusDim = Color(0xFF4A4A4A),
        accent = Color(0xFF00B2FF),
        selection = Color(0x5900B2FF),
        codeBg = Color(0xFF141414),
        highlightBg = Color(0x2DFFD900),
        highlightText = Color(0xFFDAD094),
        divider = Color(0xFF1F1F1F),
        scrim = Color(0xB3000000),
        danger = Color(0xFFFF6B5E),
        isDark = true,
    )

val LocalWriterColors = staticCompositionLocalOf { LightWriterColors }
