# T05 — Editor surface: MarkdownEditText + EditorScrollView + Compose host

**Goal** The app opens straight into a full-screen, plain (unstyled) Markdown editor: Duo font, iA line pitch, a
centred column on large screens, a 2 dp blue caret that spans the full line pitch, 50 % window-height scroll room
at the end, and the caret always kept above the keyboard. Scrolling a 100k-char document never shows blank bands.
This task proves the scrolling architecture (01 §4.4) on the emulator before any styling is built on top of it.

**Depends on** T02 (tokens: `WriterColors`, `LocalWriterColors`, `WriterFont`, `PlatformFonts`, `WidthClass`,
`EditorMetrics`, `WriterDimens`, `MdWriterTheme`; `DesignGallery`; fonts in `res/font`). T01 (Makefile, build).

**Read first**
- `plans/01-architecture.md` §3 (editor/ package), §4.1/§4.4, §6.2, §7, §8, §10 rules 2, 3, 8, 11.
- `plans/02-design-spec.md` §3 (table + column math + pitch), §5 (caret, selection, status-bar strip).
- `plans/research/editor-engine.md` §6.5 (EditText config sketch; its undo/shortcut/selection parts are NOT yours).
- `plans/research/factcheck.md` §1 A11, A14, A15, A16, A20 and §6 C7, C9, C10.
- `plans/reference/bench/Md.kt` (`object Doc`, `generate(target)`), `plans/reference/bench/MainActivity.kt`
  (`fmListener`, `recordEdit`, `report`: the FrameMetrics work formula).

## Scope — In / Out
In: `MarkdownEditText`, `CaretDrawable`, `EditorScrollView`, `EditorGeometry` (pure), `EditorStyle` + `EditorColors`,
`FontSet`, `EditorController` skeleton, `EditorHost` composable, debug sample loader + frame logger, MainActivity wiring.

Out (do NOT build; the named task owns it):
- Any span / styling, `MdEditable`, `SpanFactory`, `reflowAll` → **T06**. Restyler, `edits`, `version` → **T07**.
- Undo (`allowUndo=false` only), smart Enter/Tab, shortcuts, `apply(TextEdit)` → **T08**.
- `HideSystemSelectionToolbar`, `selection` flow, pill, `perform()` → **T09**.
- EditorScreen / ViewModel / real document loading / read-only enforcement → **T11**. Drawer → **T12**.
- Swipe gesture → **T13**. Focus Mode, typewriter room → **T15**. Find → its own later task.
- Do not add stub members for the above to `EditorController`; each owner adds its own.

## Files to create / modify
- `app/src/main/res/values/editor_styles.xml` — `Widget.MdWriter.Editor` style.
- `app/src/main/kotlin/dev/mdwriter/editor/spans/FontSet.kt` — six platform faces (T06 uses it as-is).
- `app/src/main/kotlin/dev/mdwriter/editor/spans/EditorStyle.kt` — `EditorStyle`, `EditorColors`, `WriterColors.toEditorColors()`.
- `app/src/main/kotlin/dev/mdwriter/editor/EditorGeometry.kt` — pure column/pitch/room math.
- `app/src/main/kotlin/dev/mdwriter/editor/CaretDrawable.kt` — full-pitch caret.
- `app/src/main/kotlin/dev/mdwriter/editor/MarkdownEditText.kt` — the widget.
- `app/src/main/kotlin/dev/mdwriter/editor/EditorScrollView.kt` — scroller, geometry application, viewport helpers.
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt` — skeleton facade + `InstallRequest`.
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorHost.kt` — `AndroidView` host + status-bar strip.
- `app/src/main/kotlin/dev/mdwriter/debug/SampleDocs.kt` — `SMALL` sample + `generate()` (debug-gated use).
- `app/src/main/kotlin/dev/mdwriter/debug/FrameWorkLogger.kt` — FrameMetrics logger (debug-gated use).
- `app/src/main/kotlin/dev/mdwriter/MainActivity.kt` (modify) — editor host by default; gallery behind extra.
- `app/src/main/AndroidManifest.xml` (modify only if missing) — `android:windowSoftInputMode="adjustResize"`.
- `app/src/test/kotlin/dev/mdwriter/editor/EditorGeometryTest.kt` — JVM.
- `app/src/test/kotlin/dev/mdwriter/editor/MarkdownEditTextConfigTest.kt` — Robolectric.
- `app/src/androidTest/kotlin/dev/mdwriter/editor/EditorScrollDeviceTest.kt` — instrumented.

