package dev.mdwriter.editor

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * A 2 dp, rounded caret that spans the **full line pitch**, not just the glyph box (02 §5, factcheck A11).
 * `Editor.updateCursorPosition` sets the drawable's bounds to
 * `(left, lineTop - padding.top, left + intrinsicWidth, lineBottomWithoutSpacing + padding.bottom)`, so returning
 * [getPadding] of `halfExtraPx` on both top and bottom is what stretches the caret to the pitch: half the line's
 * extra leading above the glyph box, half below.
 *
 * Fallback (see STATUS "Deviations" if this misbehaves on device): set [halfExtraPx] to 0 permanently for a
 * glyph-box-only caret. [dev.mdwriter.editor.EditorScrollView.applyGeometry] must call
 * `editText.setTextCursorDrawable(caret)` again after changing [halfExtraPx] (the padding is read once, on bounds
 * assignment, not observed).
 */
class CaretDrawable(
    private val widthPx: Int,
    private val radiusPx: Float,
) : Drawable() {
    var color: Int = 0
        set(v) {
            field = v
            paint.color = v
            invalidateSelf()
        }

    /** = round(lineSpacingExtraPx / 2), updated with geometry. */
    var halfExtraPx: Int = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun getIntrinsicWidth(): Int = widthPx

    override fun getIntrinsicHeight(): Int = -1

    override fun getPadding(padding: Rect): Boolean {
        padding.set(0, halfExtraPx, 0, halfExtraPx)
        return true
    }

    override fun draw(c: Canvas) {
        val b = bounds
        c.drawRoundRect(
            b.left.toFloat(),
            b.top.toFloat(),
            b.right.toFloat(),
            b.bottom.toFloat(),
            radiusPx,
            radiusPx,
            paint,
        )
    }

    override fun setAlpha(a: Int) {
        paint.alpha = a
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.colorFilter = cf
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
