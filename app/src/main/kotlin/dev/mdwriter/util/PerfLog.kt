package dev.mdwriter.util

import android.os.SystemClock
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug-only timing markers (logcat tag `MdPerf`); T21 reads them. Events: `open.request`, `open.loadingShown`,
 * `open.installed`, `open.firstFrame`. Disabled (and free) unless [enabled] is set by [dev.mdwriter.MdWriterApp].
 */
object PerfLog {
    const val TAG = "MdPerf"

    @Volatile var enabled: Boolean = false
    private val marks = ConcurrentHashMap<String, Long>()

    fun mark(event: String) {
        if (enabled) marks[event] = SystemClock.elapsedRealtimeNanos()
    }

    /** Logs "span <from>-><event> ms=<x.y>". No-op if disabled or [from] was never marked. */
    fun since(
        from: String,
        event: String,
    ) {
        if (!enabled) return
        val t0 = marks[from] ?: return
        android.util.Log.i(
            TAG,
            String.format(
                Locale.ROOT,
                "span %s->%s ms=%.1f",
                from,
                event,
                (SystemClock.elapsedRealtimeNanos() - t0) / 1e6,
            ),
        )
    }
}
