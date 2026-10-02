package dev.mdwriter.debug

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.Editable
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.doOnNextLayout
import androidx.lifecycle.lifecycleScope
import dev.mdwriter.editor.EditorController
import dev.mdwriter.editor.InstallRequest
import dev.mdwriter.editor.MdEditableFactory
import dev.mdwriter.editor.spans.EditorStyle
import dev.mdwriter.ui.theme.LightWriterColors
import dev.mdwriter.util.Log
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Debug-only perf harness for T07 (01 §9 budgets). Installs one [SampleDocs] sample, then fires a scripted
 * sequence of edits at 250 ms spacing and reports the same "per-keystroke main-thread work" metric the verified
 * bench (`plans/reference/bench/MainActivity.kt`'s `recordEdit`/`report`) used: FrameMetrics
 * `INPUT_HANDLING + ANIMATION + LAYOUT_MEASURE + DRAW`, plus the direct edit-call time, for the first frame after
 * each edit (matched to the edit's own `Choreographer.postFrameCallback` vsync timestamp).
 *
 * Extras: `sample` (`"small"` / `"100k"` / `"300k"`, via [SampleDocs.forExtra]; absent = empty document),
 * `perfEdits` (default 24, `0` = install only, no scripted edits — used by the instrumented tests),
 * `vary` (cycle insert/delete variants instead of a plain `"a"` insert every time), `verifyLayout` (after the
 * scripted edits, compare the live [android.text.Layout] against one produced by [dev.mdwriter.editor.MarkdownEditText.reflowAll]),
 * `mdEditable` (default `true`; `false` disables [MdEditableFactory] for the A/B comparison, Acceptance 7).
 *
 * T08 additions (for `EditorTestHost`'s instrumented tests, which need an arbitrary starting document rather
 * than one of [SampleDocs]'s fixed samples): `text` (a literal string; overrides `sample` when present),
 * `selection` (initial caret offset; default = `text.length`), `readOnly` (default `false`).
 */
class EditorPerfActivity : ComponentActivity() {
    lateinit var controller: EditorController
        private set

    private var extendSelection = false
    private val main = Handler(Looper.getMainLooper())
    private val fmThread = HandlerThread("mdperf-fm").apply { start() }
    private val frames = Collections.synchronizedList(ArrayList<LongArray>())
    private val editDur = ArrayList<Long>()
    private val editVsync = ArrayList<Long>()

    private val fmListener =
        Window.OnFrameMetricsAvailableListener { _, fm, _ ->
            frames.add(
                longArrayOf(
                    fm.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                    fm.getMetric(FrameMetrics.TOTAL_DURATION),
                    fm.getMetric(FrameMetrics.ANIMATION_DURATION),
                    fm.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    fm.getMetric(FrameMetrics.DRAW_DURATION),
                    fm.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
                    fm.getMetric(FrameMetrics.SYNC_DURATION),
                ),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MdEditableFactory.enabled = intent.getBooleanExtra("mdEditable", true) // A/B harness only (Acceptance 7)
        controller = EditorController(this, EditorStyle.create(this, LightWriterColors))
        setContentView(controller.scrollView)
        controller.editText.showSoftInputOnFocus = false

        val sampleExtra = intent.getStringExtra("sample")
        val literalText = intent.getStringExtra("text")
        val text = literalText ?: SampleDocs.forExtra(sampleExtra) ?: ""
        val sizeLabel = sampleExtra ?: "custom"
        // T21: `label` (S1..S5) is appended to the RESULT line; `focus` (off|sentence|paragraph) sets Focus Mode;
        // `extendSelection` (S3) makes every step grow the selection by one char instead of inserting text;
        // `noHang` removes the document-wide gutter span (hang-room cost A/B).
        val scenario = intent.getStringExtra("label")
        val label = if (scenario == null) sizeLabel else "$sizeLabel|label=$scenario"
        extendSelection = intent.getBooleanExtra("extendSelection", false)
        controller.debugNoHangRoom = intent.getBooleanExtra("noHang", false)
        intent.getStringExtra("focus")?.let { f ->
            controller.focusMode =
                dev.mdwriter.editor.FocusModeKind.entries
                    .first { it.name.equals(f, ignoreCase = true) }
        }
        val perfEdits = intent.getIntExtra("perfEdits", DEFAULT_EDITS)
        val vary = intent.getBooleanExtra("vary", false)
        val verifyLayout = intent.getBooleanExtra("verifyLayout", false)
        val selection = intent.getIntExtra("selection", if (literalText != null) text.length else text.length / 2)
        val readOnly = intent.getBooleanExtra("readOnly", false)

        lifecycleScope.launch {
            controller.install(
                InstallRequest(text = text, selection = selection, scrollY = 0, readOnly = readOnly),
            )
            controller.editText.doOnNextLayout {
                val et = controller.editText
                val contentHeight = (et.layout?.height ?: 0) + et.totalPaddingTop + et.totalPaddingBottom
                controller.scrollView.scrollTo(0, contentHeight / 2)
                controller.requestFocus()
                controller.hideIme()
            }
            if (perfEdits > 0) {
                main.postDelayed({ runHarness(label, perfEdits, vary, verifyLayout) }, WARMUP_MS)
            }
        }
    }

    private fun runHarness(
        label: String,
        edits: Int,
        vary: Boolean,
        verifyLayout: Boolean,
    ) {
        window.addOnFrameMetricsAvailableListener(fmListener, Handler(fmThread.looper))
        var n = 0
        val runner =
            object : Runnable {
                override fun run() {
                    if (n < edits) {
                        recordEdit { applyEdit(controller.editText.text!!, n, vary) }
                        n++
                        main.postDelayed(this, EDIT_SPACING_MS)
                    } else {
                        main.postDelayed({ report(label, verifyLayout) }, REPORT_DELAY_MS)
                    }
                }
            }
        main.postDelayed(runner, EDIT_SPACING_MS)
    }

    private fun applyEdit(
        editable: Editable,
        n: Int,
        vary: Boolean,
    ) {
        val mid = editable.length / 2
        if (extendSelection) {
            val et = controller.editText
            if (n == 0) et.setSelection(mid, mid + 1) else et.setSelection(mid, mid + 1 + n)
            return
        }
        if (!vary) {
            editable.insert(mid, "a")
            return
        }
        when (n % 7) {
            0 -> editable.insert(mid, "a")
            1 -> editable.insert(mid, " ")
            2 -> editable.insert(mid, "\n")
            3 -> editable.insert(mid, "# ")
            4 -> editable.insert(mid, "*")
            5 -> editable.insert(mid, "x")
            else -> if (mid > 0) editable.delete(mid - 1, mid)
        }
    }

    private fun recordEdit(block: () -> Unit) {
        val t0 = System.nanoTime()
        block()
        val t1 = System.nanoTime()
        editDur.add(t1 - t0)
        val idx = editVsync.size
        editVsync.add(-1)
        Choreographer.getInstance().postFrameCallback { ft -> editVsync[idx] = ft }
    }

    private fun report(
        label: String,
        verifyLayout: Boolean,
    ) {
        window.removeOnFrameMetricsAvailableListener(fmListener)
        val snapshot = synchronized(frames) { frames.toList() }
        val workMs = ArrayList<Double>()
        val parts = Array(6) { ArrayList<Double>() } // edit, anim, layout, draw, input, sync (ms)
        for (i in editVsync.indices) {
            val v = editVsync[i]
            if (v < 0) continue
            val f = snapshot.minByOrNull { abs(it[0] - v) } ?: continue
            if (abs(f[0] - v) > VSYNC_MATCH_TOLERANCE_NS) continue
            val uiNs = f[2] + f[3] + f[4] + f[5] + f[6]
            workMs.add((uiNs + editDur[i]) / 1_000_000.0)
            parts[0].add(editDur[i] / 1e6)
            parts[1].add(f[2] / 1e6)
            parts[2].add(f[3] / 1e6)
            parts[3].add(f[4] / 1e6)
            parts[4].add(f[5] / 1e6)
            parts[5].add(f[6] / 1e6)
        }
        if (workMs.isNotEmpty()) {
            val names = listOf("edit", "anim", "layout", "draw", "input", "sync")
            Log.i(TAG) {
                "PARTS|$label|" +
                    names.indices.joinToString("|") { i ->
                        names[i] + "=" + "%.2f".format(Locale.ROOT, parts[i].sorted()[parts[i].size / 2])
                    }
            }
            val sorted = workMs.sorted()
            val med = sorted[sorted.size / 2]
            val p90 = sorted[((sorted.size - 1) * 0.9).roundToInt()]
            Log.i(TAG) {
                "RESULT|$label|per-keystroke-main-thread-work|med=" + "%.2f".format(Locale.ROOT, med) +
                    "|p90=" + "%.2f".format(Locale.ROOT, p90) + "|n=${sorted.size}"
            }
        } else {
            Log.i(TAG) { "RESULT|$label|per-keystroke-main-thread-work|n=0" }
        }
        if (verifyLayout) verifyLayoutEqualsFullReflow()
    }

    /** Waits for the restyler to go idle, then compares the live layout against one forced through [reflowAll]. */
    private fun verifyLayoutEqualsFullReflow() {
        if (!controller.isRestyleIdle) {
            main.postDelayed({ verifyLayoutEqualsFullReflow() }, IDLE_POLL_MS)
            return
        }
        val et = controller.editText
        val layout = et.layout
        if (layout == null) {
            Log.i(TAG) { "LAYOUT|equal=false|lines=0" }
            return
        }
        val n = layout.lineCount
        val starts = IntArray(n) { layout.getLineStart(it) }
        val tops = IntArray(n) { layout.getLineTop(it) }
        et.reflowAll()
        val l2 = et.layout!!
        val equal =
            l2.lineCount == n && (0 until n).all { l2.getLineStart(it) == starts[it] && l2.getLineTop(it) == tops[it] }
        Log.i(TAG) { "LAYOUT|equal=$equal|lines=$n" }
    }

    private companion object {
        const val TAG = "MDPERF"
        const val DEFAULT_EDITS = 24
        const val WARMUP_MS = 2500L
        const val EDIT_SPACING_MS = 250L
        const val REPORT_DELAY_MS = 1500L
        const val IDLE_POLL_MS = 50L
        const val VSYNC_MATCH_TOLERANCE_NS = 2_000_000L
    }
}
