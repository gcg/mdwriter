package dev.bench.mdtext

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.DynamicLayout
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Log
import android.util.TypedValue
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.ExpandPolicy
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Collections
import kotlin.math.roundToInt

const val TAG = "MDBENCH"

fun stats(label: String, ns: List<Long>) {
    if (ns.isEmpty()) { Log.i(TAG, "$label n=0"); return }
    val s = ns.sorted().map { it / 1_000_000.0 }
    val med = s[s.size / 2]
    val p90 = s[((s.size - 1) * 0.9).roundToInt()]
    Log.i(TAG, "RESULT|$label|n=${s.size}|min=%.2f|med=%.2f|p90=%.2f|max=%.2f".format(s.first(), med, p90, s.last()))
}

// ---------- Compose styles ----------
object CStyles {
    val mark = SpanStyle(color = Color(0xFFB4B4B4))
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val ital = SpanStyle(fontStyle = FontStyle.Italic)
    val code = SpanStyle(fontFamily = FontFamily.Monospace)
    val quote = SpanStyle(color = Color(0xFF6A6A6A))
    val url = SpanStyle(color = Color(0xFF9A9A9A))
    val h = listOf(28, 28, 24, 21, 19, 19, 19).map { SpanStyle(fontSize = it.sp, fontWeight = FontWeight.Bold) }
    val hPara = ParagraphStyle(lineHeight = 1.25.em)

    fun forKind(k: Int, level: Int): SpanStyle = when (k) {
        K_H -> h[level]
        K_MARK -> mark
        K_BOLD -> bold
        K_ITAL -> ital
        K_CODE, K_FENCE -> code
        K_QUOTE -> quote
        else -> url
    }

    fun annotated(text: String, sp: Spans?, para: Boolean): AnnotatedString {
        val b = AnnotatedString.Builder(text.length)
        b.append(text)
        if (sp != null) for (i in 0 until sp.size) {
            b.addStyle(forKind(sp.kind[i], sp.level[i]), sp.start[i], sp.end[i])
            if (para && sp.kind[i] == K_H) b.addStyle(hPara, sp.start[i], sp.end[i])
        }
        return b.toAnnotatedString()
    }
}

// ---------- Android spans ----------
interface MdSpan
class HangSpan(private val w: Int) : android.text.style.LeadingMarginSpan, android.text.style.UpdateLayout {
    override fun getLeadingMargin(first: Boolean): Int = if (first) -w else 0
    override fun drawLeadingMargin(c: android.graphics.Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: android.text.Layout?) {}
}
interface SpanSink { fun put(o: Any, st: Int, en: Int) }
class MdSize(px: Int) : AbsoluteSizeSpan(px), MdSpan
class MdStyle(s: Int) : StyleSpan(s), MdSpan
class MdColor(c: Int) : ForegroundColorSpan(c), MdSpan
class MdFont(t: Typeface) : TypefaceSpan(t), MdSpan

data class SKey(val cls: Class<*>, val attr: Int, val s: Int, val e: Int)

fun attrOf(o: Any): Int = when (o) {
    is MdSize -> o.size
    is MdStyle -> o.style
    is MdColor -> o.foregroundColor
    else -> 0
}

