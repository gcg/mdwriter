package dev.mdwriter.debug

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import dev.mdwriter.util.Log
import java.util.Locale

/**
 * Debug-only per-frame main-thread work logger (09 §9 perf budgets). Listens on its own `HandlerThread` (frame
 * metrics callbacks must not run on the UI thread — see `Window.addOnFrameMetricsAvailableListener`'s contract).
 *
 * `work = INPUT_HANDLING + ANIMATION + LAYOUT_MEASURE + DRAW` (ns -> ms), the same formula the verified bench
 * (`plans/reference/bench/MainActivity.kt`'s `recordEdit`/`report`) uses for "per-keystroke main-thread work",
 * minus its own edit-call timing (there is no automated edit harness here — a human or `adb shell input text`
 * drives real keystrokes). Only frames that did real input/layout work are counted; every 20 such frames a
 * median/p90 summary is logged. T07 (incremental restyle) may reuse this class as-is.
 */
internal class FrameWorkLogger(
    private val window: Window,
) {
    private val thread = HandlerThread("mdframes").apply { start() }
    private val batch = ArrayList<Double>()

    private val listener =
        Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
            val input = frameMetrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
            val anim = frameMetrics.getMetric(FrameMetrics.ANIMATION_DURATION)
            val layout = frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
            val draw = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
            if (layout > 0 || input > 0) {
                val workMs = (input + anim + layout + draw) / 1_000_000.0
                Log.i(TAG) { "FRAME|work=" + "%.2f".format(Locale.ROOT, workMs) }
                batch.add(workMs)
                if (batch.size >= 20) {
                    val sorted = batch.sorted()
                    val med = sorted[sorted.size / 2]
                    val p90Index = (sorted.size * 0.9).toInt().coerceAtMost(sorted.size - 1)
                    val p90 = sorted[p90Index]
                    Log.i(TAG) {
                        "RESULT|frames|plain-typing|med=" + "%.2f".format(Locale.ROOT, med) +
                            "|p90=" + "%.2f".format(Locale.ROOT, p90)
                    }
                    batch.clear()
                }
            }
        }

    init {
        window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
    }

    fun stop() {
        window.removeOnFrameMetricsAvailableListener(listener)
        thread.quitSafely()
    }

    private companion object {
        const val TAG = "MDPERF"
    }
}