## Steps
1. Style XML (below). No `android:id` anywhere for the editor (rule 8).
2. `FontSet` + `EditorStyle` + `EditorColors` exactly as in Reference code §A.
3. `EditorGeometry.compute(...)` (§B) + `EditorGeometryTest` (table in Acceptance 1). Run `make test`.
4. `CaretDrawable` (§C). `MarkdownEditText` (§D). `EditorScrollView` (§E). `EditorController` (§F).
5. `EditorHost` (§G). `SampleDocs`: copy `object Doc` from `plans/reference/bench/Md.kt` verbatim as
   `internal object SampleDocs { fun generate(target: Int): String }` (keep its RNG/seed so output is deterministic),
   plus `const val SMALL` = the fixed construct sample in §H, plus
   `fun forExtra(v: String?): String? = when (v) { "small" -> SMALL; "100k" -> generate(100_000); "300k" -> generate(300_000); else -> null }`.
6. `FrameWorkLogger(window)`: `Window.OnFrameMetricsAvailableListener` on a `HandlerThread("mdframes")`; per frame
   `work = INPUT_HANDLING + ANIMATION + LAYOUT_MEASURE + DRAW` (ns → ms, same formula as the bench `recordEdit`);
   for frames with `LAYOUT_MEASURE > 0 || INPUT_HANDLING > 0` log `Log.i("MDPERF", "FRAME|work=%.2f")`; every 20
   such frames log `MDPERF RESULT|frames|plain-typing|med=..|p90=..`. T07 may reuse it.