class ASpans(val density: Float) {
    /** Diff-based restyle of [a,b): only spans that actually differ are removed/added. */
    fun reconcile(sp: Spannable, a: Int, b: Int, s: Spans) {
        val tmp = SpannableStringBuilder()
        val wanted = HashMap<SKey, Any>()
        // materialise wanted spans without touching sp
        val sink = object : SpanSink { override fun put(o: Any, st: Int, en: Int) { wanted[SKey(o.javaClass, attrOf(o), st, en)] = o } }
        emit(s, sink)
        for (o in sp.getSpans(a, b, MdSpan::class.java)) {
            val st = sp.getSpanStart(o); val en = sp.getSpanEnd(o)
            if (st < a || en > b) continue
            val k = SKey(o.javaClass, attrOf(o), st, en)
            if (wanted.remove(k) == null) sp.removeSpan(o)
        }
        for ((k, o) in wanted) sp.setSpan(o, k.s, k.e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    fun emit(s: Spans, out: SpanSink) {
        for (i in 0 until s.size) {
            val a = s.start[i]; val b = s.end[i]
            when (s.kind[i]) {
                K_H -> { out.put(MdSize(hPx[s.level[i]]), a, b); out.put(MdStyle(Typeface.BOLD), a, b) }
                K_MARK -> out.put(MdColor(0xFFB4B4B4.toInt()), a, b)
                K_BOLD -> out.put(MdStyle(Typeface.BOLD), a, b)
                K_ITAL -> out.put(MdStyle(Typeface.ITALIC), a, b)
                K_CODE, K_FENCE -> out.put(MdFont(Typeface.MONOSPACE), a, b)
                K_QUOTE -> out.put(MdColor(0xFF6A6A6A.toInt()), a, b)
                K_URL -> out.put(MdColor(0xFF9A9A9A.toInt()), a, b)
            }
        }
    }
    private val hPx = intArrayOf(28, 28, 24, 21, 19, 19, 19).map { (it * density).roundToInt() }
    fun apply(sp: Spannable, s: Spans) {
        val f = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        for (i in 0 until s.size) {
            val a = s.start[i]; val b = s.end[i]
            when (s.kind[i]) {
                K_H -> { sp.setSpan(MdSize(hPx[s.level[i]]), a, b, f); sp.setSpan(MdStyle(Typeface.BOLD), a, b, f) }
                K_MARK -> sp.setSpan(MdColor(0xFFB4B4B4.toInt()), a, b, f)
                K_BOLD -> sp.setSpan(MdStyle(Typeface.BOLD), a, b, f)
                K_ITAL -> sp.setSpan(MdStyle(Typeface.ITALIC), a, b, f)
                K_CODE, K_FENCE -> sp.setSpan(MdFont(Typeface.MONOSPACE), a, b, f)
                K_QUOTE -> sp.setSpan(MdColor(0xFF6A6A6A.toInt()), a, b, f)
                K_URL -> sp.setSpan(MdColor(0xFF9A9A9A.toInt()), a, b, f)
            }
        }
    }
}

fun paragraphBounds(t: CharSequence, pos: Int): IntArray {
    var s = pos
    while (s > 0 && t[s - 1] != '\n') s--
    var e = pos
    while (e < t.length && t[e] != '\n') e++
    return intArrayOf(s, e)
}

class MainActivity : ComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val fmThread = HandlerThread("fm").apply { start() }
    private val frames = Collections.synchronizedList(ArrayList<LongArray>())
    private val fmListener = Window.OnFrameMetricsAvailableListener { _, fm, _ ->
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

    private val sizeDp get() = resources.displayMetrics.density
    private fun textPx() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 18f, resources.displayMetrics)
    private fun contentWidthPx() = resources.displayMetrics.widthPixels - (2 * 24 * sizeDp).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val mode = intent.getStringExtra("mode") ?: "micro"
        val size = intent.getIntExtra("size", 100_000)
        val edits = intent.getIntExtra("edits", 20)
        Log.i(TAG, "START mode=$mode size=$size widthPx=${contentWidthPx()} density=$sizeDp")
        when (mode) {
            "micro" -> Thread { micro() }.start()
            "compose-state", "compose-ot", "compose-plain" -> composeE2E(mode, size, edits)
            "edittext", "edittext-plain", "edittext-fast", "edittext-fast2", "edittext-fast2r" -> editTextE2E(mode, size, edits)
        }
    }

