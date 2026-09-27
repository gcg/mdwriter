package dev.mdwriter.ui.gesture

/**
 * Every threshold the swipe recognizer uses (01 §4.8, T13). Empirical starting points from platform §4.2 —
 * tuned on device during this task (see STATUS.md for the final values actually shipped).
 */
object SwipeTuning {
    /** A drag past this many dp, mostly horizontal and under [MAX_COMMIT_MS], commits immediately. */
    const val COMMIT_DP = 56f

    /** Horizontal must be at least this many times the vertical drift to count as "horizontal". */
    const val RATIO = 2.5f

    /** A drag slower than this cannot commit-by-distance (still eligible for a flick on release). */
    const val MAX_COMMIT_MS = 600L

    /** A release velocity at or above this many dp/s can commit even with a short drag. */
    const val FLICK_DP_PER_S = 1000f

    /** A flick still needs at least this much horizontal travel to avoid firing on a stationary tap-release. */
    const val MIN_FLICK_DP = 24f
}
