package dev.mdwriter.editor

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ScrollView
import dev.mdwriter.editor.spans.EditorStyle
import kotlin.math.roundToInt

/**
 * Hosts [MarkdownEditText] as a `wrap_content` child (01 §4.4): the EditText never scrolls itself, this
 * (a platform `ScrollView`) does. That is what keeps the large top/bottom padding bands (scroll room) fully
 * drawn while scrolling — see [MarkdownEditText]'s KDoc and factcheck A15.
 *
 * [context] must be an Activity context (`currentWindowMetrics` throws `IncorrectContextUseViolation` under
 * StrictMode otherwise, per factcheck).
 */
class EditorScrollView(
    context: Context,
    val editText: MarkdownEditText,
    private val style: EditorStyle,
) : ScrollView(context) {
    init {
        isFillViewport = true
        isVerticalScrollBarEnabled = true
        addView(editText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private var lastWidth = -1

    /** T06 hooks HangRoomSpan/restyle on a geometry change here. */
    var onGeometryChanged: ((EditorGeometry) -> Unit)? = null

    /** T13: emits every scroll (`y`, `dy`) — [EditorController.scrollChanges]. Emit only; never touch padding here
     * (rule 2). */
    var onScrolled: ((y: Int, dy: Int) -> Unit)? = null

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width != lastWidth) {
            lastWidth = width
            applyGeometry()
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Width change or settings change ONLY (rule 2) — never on IME show/hide or scroll. */
    fun applyGeometry() {
        val wm = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val top =
            wm.windowInsets
                .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                .top
        val density = resources.displayMetrics.density
        val g =
            EditorGeometry.compute(
                widthPx = lastWidth,
                windowHeightPx = wm.bounds.height(),
                topInsetPx = top,
                density = density,
                font = style.font,
                textSizeStep = style.textSizeStep,
                measureChars = style.measureChars,
                spToPx = { TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, resources.displayMetrics) },
            )
        style.widthClass = g.widthClass
        style.gutterPx = g.gutterPx
        style.lineSpacingExtraPx = g.lineSpacingExtraPx
        with(editText) {
            if (textSize != g.textSizePx) setTextSize(TypedValue.COMPLEX_UNIT_PX, g.textSizePx)
            if (lineSpacingExtra != g.lineSpacingExtraPx || lineSpacingMultiplier != 1f) {
                setLineSpacing(g.lineSpacingExtraPx, 1f)
            }
            if (paddingStart != g.paddingStart || paddingTop != g.paddingTop ||
                paddingEnd != g.paddingEnd || paddingBottom != g.paddingBottom
            ) {
                setPaddingRelative(g.paddingStart, g.paddingTop, g.paddingEnd, g.paddingBottom)
            }
            caret.halfExtraPx = (g.lineSpacingExtraPx / 2).roundToInt()
            setTextCursorDrawable(caret)
        }
        style.textSizePx = g.textSizePx
        onGeometryChanged?.invoke(g)
    }

    override fun onScrollChanged(
        l: Int,
        t: Int,
        oldl: Int,
        oldt: Int,
    ) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t, t - oldt)
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        // IME opened: keep the caret above it. Padding/geometry are untouched here (rule 2) — imePadding() on
        // the Compose host shrinks this ScrollView's height instead.
        if (h < oldh && editText.isFocused) post { editText.bringPointIntoView(editText.selectionEnd) }
    }

    /** Layout (text) y -> y inside this ScrollView's visible area. */
    fun textYToViewport(textY: Int): Int = editText.top + editText.totalPaddingTop + textY - scrollY

    /** Visible region in Layout coordinates (height excludes the IME: the host has `imePadding()`). */
    fun visibleTextRect(out: Rect = Rect()): Rect {
        val t = scrollY - editText.top - editText.totalPaddingTop
        out.set(0, t, editText.layout?.width ?: 0, t + height)
        return out
    }

    /** Inclusive line range currently visible (clamped), empty if no layout. Used by T07/T15. */
    fun visibleLineRange(): IntRange {
        val l = editText.layout ?: return IntRange.EMPTY
        val r = visibleTextRect()
        return l.getLineForVertical(r.top.coerceAtLeast(0))..l.getLineForVertical(r.bottom.coerceAtLeast(0))
    }
}