    // ---------------- micro benchmarks (background thread) ----------------
    private fun micro() {
        val sizes = (intent.getStringExtra("sizes") ?: "10000,50000,100000,300000").split(',').map { it.trim().toInt() }
        val density = Density(this)
        val resolver = createFontFamilyResolver(this)
        val measurer = TextMeasurer(resolver, density, LayoutDirection.Ltr, 1)
        val style = TextStyle(fontSize = 18.sp, lineHeight = 1.6.em)
        val cons = Constraints(maxWidth = contentWidthPx())
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = textPx() }
        val aspans = ASpans(resources.displayMetrics.scaledDensity)
        val warm = 3; val iters = 10
        for (size in sizes) {
            var text = Doc.generate(size)
            Log.i(TAG, "doc size=${text.length} lines=${text.count { it == '\n' }}")
            // parse cost (full)
            run {
                val ts = ArrayList<Long>()
                repeat(warm + iters) { k -> val t0 = System.nanoTime(); val sp = MdStyler.parse(text); val t1 = System.nanoTime(); if (k >= warm) ts.add(t1 - t0); if (k == 0) Log.i(TAG, "spans=${sp.size}") }
                stats("micro|$size|parse-full", ts)
            }
            // Compose TextMeasurer: what BasicTextField(TextFieldState) does on every text change
            val only = intent.getStringExtra("only")
            for (variant in (if (only == "android") emptyList() else listOf("plain", "spans", "spans+para"))) {
                val tb = ArrayList<Long>(); val tm = ArrayList<Long>(); val tt = ArrayList<Long>()
                repeat(warm + iters) { k ->
                    val mid = text.length / 2
                    text = text.substring(0, mid) + "a" + text.substring(mid)
                    val t0 = System.nanoTime()
                    val sp = if (variant == "plain") null else MdStyler.parse(text)
                    val ann = CStyles.annotated(text, sp, variant == "spans+para")
                    val t1 = System.nanoTime()
                    val r = measurer.measure(ann, style = style, constraints = cons, skipCache = true)
                    val lc = r.lineCount
                    val t2 = System.nanoTime()
                    if (k >= warm) { tb.add(t1 - t0); tm.add(t2 - t1); tt.add(t2 - t0) }
                    if (k == 0) Log.i(TAG, "compose $variant lines=$lc height=${r.size.height}")
                }
                stats("micro|$size|compose-$variant|parse+build", tb)
                stats("micro|$size|compose-$variant|measure", tm)
                stats("micro|$size|compose-$variant|total", tt)
            }
            // Android StaticLayout full build (== cost of opening a doc in EditText)
            run {
                val ssb = SpannableStringBuilder(text); aspans.apply(ssb, MdStyler.parse(text))
                val ts = ArrayList<Long>()
                repeat(warm + 5) { k ->
                    val t0 = System.nanoTime()
                    val l = StaticLayout.Builder.obtain(ssb, 0, ssb.length, paint, contentWidthPx()).setIncludePad(false).build()
                    val t1 = System.nanoTime()
                    if (k >= warm) ts.add(t1 - t0)
                    if (k == 0) Log.i(TAG, "static lines=${l.lineCount}")
                }
                stats("micro|$size|android-staticlayout-full", ts)
            }
            // DynamicLayout: incremental reflow per keystroke (what EditText does)
            for (variant in listOf("plain", "spans", "spans-fast")) {
                val ssb: SpannableStringBuilder = if (variant == "spans-fast") FastEditable(text) else SpannableStringBuilder(text)
                if (variant != "plain") aspans.apply(ssb, MdStyler.parse(text))
                val t0b = System.nanoTime()
                val dl = DynamicLayout.Builder.obtain(ssb, paint, contentWidthPx()).setIncludePad(false).build()
                val t1b = System.nanoTime()
                Log.i(TAG, "RESULT|micro|$size|android-dynamiclayout-$variant|initial-build|n=1|min=%.2f|med=%.2f|p90=%.2f|max=%.2f".format((t1b - t0b) / 1e6, (t1b - t0b) / 1e6, (t1b - t0b) / 1e6, (t1b - t0b) / 1e6))
                val ti = ArrayList<Long>(); val tr = ArrayList<Long>()
                repeat(warm + 30) { k ->
                    val mid = ssb.length / 2
                    val t0 = System.nanoTime()
                    ssb.insert(mid, "a") // triggers DynamicLayout.ChangeWatcher.reflow synchronously
                    val t1 = System.nanoTime()
                    if (variant != "plain") {
                        // incremental restyle of the edited paragraph only
                        val pb = paragraphBounds(ssb, mid)
                        for (o in ssb.getSpans(pb[0], pb[1], MdSpan::class.java)) {
                            val st = ssb.getSpanStart(o); val en = ssb.getSpanEnd(o)
                            if (st >= pb[0] && en <= pb[1]) ssb.removeSpan(o)
                        }
                        aspans.apply(ssb, MdStyler.parse(ssb, pb[0], pb[1]))
                    }
                    val t2 = System.nanoTime()
                    if (k >= warm) { ti.add(t1 - t0); tr.add(t2 - t0) }
                }
                Log.i(TAG, "dynamic lines=${dl.lineCount}")
                stats("micro|$size|android-dynamiclayout-$variant|insert-reflow", ti)
                stats("micro|$size|android-dynamiclayout-$variant|insert+restyle", tr)
            }
        }
        Log.i(TAG, "DONE micro")
    }

    // ---------------- end-to-end helpers ----------------
    private val editT0 = ArrayList<Long>()
    private val editDur = ArrayList<Long>()
    private val editVsync = ArrayList<Long>()

    private fun recordEdit(block: () -> Unit) {
        val t0 = System.nanoTime()
        block()
        val t1 = System.nanoTime()
        editT0.add(t0); editDur.add(t1 - t0)
        val idx = editVsync.size
        editVsync.add(-1)
        Choreographer.getInstance().postFrameCallback { ft -> editVsync[idx] = ft }
    }

    private var etRef: EditText? = null

    private fun verifyLayout() {
        val et = etRef ?: return
        val l = et.layout
        val n = l.lineCount
        val starts = IntArray(n) { l.getLineStart(it) }
        val tops = IntArray(n) { l.getLineTop(it) }
        val force = object : android.text.style.UpdateLayout {}
        et.text.setSpan(force, 0, et.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        et.text.removeSpan(force)
        val l2 = et.layout
        var bad = -1
        if (l2.lineCount != n) bad = -2 else for (i in 0 until n) if (l2.getLineStart(i) != starts[i] || l2.getLineTop(i) != tops[i]) { bad = i; break }
        Log.i(TAG, "VERIFY lines=$n height=${l2.height} result=" + (if (bad == -1) "OK" else "MISMATCH at $bad (lines after full reflow=${l2.lineCount})"))
    }

    private fun report(label: String) {
        verifyLayout()
        window.removeOnFrameMetricsAvailableListener(fmListener)
        val snapshot = synchronized(frames) { frames.toList() }
        val total = ArrayList<Long>(); val ui = ArrayList<Long>(); val work = ArrayList<Long>()
        for (i in editVsync.indices) {
            val v = editVsync[i]
            val f = snapshot.minByOrNull { kotlin.math.abs(it[0] - v) } ?: continue
            if (kotlin.math.abs(f[0] - v) > 2_000_000) continue
            total.add(f[1])
            val uiNs = f[2] + f[3] + f[4] + f[5] + f[6]
            ui.add(uiNs)
            work.add(uiNs + editDur[i])
        }
        stats("$label|edit-call", editDur)
        stats("$label|frame-total", total)
        stats("$label|frame-ui(anim+layout+draw+input+sync)", ui)
        stats("$label|per-keystroke-main-thread-work(edit+frame-ui)", work)
        Log.i(TAG, "DONE $label")
    }

    private fun scheduleEdits(label: String, edits: Int, spacingMs: Long, doEdit: (Int) -> Unit) {
        var n = 0
        val r = object : Runnable {
            override fun run() {
                if (n < edits) { recordEdit { doEdit(n) }; n++; main.postDelayed(this, spacingMs) } else main.postDelayed({ report(label) }, 1500)
            }
        }
        main.postDelayed({ window.addOnFrameMetricsAvailableListener(fmListener, Handler(fmThread.looper)); main.postDelayed(r, 500) }, 2500)
    }

    private fun logOpen(label: String, t0: Long) {
        // first frame after content: log the time the frame completes
        Choreographer.getInstance().postFrameCallback {
            main.post { Log.i(TAG, "RESULT|$label|open-to-first-frame|n=1|med=%.2f".format((System.nanoTime() - t0) / 1e6)) }
        }
    }

    // ---------------- Compose BasicTextField(TextFieldState) ----------------
    private fun composeE2E(mode: String, size: Int, edits: Int) {
        val text = Doc.generate(size)
        val t0 = System.nanoTime()
        val state = TextFieldState(text, TextRange(text.length / 2))
        if (mode == "compose-state") {
            val sp = MdStyler.parse(text)
            state.edit {
                for (i in 0 until sp.size) {
                    addStyle(CStyles.forKind(sp.kind[i], sp.level[i]), TextRange(sp.start[i], sp.end[i]), ExpandPolicy.AtEnd)
                }
            }
        }
        val ot = if (mode == "compose-ot") OutputTransformation {
            val sp = MdStyler.parse(asCharSequence())
            for (i in 0 until sp.size) addStyle(CStyles.forKind(sp.kind[i], sp.level[i]), sp.start[i], sp.end[i])
        } else null
        val label = "e2e|$size|$mode"
        setContent {
            BasicTextField(
                state = state,
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp),
                textStyle = TextStyle(fontSize = 18.sp, lineHeight = 1.6.em),
                outputTransformation = ot,
            )
            LaunchedEffect(Unit) { delay(1) }
        }
        logOpen(label, t0)
        scheduleEdits(label, edits, 250) {
            state.edit {
                val mid = length / 2
                insert(mid, "a")
                placeCursorBeforeCharAt(mid + 1)
            }
        }
    }

    // ---------------- EditText + spans ----------------
    private fun editTextE2E(mode: String, size: Int, edits: Int) {
        val text = Doc.generate(size)
        val t0 = System.nanoTime()
        val aspans = ASpans(resources.displayMetrics.scaledDensity)
        val tb = intent.getStringExtra("tb")
        val et = (if (tb != null) NoFloatEditText(this, tb) else EditText(this)).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.TOP or Gravity.START
            setPadding((24 * sizeDp).roundToInt(), (48 * sizeDp).roundToInt(), (24 * sizeDp).roundToInt(), (48 * sizeDp).roundToInt())
            background = null
            setLineSpacing(0f, 1.6f)
        }
        if (mode == "edittext-fast") et.setEditableFactory(FastEditable.Factory)
        if (tb == "clear") et.customSelectionActionModeCallback = NoFloatEditText.clearingCallback()
        if (mode.startsWith("edittext-fast2")) et.setEditableFactory(FastEditable2.Factory)
        if (mode == "edittext-fast2r") {
            val caret = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setColor(0xFF1AA3FF.toInt())
                setSize((2 * sizeDp).roundToInt(), 1)
            }
            et.setTextCursorDrawable(caret)
            et.highlightColor = 0x401AA3FF
        }
        val ssb = SpannableStringBuilder(text)
        if (mode != "edittext-plain") aspans.apply(ssb, MdStyler.parse(text))
        if (intent.getBooleanExtra("hang", false)) {
            val hang = (48 * sizeDp).roundToInt()
            et.setPadding((8 * sizeDp).roundToInt(), et.paddingTop, et.paddingRight, et.paddingBottom)
            ssb.setSpan(android.text.style.LeadingMarginSpan.Standard(hang), 0, ssb.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            val sp = MdStyler.parse(text)
            for (i in 0 until sp.size) if (sp.kind[i] == K_H) {
                val lvl = sp.level[i]
                val p = TextPaint(et.paint).apply { textSize = intArrayOf(28, 28, 24, 21, 19, 19, 19)[lvl] * resources.displayMetrics.scaledDensity; typeface = Typeface.DEFAULT_BOLD }
                val w = p.measureText("#".repeat(lvl) + " ").roundToInt()
                ssb.setSpan(HangSpan(w), sp.start[i], sp.end[i], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        et.setText(ssb, android.widget.TextView.BufferType.EDITABLE)
        if (intent.getBooleanExtra("focus", false) && et is NoFloatEditText) {
            val pb = paragraphBounds(et.text, text.length / 2)
            et.focusStart = pb[0]; et.focusEnd = pb[1]
        }
        Log.i(TAG, "editable class=${et.text.javaClass.name}")
        main.postDelayed({ (et.text as? FastEditable2)?.let { Log.i(TAG, "suppressed=${it.suppressed}") } }, 12000)
        et.setSelection(text.length / 2)
        setContentView(et, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val label = "e2e|$size|$mode"
        logOpen(label, t0)
        etRef = et
        val vary = intent.getBooleanExtra("vary", false)
        scheduleEdits(label, edits, 250) { n ->
            val ed: Editable = et.text
            val mid = ed.length / 2
            if (!vary) ed.insert(mid, "a") else when (n % 5) {
                0 -> ed.insert(mid, "a")
                1 -> ed.insert(mid, "\n")
                2 -> ed.delete(mid, mid + 3)
                3 -> ed.insert(mid, " **bold** and `code` ")
                else -> ed.insert(paragraphBounds(ed, mid)[0], "## ")
            }
            if (mode == "edittext-fast2r") {
                val pb = paragraphBounds(ed, mid)
                aspans.reconcile(ed, pb[0], pb[1], MdStyler.parse(ed, pb[0], pb[1]))
            } else if (mode != "edittext-plain") {
                val pb = paragraphBounds(ed, mid)
                for (o in ed.getSpans(pb[0], pb[1], MdSpan::class.java)) {
                    val st = ed.getSpanStart(o); val en = ed.getSpanEnd(o)
                    if (st >= pb[0] && en <= pb[1]) ed.removeSpan(o)
                }
                aspans.apply(ed, MdStyler.parse(ed, pb[0], pb[1]))
            }
        }
    }
}
