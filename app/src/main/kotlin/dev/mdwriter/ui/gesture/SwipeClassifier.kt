package dev.mdwriter.ui.gesture

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sign

/** `TowardEnd` = left→right in LTR (right→left in RTL); the direction that opens the library. */
enum class SwipeDir { TowardEnd, TowardStart }

sealed interface SwipeDecision {
    data object Undecided : SwipeDecision

    data object Abort : SwipeDecision

    data class Commit(
        val dir: SwipeDir,
    ) : SwipeDecision
}

/**
 * Pure geometric gesture recognizer for the editor's horizontal swipe navigation (01 §4.8, T13). No Android/Compose
 * types — easy to unit test, and reused verbatim by [Modifier.editorSwipeNav][dev.mdwriter.ui.gesture.editorSwipeNav].
 *
 * A long press that stays still turns into a text selection (never a swipe); a mostly-vertical drag is a scroll;
 * a second pointer aborts (pinch/rotate gestures are never swipe navigation); a fast, short flick at pointer-up can
 * still commit even if the move-phase itself never crossed [SwipeTuning.COMMIT_DP].
 */
class SwipeClassifier(
    private val touchSlopPx: Float,
    private val longPressMs: Long,
    density: Float,
    private val rtl: Boolean,
) {
    private val commitPx = SwipeTuning.COMMIT_DP * density
    private val minFlickPx = SwipeTuning.MIN_FLICK_DP * density
    private val flickPxPerS = SwipeTuning.FLICK_DP_PER_S * density

    private var x0 = 0f
    private var y0 = 0f
    private var t0 = 0L

    fun down(
        x: Float,
        y: Float,
        timeMs: Long,
    ) {
        x0 = x
        y0 = y
        t0 = timeMs
    }

    fun move(
        x: Float,
        y: Float,
        timeMs: Long,
        pressedPointers: Int,
    ): SwipeDecision {
        if (pressedPointers > 1) return SwipeDecision.Abort // pinch / two-finger
        val dx = x - x0
        val dy = y - y0
        val elapsed = timeMs - t0
        val horizontal = abs(dx) >= SwipeTuning.RATIO * abs(dy)
        return when {
            elapsed > longPressMs && hypot(dx, dy) < touchSlopPx -> SwipeDecision.Abort

            // long-press -> selection
            abs(dy) > touchSlopPx && !horizontal -> SwipeDecision.Abort

            // vertical scroll
            abs(dx) >= commitPx && horizontal && elapsed < SwipeTuning.MAX_COMMIT_MS -> SwipeDecision.Commit(dir(dx))

            else -> SwipeDecision.Undecided
        }
    }

    fun up(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
    ): SwipeDecision {
        val dx = x - x0
        val flick =
            abs(vx) >= flickPxPerS && abs(vx) >= SwipeTuning.RATIO * abs(vy) &&
                abs(dx) >= minFlickPx && sign(vx) == sign(dx)
        return if (flick) SwipeDecision.Commit(dir(dx)) else SwipeDecision.Abort
    }

    private fun dir(dx: Float) = if ((dx > 0) != rtl) SwipeDir.TowardEnd else SwipeDir.TowardStart
}
