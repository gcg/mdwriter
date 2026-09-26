package dev.mdwriter.editor

import dev.mdwriter.ui.theme.EditorMetrics
import dev.mdwriter.ui.theme.WidthClass
import dev.mdwriter.ui.theme.WriterFont
import kotlin.math.roundToInt

/**
 * Pure geometry math for the editor column (02 §3): width class, text size, line pitch, gutter and padding.
 * No `android.*` imports (JVM-testable). [EditorScrollView.applyGeometry] is the only caller in the app; it
 * supplies real Android measurements (window bounds, insets, density, `TypedValue.applyDimension`).
 */
data class EditorGeometry(
    val widthClass: WidthClass,
    val textSizePx: Float,
    val pitchPx: Float,
    val lineSpacingExtraPx: Float,
    val gutterPx: Int,
    val paddingStart: Int,
    val paddingEnd: Int,
    val paddingTop: Int,
    val paddingBottom: Int,
) {
    companion object {
        /**
         * @param widthPx available width (the ScrollView's measured width, i.e. the EditText's width).
         * @param windowHeightPx the **window's** height (not the IME-reduced height) — bottom room must not
         *   change when the keyboard opens.
         * @param topInsetPx status bar + display cutout inset, added to the top room.
         * @param spToPx converts an sp value to px at the current density/font-scale (`TypedValue.applyDimension`).
         */
        fun compute(
            widthPx: Int,
            windowHeightPx: Int,
            topInsetPx: Int,
            density: Float,
            font: WriterFont,
            textSizeStep: Int,
            measureChars: Int,
            spToPx: (Float) -> Float,
        ): EditorGeometry {
            val wc = WidthClass.fromWidthDp(widthPx / density)
            val size = spToPx(EditorMetrics.bodyTextSizeSp(textSizeStep, wc).toFloat())
            val pitch = EditorMetrics.linePitchMultiplier(font, wc) * size
            val extra = (pitch - EditorMetrics.NATURAL_LINE_BOX_EM * size).coerceAtLeast(0f)
            val minSide = EditorMetrics.sideMarginMin(wc).value * density
            val side: Float
            val gutter: Float
            if (wc == WidthClass.Compact) {
                // phones: fill width, no hang (02 §3 "Compact: hang 0")
                side = minSide
                gutter = 0f
            } else {
                val column = measureChars * EditorMetrics.N_WIDTH_EM * size
                side = maxOf(minSide, (widthPx - column) / 2f)
                // the hang only borrows from the start margin, never pushes it negative
                gutter = minOf(EditorMetrics.gutterChars(wc) * EditorMetrics.N_WIDTH_EM * size, side)
            }
            return EditorGeometry(
                widthClass = wc,
                textSizePx = size,
                pitchPx = pitch,
                lineSpacingExtraPx = extra,
                gutterPx = gutter.roundToInt(),
                paddingStart = (side - gutter).roundToInt(),
                paddingEnd = side.roundToInt(),
                paddingTop = topInsetPx + (EditorMetrics.topRoom(wc).value * density).roundToInt(),
                paddingBottom = (EditorMetrics.BOTTOM_ROOM_FRACTION * windowHeightPx).roundToInt(),
            )
        }
    }
}
