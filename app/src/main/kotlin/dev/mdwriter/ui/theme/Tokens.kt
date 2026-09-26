package dev.mdwriter.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 02 §3 width buckets: phone < 600 dp ≤ medium < 840 dp ≤ expanded. */
enum class WidthClass {
    Compact,
    Medium,
    Expanded,
    ;

    companion object {
        const val MEDIUM_MIN_DP = 600f
        const val EXPANDED_MIN_DP = 840f

        fun fromWidthDp(widthDp: Float): WidthClass =
            when {
                widthDp >= EXPANDED_MIN_DP -> Expanded
                widthDp >= MEDIUM_MIN_DP -> Medium
                else -> Compact
            }
    }
}

/** Editor typography/layout (02 §3). Consumed by T05 (margins, room) and T06 (EditorStyle). */
object EditorMetrics {
    val textSizeStepsSp: List<Int> = listOf(15, 16, 17, 19, 21, 24) // XS S M L XL XXL
    const val DEFAULT_TEXT_SIZE_STEP = 2 // M
    const val LARGE_SCREEN_EXTRA_SP = 1 // +1 sp at >= 600 dp

    fun bodyTextSizeSp(
        step: Int,
        widthClass: WidthClass,
    ): Int =
        textSizeStepsSp[step.coerceIn(textSizeStepsSp.indices)] +
            if (widthClass == WidthClass.Compact) 0 else LARGE_SCREEN_EXTRA_SP

    fun linePitchMultiplier(
        font: WriterFont,
        widthClass: WidthClass,
    ): Float {
        val large = widthClass != WidthClass.Compact
        return when (font) {
            WriterFont.Quattro -> if (large) 1.65f else 1.55f
            WriterFont.Duo, WriterFont.Mono -> if (large) 1.75f else 1.65f
        }
    }

    /** Index = heading level (0 = body). H1 1.60, H2 1.40, H3 1.25, H4 1.10, H5 1.00, H6 1.00. */
    val headingScale: List<Float> = listOf(1.00f, 1.60f, 1.40f, 1.25f, 1.10f, 1.00f, 1.00f)
    const val NATURAL_LINE_BOX_EM = 1.30f // hhea 1025/-275 on UPM 1000
    const val N_WIDTH_EM = 0.6f // measure math: columnPx = measure * 0.6 * textSizePx

    fun sideMarginMin(w: WidthClass): Dp =
        when (w) {
            WidthClass.Compact -> 24.dp
            WidthClass.Medium -> 32.dp
            WidthClass.Expanded -> 48.dp
        }

    fun gutterChars(w: WidthClass): Int =
        when (w) {
            WidthClass.Compact -> 0
            WidthClass.Medium -> 4
            WidthClass.Expanded -> 6
        }

    fun topRoom(w: WidthClass): Dp =
        when (w) {
            WidthClass.Compact -> 56.dp
            WidthClass.Medium -> 64.dp
            WidthClass.Expanded -> 72.dp
        }

    val measureCharsOptions: List<Int> = listOf(64, 72, 80)
    const val DEFAULT_MEASURE_CHARS = 64
    const val BOTTOM_ROOM_FRACTION = 0.50f // of window height
    const val TYPEWRITER_TOP_FRACTION = 0.45f
    const val TYPEWRITER_BOTTOM_FRACTION = 0.55f
    const val TYPEWRITER_CARET_FRACTION = 0.45f // caret line at 45 % of the visible height (02 §5)
    val caretWidth: Dp = 2.dp
    val caretCornerRadius: Dp = 1.dp
}

/** UI chrome dimensions (02 §5–§10). */
object WriterDimens {
    val touchTarget = 48.dp
    val icon = 24.dp

    fun chromeGlyphInset(w: WidthClass): Dp =
        when (w) {
            WidthClass.Compact -> 4.dp
            WidthClass.Medium -> 8.dp
            WidthClass.Expanded -> 12.dp
        }

