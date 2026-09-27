package dev.mdwriter.editor

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import dev.mdwriter.editor.spans.EditorStyle
import kotlin.math.abs
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

    /** Width change or settings change ONLY (rule 2) — never on IME show/hide or scroll. [editText.typewriter]
     * (T15) replaces T05's geometry-derived top/bottom padding with the 45 %/55 % typewriter room; this is still
     * the ONE `setPaddingRelative` call site (no new one is added — Acceptance 5). */
    fun applyGeometry() {
        val wm = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val windowHeightPx = wm.bounds.height()
        val top =
            wm.windowInsets
                .getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                .top
        val density = resources.displayMetrics.density
        val g =
            EditorGeometry.compute(
                widthPx = lastWidth,
                windowHeightPx = windowHeightPx,
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
        val (twTop, twBottom) = TypewriterMath.paddings(windowHeightPx)
        val topPad = if (editText.typewriter) twTop else g.paddingTop
        val bottomPad = if (editText.typewriter) twBottom else g.paddingBottom
        with(editText) {
            if (textSize != g.textSizePx) setTextSize(TypedValue.COMPLEX_UNIT_PX, g.textSizePx)
            if (lineSpacingExtra != g.lineSpacingExtraPx || lineSpacingMultiplier != 1f) {
                setLineSpacing(g.lineSpacingExtraPx, 1f)
            }
            if (paddingStart != g.paddingStart || paddingTop != topPad ||
                paddingEnd != g.paddingEnd || paddingBottom != bottomPad
            ) {
                setPaddingRelative(g.paddingStart, topPad, g.paddingEnd, bottomPad)
            }
            caret.halfExtraPx = (g.lineSpacingExtraPx / 2).roundToInt()
            setTextCursorDrawable(caret)
        }
        style.textSizePx = g.textSizePx
        onGeometryChanged?.invoke(g)
    }

    // ---- T15: typewriter scrolling / focus-overlay viewport plumbing ----------------------------------------------

    private var dragging = false
    private var downY = 0f

    /** True while dragging (past touch slop) or while a fling is still settling (01 §10 rule 2/pitfalls: the
     * typewriter animator must never fight a user drag/fling). */
    val isUserScrolling: Boolean get() = dragging || settling
    private var settling = false
    private var scrollAnimator: ValueAnimator? = null
    private val settleCheck = Runnable { settling = false }

    /** A fresh user touch always cancels any in-flight typewriter re-centre animation. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.y
                dragging = false
                cancelTypewriter()
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(ev.y - downY) > ViewConfiguration.get(context).scaledTouchSlop) dragging = true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun fling(velocityY: Int) {
        super.fling(velocityY)
        settling = true
    }

    fun cancelTypewriter() {
        scrollAnimator?.cancel()
        scrollAnimator = null
    }

    /** 150 ms, decelerate (02 §11); jumps straight to [y] if animations are globally disabled. */
    fun animateScrollTo(y: Int) {
        cancelTypewriter()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            scrollTo(0, y)
            return
        }
        scrollAnimator =
            ValueAnimator.ofInt(scrollY, y).apply {
                duration = TypewriterMath.ANIM_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { scrollTo(0, it.animatedValue as Int) }
                start()
            }
    }

    fun viewportHeight() = height - paddingTop - paddingBottom

    fun maxScrollY() = maxOf(0, (getChildAt(0)?.height ?: 0) - viewportHeight())

    /** The band currently on screen, in [editText]'s own coordinate space (`top = scrollY − child.top`) — used by
     * both the typewriter caret target and the focus-overlay dim band. */
    fun childViewport(out: Rect): Rect {
        val childTop = getChildAt(0)?.top ?: 0
        val top = scrollY - childTop
        out.set(0, top, editText.width, top + viewportHeight())
        return out
    }

    override fun onScrollChanged(
        l: Int,
        t: Int,
        oldl: Int,
        oldt: Int,
    ) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t, t - oldt)
        editText.onViewportChanged()
        if (settling) {
            removeCallbacks(settleCheck)
            postDelayed(settleCheck, SETTLE_QUIET_MS)
        }
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

    private companion object {
        const val SETTLE_QUIET_MS = 100L
    }
}

/** Pure typewriter-scroll math (T15, 02 §5/§11): the target scroll `y` that puts a caret line's centre at 45 % of
 * the viewport, and the 45 %/55 % top/bottom padding pair for typewriter mode. JVM-testable (no Android import). */
internal object TypewriterMath {
    const val FRACTION = 0.45f

    // = dev.mdwriter.ui.theme.WriterMotion.TYPEWRITER_RECENTER_MS (no Compose import allowed in this package).
    const val ANIM_MS = 150L

    fun targetScrollY(
        childTop: Int,
        lineCenterInChild: Int,
        viewportH: Int,
        maxScrollY: Int,
    ): Int = (childTop + lineCenterInChild - FRACTION * viewportH).roundToInt().coerceIn(0, maxOf(0, maxScrollY))

    fun paddings(windowH: Int): Pair<Int, Int> = (windowH * 0.45f).roundToInt() to (windowH * 0.55f).roundToInt()
}
