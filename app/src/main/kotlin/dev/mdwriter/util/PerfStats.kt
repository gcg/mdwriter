package dev.mdwriter.util

/** Fixed-capacity sample buffer with percentiles (T21). Pure, so it is JVM-tested. */
class PerfStats(
    private val capacity: Int = 256,
) {
    private val buf = LongArray(capacity)
    private var n = 0

    /** Returns true when the buffer just filled (the caller logs, then [reset]s). */
    fun add(nanos: Long): Boolean {
        buf[n++] = nanos
        return n == capacity
    }

    fun percentileMs(p: Double): Double {
        val s = buf.copyOf(n).also { it.sort() }
        return s[((n - 1) * p).toInt().coerceIn(0, n - 1)] / 1e6
    }

    fun reset() {
        n = 0
    }

    val count: Int get() = n
}