    val chromeTapZone = 56.dp // tap in the top 56 dp shows the glyphs
    val chromeScrollUpThreshold = 24.dp
    const val STATUS_PROTECTION_ALPHA = 0.94f

    // library drawer (02 §7)
    val drawerMaxWidth = 360.dp
    val drawerEdgeGap = 56.dp // modal width = min(360 dp, screenWidth - 56 dp)
    val permanentPaneWidth = 320.dp
    val drawerHeaderHeight = 56.dp
    val fileRowHeight = 72.dp
    val folderRowHeight = 48.dp
    val locationRowHeight = 44.dp
    val rowPaddingHorizontal = 20.dp
    val folderIcon = 20.dp
    val activeFileBarWidth = 3.dp
    val searchFieldCornerRadius = 20.dp

    // selection pill (02 §6)
    val pillHeight = 48.dp
    val pillButton = 48.dp
    val pillCornerRadius = 24.dp
    val pillGapAboveSelection = 8.dp
    val pillGapBelowSelection = 28.dp // below the selection: leave room for the handles
    val pillScreenPadding = 16.dp // slots n = min(9, floor((width - 2 * 16 dp) / 48 dp))
    const val PILL_MAX_SLOTS = 9

    // menus, sheets, preview (02 §6, §8, §9, §10)
    val menuCornerRadius = 12.dp
    val overflowMenuWidth = 240.dp
    val sheetTopCornerRadius = 20.dp

    fun sheetPadding(w: WidthClass): Dp = if (w == WidthClass.Expanded) 32.dp else 24.dp

    val previewCodePadding = 12.dp
    val previewCodeCornerRadius = 6.dp
    val blockquoteRuleWidth = 2.dp
}

/** Motion (02 §11). Respect "Remove animations": Compose does automatically; Views use ValueAnimator.areAnimatorsEnabled(). */
object WriterMotion {
    const val CHROME_FADE_OUT_MS = 150
    const val CHROME_FADE_IN_MS = 220
    val chromeFadeOutEasing: Easing = LinearOutSlowInEasing
    val chromeFadeInEasing: Easing = FastOutSlowInEasing
    const val CHROME_IDLE_SHOW_DELAY_MS = 1_500L
    const val PILL_IN_MS = 120
    const val PILL_OUT_MS = 90
    const val PILL_IN_SCALE_FROM = 0.96f
    val pillInOffsetY = 8.dp
    const val PILL_RESHOW_DELAY_MS = 150L
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val PREVIEW_TRANSITION_MS = 250
    const val TYPEWRITER_RECENTER_MS = 150
}

/** UI text roles (02 §5, §7; design.md §5.2). Colour is NOT part of the style: pick it from WriterColors. */
@Immutable
data class WriterTypography(
    val body: TextStyle, // 17/28 — placeholder, gallery (the editor itself is a View)
    val drawerTitle: TextStyle, // "Library" 20/28 bold
    val rowTitle: TextStyle, // file/folder row title, settings row label 16/22
    val rowExcerpt: TextStyle, // excerpt, settings value, "Locations", breadcrumb 13/18
    val caption: TextStyle, // relative date 12/16
    val stats: TextStyle, // stats line 12/16, tabular figures
    val menuItem: TextStyle, // menu items 15/20
)

fun writerTypography(family: FontFamily): WriterTypography {
    fun style(
        size: Int,
        lineHeight: Int,
        weight: FontWeight = FontWeight.Normal,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp,
    )
    return WriterTypography(
        body = style(17, 28),
        drawerTitle = style(20, 28, FontWeight.Bold),
        rowTitle = style(16, 22),
        rowExcerpt = style(13, 18),
        caption = style(12, 16),
        stats = style(12, 16).copy(fontFeatureSettings = "tnum"),
        menuItem = style(15, 20),
    )
}

val LocalWriterTypography = staticCompositionLocalOf { writerTypography(FontFamily.Default) }

/** 1 physical pixel (02 §2 "1 px hairlines"; design.md §5.4). */
@Composable
@ReadOnlyComposable
fun hairline(): Dp = with(LocalDensity.current) { 1.toDp() }
