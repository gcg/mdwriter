package dev.mdwriter.ui.gesture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Acceptance 1: density 3, slop 24 px, long-press 400 ms (`SwipeClassifier(touchSlopPx = 24f, longPressMs = 400,
 * density = 3f, rtl = ...)`). All distances/velocities below are already in px (the classifier itself never sees
 * dp).
 */
class SwipeClassifierTest {
    private fun classifier(rtl: Boolean = false) =
        SwipeClassifier(touchSlopPx = 24f, longPressMs = 400L, density = 3f, rtl = rtl)

    @Test
    fun fastLtrCommitsTowardEnd() {
        val c = classifier(rtl = false)
        c.down(0f, 0f, 0L)
        val d = c.move(200f, 0f, 200L, 1)
        assertThat(d).isEqualTo(SwipeDecision.Commit(SwipeDir.TowardEnd))
    }

    @Test
    fun sameInputInRtlCommitsTowardStart() {
        val c = classifier(rtl = true)
        c.down(0f, 0f, 0L)
        val d = c.move(200f, 0f, 200L, 1)
        assertThat(d).isEqualTo(SwipeDecision.Commit(SwipeDir.TowardStart))
    }

    @Test
    fun dx168Dy67p2UnderMaxCommitCommits() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.move(168f, 67.2f, 300L, 1)
        assertThat(d).isEqualTo(SwipeDecision.Commit(SwipeDir.TowardEnd))
    }

    @Test
    fun dx200AtT700IsUndecidedThenSlowUpAborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val move = c.move(200f, 0f, 700L, 1)
        assertThat(move).isEqualTo(SwipeDecision.Undecided)
        val up = c.up(200f, 0f, vx = 10f, vy = 0f)
        assertThat(up).isEqualTo(SwipeDecision.Abort)
    }

    @Test
    fun verticalDriftAborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.move(20f, 60f, 100L, 1)
        assertThat(d).isEqualTo(SwipeDecision.Abort)
    }

    @Test
    fun diagonal30DegreesAborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.move(170f, 100f, 100L, 1)
        assertThat(d).isEqualTo(SwipeDecision.Abort)
    }

    @Test
    fun stillFor450MsAborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.move(2f, 1f, 450L, 1)
        assertThat(d).isEqualTo(SwipeDecision.Abort)
    }

    @Test
    fun secondPointerAborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.move(200f, 0f, 100L, 2)
        assertThat(d).isEqualTo(SwipeDecision.Abort)
    }

    @Test
    fun flickVx4000WithDx90Commits() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.up(90f, 0f, vx = 4000f, vy = 0f)
        assertThat(d).isEqualTo(SwipeDecision.Commit(SwipeDir.TowardEnd))
    }

    @Test
    fun flickWithDx50Aborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.up(50f, 0f, vx = 4000f, vy = 0f)
        assertThat(d).isEqualTo(SwipeDecision.Abort)
    }

    @Test
    fun flickWithVelocityOppositeToDxAborts() {
        val c = classifier()
        c.down(0f, 0f, 0L)
        val d = c.up(90f, 0f, vx = -4000f, vy = 0f)
        assertThat(d).isEqualTo(SwipeDecision.Abort)
    }
}