7. `MainActivity` (keep T02's edge-to-edge + theme code): inside `MdWriterTheme { }`:
   `if (BuildConfig.DEBUG && intent.getBooleanExtra("gallery", false)) DesignGallery() else EditorDemo()` where
   private `EditorDemo()` = `remember { EditorController(activity, EditorStyle.create(activity, colors)) }`,
   `LaunchedEffect(Unit) { controller.install(InstallRequest(text = (if (BuildConfig.DEBUG) SampleDocs.forExtra(intent.getStringExtra("sample")) else null) ?: "", selection = 0, scrollY = 0, readOnly = false)) }`,
   `LaunchedEffect(colors) { controller.setStyle(controller.style.also { it.colors = colors.toEditorColors() }) }`,
   `DisposableEffect(Unit) { onDispose { controller.release() } }`, then `EditorHost(controller)`.
   If `BuildConfig.DEBUG && intent.getBooleanExtra("frameLog", false)` attach `FrameWorkLogger(window)`.
   Debug-only `Log.i("MDLIFE", "onCreate")` in `onCreate` (for the rotation check). T11 replaces `EditorDemo`.
8. `MarkdownEditTextConfigTest` (Robolectric), `EditorScrollDeviceTest` (instrumented) — see Acceptance.
9. Run the VERIFICATION GATE (Acceptance 5–10) on the emulator. If any gate item fails after a reasonable fix
   attempt: STOP-AND-ASK in STATUS.md with the screenshots/logs. Never fall back to a self-scrolling EditText.
10. `make check`, `make test-device DEVICE=emulator-5554`, STATUS entry, commit.

## Reference code
**A. FontSet / EditorStyle (copy; names are the contract for T06/T07/T15)**
```kotlin
package dev.mdwriter.editor.spans
class FontSet(val regular: Typeface, val italic: Typeface, val bold: Typeface, val boldItalic: Typeface,
              val mono: Typeface, val monoBold: Typeface) {
    fun isBold(t: Typeface?) = t === bold || t === boldItalic
    fun isItalic(t: Typeface?) = t === italic || t === boldItalic
    fun of(bold: Boolean, italic: Boolean): Typeface =
        when { bold && italic -> boldItalic; bold -> this.bold; italic -> this.italic; else -> regular }
    companion object {
        fun load(ctx: Context, font: WriterFont): FontSet {
            val f = PlatformFonts.load(ctx, font); val m = PlatformFonts.load(ctx, WriterFont.Mono)
            return FontSet(f.regular, f.italic, f.bold, f.boldItalic, m.regular, m.bold)
        }
    }
}
data class EditorColors(val bg: Int, val text: Int, val textSecondary: Int, val markup: Int, val accent: Int,
    val selection: Int, val codeBg: Int, val highlightBg: Int, val highlightText: Int, val focusDim: Int)
fun WriterColors.toEditorColors() = EditorColors(bg.toArgb(), text.toArgb(), textSecondary.toArgb(), markup.toArgb(),
    accent.toArgb(), selection.toArgb(), codeBg.toArgb(), highlightBg.toArgb(), highlightText.toArgb(), focusDim.toArgb())
/** Mutable, shared by the widget and (T06) every span. Mutate, then call EditorController.setStyle(). */
class EditorStyle(var font: WriterFont, var fonts: FontSet, var colors: EditorColors,
                  var textSizeStep: Int = EditorMetrics.DEFAULT_TEXT_SIZE_STEP,
                  var measureChars: Int = EditorMetrics.DEFAULT_MEASURE_CHARS, var highlightSyntax: Boolean = false) {
    // Derived by EditorScrollView from EditorGeometry on width/settings change; read-only for everyone else.
    var widthClass = WidthClass.Compact; var textSizePx = 0f; var lineSpacingExtraPx = 0f; var gutterPx = 0
    var underlinePx = 1f                                   // 1 dp, set from density in create()
    val headingScale = floatArrayOf(1f, 1.60f, 1.40f, 1.25f, 1.10f, 1.00f, 1.00f)   // index = level (02 §3)
    companion object { fun create(ctx: Context, colors: WriterColors, font: WriterFont = WriterFont.Duo) =
        EditorStyle(font, FontSet.load(ctx, font), colors.toEditorColors()).also { it.underlinePx = ctx.resources.displayMetrics.density } }
}
```
**B. EditorGeometry (sketch; pure Kotlin — no `android.*` imports; `WidthClass`/`EditorMetrics`/`Dp` are JVM-safe)**
```kotlin
data class EditorGeometry(val widthClass: WidthClass, val textSizePx: Float, val pitchPx: Float,
    val lineSpacingExtraPx: Float, val gutterPx: Int, val paddingStart: Int, val paddingEnd: Int,
    val paddingTop: Int, val paddingBottom: Int) {
  companion object {
    fun compute(widthPx: Int, windowHeightPx: Int, topInsetPx: Int, density: Float, font: WriterFont,
                textSizeStep: Int, measureChars: Int, spToPx: (Float) -> Float): EditorGeometry {
      val wc = WidthClass.fromWidthDp(widthPx / density)
      val size = spToPx(EditorMetrics.bodyTextSizeSp(textSizeStep, wc).toFloat())
      val pitch = EditorMetrics.linePitchMultiplier(font, wc) * size
      val extra = (pitch - EditorMetrics.NATURAL_LINE_BOX_EM * size).coerceAtLeast(0f)
      val minSide = EditorMetrics.sideMarginMin(wc).value * density
      val side: Float; val gutter: Float
      if (wc == WidthClass.Compact) { side = minSide; gutter = 0f }          // phones: fill width, no hang (C6)
      else {
        val column = measureChars * EditorMetrics.N_WIDTH_EM * size
        side = maxOf(minSide, (widthPx - column) / 2f)
        gutter = minOf(EditorMetrics.gutterChars(wc) * EditorMetrics.N_WIDTH_EM * size, side) // hang borrows start margin only
      }
      return EditorGeometry(wc, size, pitch, extra, gutter.roundToInt(), (side - gutter).roundToInt(), side.roundToInt(),
        topInsetPx + (EditorMetrics.topRoom(wc).value * density).roundToInt(),
        (EditorMetrics.BOTTOM_ROOM_FRACTION * windowHeightPx).roundToInt())
    }
  }
}
```
(If `sideMarginMin`/`topRoom` return Compose `Dp`, `.value` is the float — `Dp` is a pure inline class, JVM-safe.)

**C. CaretDrawable (sketch — VERIFY; fallback below)** Editor sets the cursor bounds to
`(left, lineTop − padding.top, left + intrinsicWidth, lineBottomWithoutSpacing + padding.bottom)` (Editor.java
`updateCursorPosition`, check `~/Library/Android/sdk/sources/android-37.0/android/widget/Editor.java` if present).
```kotlin
class CaretDrawable(private val widthPx: Int, private val radiusPx: Float) : Drawable() {
    var color: Int = 0; set(v) { field = v; paint.color = v; invalidateSelf() }
    var halfExtraPx: Int = 0                     // = round(lineSpacingExtraPx / 2), updated with geometry
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun getIntrinsicWidth() = widthPx
    override fun getIntrinsicHeight() = -1
    override fun getPadding(padding: Rect): Boolean { padding.set(0, halfExtraPx, 0, halfExtraPx); return true }
    override fun draw(c: Canvas) { val b = bounds; c.drawRoundRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), radiusPx, radiusPx, paint) }
    override fun setAlpha(a: Int) { paint.alpha = a }; override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
```
Fallback if padding misbehaves on device (caret offset/jumping): `halfExtraPx = 0` (glyph-box caret), record it
in STATUS "Deviations". Must call `setTextCursorDrawable(caret)` again after changing `halfExtraPx`.

**D. MarkdownEditText (adapt editor-engine §6.5; ONLY these parts)**
```kotlin
class MarkdownEditText(context: Context) : EditText(context, null, 0, R.style.Widget_MdWriter_Editor) {
    val caret = CaretDrawable(dp(2), dp(1).toFloat())
    init {
        isSaveEnabled = false; id = View.NO_ID                   // rule 8
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setHorizontallyScrolling(false); isVerticalScrollBarEnabled = false; overScrollMode = OVER_SCROLL_NEVER
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE; hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        includeFontPadding = false                               // every line = 1.30 em box + extra (uniform pitch)
        revealOnFocusHint = false                                // no ScrollView scroll-to-child jumps
    }
    fun applyColors(c: EditorColors) {
        setTextColor(c.text); highlightColor = c.selection; caret.color = c.accent; setTextCursorDrawable(caret)
        textSelectHandle?.let { setTextSelectHandle(it.mutate().apply { setTint(c.accent) }) }   // same for Left/Right
    }
    /** The EditText never scrolls itself (01 §4.4, factcheck A15). bringPointIntoView still reaches the ScrollView. */
    override fun scrollTo(x: Int, y: Int) = super.scrollTo(0, 0)
    override fun onTextContextMenuItem(id: Int): Boolean = when (id) {       // rule 11
        android.R.id.paste -> super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
        android.R.id.copy, android.R.id.cut -> { /* plain String clip of [min,max); cut: text!!.delete(min,max) */ }
        else -> super.onTextContextMenuItem(id)
    }
}
```
Typeface/size/spacing are set only by `EditorScrollView.applyGeometry` and `EditorController.setStyle` (rule 2).

**E. EditorScrollView (sketch)**
```kotlin
class EditorScrollView(context: Context, val editText: MarkdownEditText, private val style: EditorStyle) : ScrollView(context) {
    init { isFillViewport = true; isVerticalScrollBarEnabled = true
           addView(editText, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
    private var lastWidth = -1
    var onGeometryChanged: ((EditorGeometry) -> Unit)? = null        // T06 hooks HangRoomSpan/restyle here
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w); if (width != lastWidth) { lastWidth = width; applyGeometry() }
        super.onMeasure(w, h)
    }
    fun applyGeometry() {                        // width change or settings change ONLY (rule 2)
        val wm = context.getSystemService(WindowManager::class.java).currentWindowMetrics   // Activity context!
        val top = wm.windowInsets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        val g = EditorGeometry.compute(lastWidth, wm.bounds.height(), top, resources.displayMetrics.density, style.font,
            style.textSizeStep, style.measureChars) { TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, resources.displayMetrics) }
        style.widthClass = g.widthClass; style.gutterPx = g.gutterPx; style.lineSpacingExtraPx = g.lineSpacingExtraPx
        with(editText) {
            if (textSize != g.textSizePx) setTextSize(TypedValue.COMPLEX_UNIT_PX, g.textSizePx)
            if (lineSpacingExtra != g.lineSpacingExtraPx || lineSpacingMultiplier != 1f) setLineSpacing(g.lineSpacingExtraPx, 1f)
            if (paddingStart != g.paddingStart || paddingTop != g.paddingTop || paddingEnd != g.paddingEnd || paddingBottom != g.paddingBottom)
                setPaddingRelative(g.paddingStart, g.paddingTop, g.paddingEnd, g.paddingBottom)
            caret.halfExtraPx = (g.lineSpacingExtraPx / 2).roundToInt(); setTextCursorDrawable(caret)
        }
        style.textSizePx = g.textSizePx; onGeometryChanged?.invoke(g)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h < oldh && editText.isFocused) post { editText.bringPointIntoView(editText.selectionEnd) }   // IME opened
    }
    /** Layout (text) y -> y inside this ScrollView's visible area. */
    fun textYToViewport(textY: Int): Int = editText.top + editText.totalPaddingTop + textY - scrollY
    /** Visible region in Layout coordinates (height excludes the IME: the host has imePadding()). */
    fun visibleTextRect(out: Rect = Rect()): Rect { val t = scrollY - editText.top - editText.totalPaddingTop
        out.set(0, t, editText.layout?.width ?: 0, t + height); return out }
    /** Inclusive line range currently visible (clamped), empty if no layout. Used by T07/T15. */
    fun visibleLineRange(): IntRange { val l = editText.layout ?: return IntRange.EMPTY; val r = visibleTextRect()
        return l.getLineForVertical(r.top.coerceAtLeast(0))..l.getLineForVertical(r.bottom.coerceAtLeast(0)) }
}
```
**F. EditorController (skeleton; T06–T15 extend it)**
```kotlin
data class InstallRequest(val text: String, val selection: Int, val scrollY: Int, val readOnly: Boolean)
class EditorController(context: Context, initialStyle: EditorStyle) {
    val style: EditorStyle = initialStyle
    val editText = MarkdownEditText(context).also { it.applyColors(initialStyle.colors); it.typeface = initialStyle.fonts.regular }
    val scrollView = EditorScrollView(context, editText, style)
    suspend fun install(doc: InstallRequest) {          // main thread; T06 replaces the body with the styled build
        editText.setText(doc.text); editText.setSelection(doc.selection.coerceIn(0, editText.length()))
        editText.showSoftInputOnFocus = !doc.readOnly    // real read-only enforcement: T08
        scrollView.doOnNextLayout { scrollView.scrollTo(0, doc.scrollY) }; scrollView.requestLayout()
    }
    fun setStyle(s: EditorStyle) { /* s === style: applyColors, typeface = fonts.regular, scrollView.applyGeometry() */ }
    fun snapshot(): String = editText.text.toString()
    fun requestFocus() { editText.requestFocus() }
    fun hasFocus(): Boolean = editText.hasFocus()                       // T12 drawer IME restore
    fun collapseSelection() { val c = editText.selectionEnd; if (c >= 0) editText.setSelection(c) }   // T12/T13/T16
    fun showIme() { editText.requestFocus(); editText.windowInsetsController?.show(WindowInsets.Type.ime()) }
    fun hideIme() { editText.windowInsetsController?.hide(WindowInsets.Type.ime()) }
    fun caret(): Int = editText.selectionEnd
    fun scrollY(): Int = scrollView.scrollY
    fun release() { scrollView.onGeometryChanged = null }
}
```
**G. EditorHost**
```kotlin
@Composable fun EditorHost(controller: EditorController, modifier: Modifier = Modifier) {
    val colors = LocalWriterColors.current
    Box(modifier.fillMaxSize().background(colors.bg).imePadding()
        .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal))) {
        AndroidView(factory = { (controller.scrollView.parent as? ViewGroup)?.removeView(controller.scrollView); controller.scrollView },
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .background(colors.bg.copy(alpha = WriterDimens.STATUS_PROTECTION_ALPHA)))      // 02 §5 strip
    }
}
```
**H. `SampleDocs.SMALL`** (T06 screenshots use it; keep exactly these constructs, one per line/block):
`# Heading one`, `## Heading two`, `### Heading three`, a paragraph with `**bold**`, `*italic*`, `` `code` ``,
`~~strike~~`, `[a link](https://example.com)`, `<https://example.org>`; a ```` ```kotlin ```` fenced block of 2
lines; `> A quote that is long enough to wrap onto a second line on a phone screen.`; `- item`, `- a long list item
that wraps onto a second line on the phone`, `1. first`; `- [ ] open task`, `- [x] done task`; a 3-row table
`| a | b |` / `|---|---|` / `| 1 | 2 |`; `***`.

## Acceptance criteria
1. `EditorGeometryTest` (density 1, `spToPx = { it }`, Duo, step 2, measure 64, window h 1000, inset 0) asserts:
   360 → Compact, size 17, extra 5.95±0.01, gutter 0, start/end 24/24, top 56, bottom 500; 448 → same as 360;
   600 → Medium, size 18, extra 8.10±0.01, gutter 32, start 0, end 32, top 64; 840 → Expanded, gutter 65,
   start 10, end 74, top 72; 1280 → gutter 65, start 230, end 294; measure 80 at 1280 → end 208; Quattro at 448 →
   extra = 1.55·17 − 22.1 = 4.25. Plus: bottom never depends on anything but window height.
2. `MarkdownEditTextConfigTest` (Robolectric): `isSaveEnabled == false`, `id == View.NO_ID`, inputType has
   MULTI_LINE|CAP_SENTENCES|AUTO_CORRECT, imeOptions has NO_EXTRACT_UI|NO_FULLSCREEN, `breakStrategy == SIMPLE`,
   `revealOnFocusHint == false`, `scrollTo(0, 500)` leaves `scrollY == 0`; copy of a selection over a `StyleSpan`
   text yields a clip whose item text `is String`; `CaretDrawable.getPadding` returns top = bottom = halfExtraPx.
3. `grep -rn "setPadding\|setTextSize\|setLineSpacing\|typeface =" app/src/main/kotlin/dev/mdwriter/editor` shows
   calls only inside `applyGeometry`, `setStyle` and the `EditorController` constructor.
4. `EditorScrollDeviceTest` (instrumented, launches MainActivity with `sample=100k`): after
   `scrollView.fullScroll(View.FOCUS_DOWN)` + idle: `editText.scrollY == 0`, `editText.paddingBottom ==
   round(0.5 × windowHeight)`, and the last line's `textYToViewport(layout.getLineTop(last))` is within
   40–60 % of `scrollView.height`; after `scrollView.scrollTo(0, contentHeight/2)` `editText.scrollY == 0`.
GATE (emulator, screenshots opened and described in STATUS):
5. Fling to the middle of the 100k sample: text is drawn from the protection strip to the bottom edge (no blank band > 1 line).
6. At the end: last line can be scrolled to ~mid-screen (bottom room ≈ 50 % of window height).
7. Tap near the end, `input text`: caret line is fully above the IME top in the screenshot.
8. `frameLog=true` typing 20 chars at 100k (no styling): `MDPERF RESULT|frames|plain-typing|med=` < 4 ms.
9. Zoomed screenshot: caret height ≈ one line pitch (±2 px) on a middle line and on the last line; handles blue.
10. Rotation: column recomputed (landscape margins/size per 02 §3), `MDLIFE onCreate` logged exactly once.

## Verification commands
```sh
make test && make install-debug DEVICE=emulator-5554
A="adb -s emulator-5554"; $A shell wm size
$A shell am start -S -n dev.mdwriter.debug/dev.mdwriter.MainActivity --es sample 100k --ez frameLog true
$A shell input swipe 700 2200 700 400 40; sleep 1; $A exec-out screencap -p > /tmp/t05-mid.png
$A shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_MOVE_END; $A shell input swipe 700 1800 700 900 300
$A exec-out screencap -p > /tmp/t05-end.png
$A shell input tap 700 1200; sleep 1; $A shell input text "hello%sworld%sagain%sand%sagain"; $A exec-out screencap -p > /tmp/t05-ime.png
$A logcat -d -s MDPERF | tail -5
$A shell settings put system accelerometer_rotation 0; $A shell settings put system user_rotation 1; sleep 2
$A exec-out screencap -p > /tmp/t05-land.png; $A logcat -d -s MDLIFE | grep -c onCreate
$A shell settings put system user_rotation 0; $A shell settings put system accelerometer_rotation 1
make test-device DEVICE=emulator-5554 && make check
```
Open every PNG with Read and describe what you see in STATUS.

## Pitfalls
- Rule 2 / A14: `setPadding`/`setTextSize`/`setLineSpacing`/`typeface` null the layout (0.1–0.3 s at 300k). Never
  on IME show/hide or scroll — `applyGeometry` runs only when the width changed or settings changed.
- C9/A16: insets go on the Compose `Box` (`imePadding`, cutout), never the EditText; `clipToPadding` does not exist on TextView.
- A15: if the EditText ever self-scrolls, padding bands are clipped (blank bands). The `scrollTo` override guards it.
- `revealOnFocusHint = true` (default) makes ScrollView jump to the EditText's top on focus.
- `currentWindowMetrics` needs the Activity context (StrictMode `IncorrectContextUseViolation` otherwise).
- Changing child padding in the ScrollView's `onSizeChanged` = requestLayout during layout (one wrong frame): use `onMeasure`.
- `AndroidView` factory must detach the reused `scrollView` from an old parent ("child already has a parent").
- Missing `adjustResize` → no IME insets → `imePadding()` does nothing and the caret hides under the IME.
- A11: without `CaretDrawable` padding the caret only covers the 1.30 em glyph box. Check empty and last lines.
- `pasteAsPlainText` must be used for paste; copy must be a plain `String` (rule 11) — no spans on the clipboard.

## Definition of done
- [ ] Acceptance 1–10 pass; gate screenshots + MDPERF numbers in STATUS.md.
- [ ] `make check` green; `make test-device DEVICE=emulator-5554` green.
- [ ] STATUS.md entry (incl. caret-padding verdict, deviations); commit `T05: editor surface (EditText in ScrollView host)`.
