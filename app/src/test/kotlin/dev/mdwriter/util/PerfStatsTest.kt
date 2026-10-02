package dev.mdwriter.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerfStatsTest {
    private fun filled(): PerfStats {
        val s = PerfStats(capacity = 100)
        for (ms in 1..100) s.add(ms * 1_000_000L)
        return s
    }

    @Test
    fun percentiles() {
        val s = filled()
        assertThat(s.percentileMs(0.5)).isWithin(1.0).of(50.0)
        assertThat(s.percentileMs(0.95)).isWithin(1.0).of(95.0)
        assertThat(s.percentileMs(1.0)).isEqualTo(100.0)
    }

    @Test
    fun addReturnsTrueExactlyAtCapacity() {
        val s = PerfStats(capacity = 3)
        assertThat(s.add(1)).isFalse()
        assertThat(s.add(2)).isFalse()
        assertThat(s.add(3)).isTrue()
    }

    @Test
    fun resetEmptiesTheBuffer() {
        val s = filled()
        s.reset()
        assertThat(s.count).isEqualTo(0)
    }
}
