# T09 — Selection toolbar pill

**Goal** — When text is selected (touch long-press, handle drag, or hardware Shift+arrows), one quiet `surface` pill
appears 8 dp above the selection. It flips below when there is no room and is clamped inside the editor. It offers
Bold · Italic · Heading · Link | Cut · Copy · Paste · … More, with as many slots as the width allows. The system floating
toolbar never appears, but the selection handles, the IME and the system "Paste" pill on a collapsed caret stay.
Each action is one undo step and leaves the transformed text selected.

**Depends on**
- T08: `ToolbarAction` (+ `isInlineWrap`), `EditorController.perform/apply` (apply sets the selection from the edit),
  `highlightEnabled`, `isReadOnly`, the undo manager, and the `MarkdownEditText.onSelectionChanged/onFocusChanged`
  overrides.
- T05: `ui/editor/EditorHost.kt` (`AndroidView(factory = { controller.scrollView })`), `EditorScrollView`, and
  `EditorController.selection` (it may be a placeholder flow).
- T02: `ic_*` Material Symbols drawables, `WriterDimens.pill*`, `WriterMotion.PILL_*`/`emphasized*`, `WriterTheme.colors`.

**Read first**
- plans/01-architecture.md §4.4 and §4.9, §6.2 (`selection: StateFlow<SelectionState>`), §10 rules 2, 10, 11
- plans/02-design-spec.md §6 (all of it), §11 (the pill motion row), §14 (icon names)
- plans/research/editor-engine.md §5.5, §6.9. The research sketch's anchor uses `editText.scrollY`, which is wrong here:
  the EditText never scrolls (01 §4.4), so use `scrollView.scrollY`.

## Scope — In / Out
**In:** `HideSystemSelectionToolbar`, `SelectionState`, `SelectionUi` (visibility, 150 ms re-show, anchor in
EditorScrollView coords, updates on scroll/layout/restyle). The Compose `FormatToolbarOverlay` + `FormatToolbar` pill +
`MoreMenu`. Slot/priority logic. `canPaste()`. Accessibility custom actions on the EditText. Tests and screenshots.
**Out (do NOT build):**
- Formatting semantics and undo → T08/T04 (just call `controller.perform`).
- Chrome glyphs and the overflow menu → **T13**. Find bar → **T17** (when it takes focus the pill hides by itself).
- Focus-mode/stats use of the selection → **T15**. The `highlightSyntax` setting UI → **T19**.
- Do not add a `customInsertionActionModeCallback`: the system caret "Paste" pill stays.

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/editor/SelectionUi.kt` — **create**: `HideSystemSelectionToolbar`, `SelectionState`, `SelectionUi`
- `app/src/main/kotlin/dev/mdwriter/editor/MarkdownEditText.kt` — **modify**: install the callback (rule 10), `selectionUi` hooks
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt` — **modify**: `selection` = `selectionUi.state`, `canPaste()`,
  `apply` wrapped in `selectionUi.programmatic { }`, a11y actions, `refreshAccessibilityActions()`
- `app/src/main/kotlin/dev/mdwriter/ui/toolbar/ToolbarAction.kt` — **modify**: add `ToolbarItem`, `MoreEntry`, `ToolbarSlots`
- `app/src/main/kotlin/dev/mdwriter/ui/toolbar/FormatToolbar.kt` — **create**: `FormatToolbarOverlay`, `FormatToolbar`, `pillOffset`
- `app/src/main/kotlin/dev/mdwriter/ui/toolbar/MoreMenu.kt` — **create**: in-tree upward menu + `moreMenuOffset`
- `app/src/main/kotlin/dev/mdwriter/ui/editor/EditorHost.kt` — **modify**: `EditorSurface` = Box(AndroidView + overlay)
- `app/src/main/res/values/strings.xml` — **modify**: `tb_*` labels (below)
- `app/src/test/kotlin/dev/mdwriter/ui/toolbar/ToolbarSlotsTest.kt`, `PillPositionTest.kt` — JVM
- `app/src/test/kotlin/dev/mdwriter/ui/toolbar/FormatToolbarTest.kt` — Robolectric + Compose (`…junit4.v2.createComposeRule`)
- `app/src/androidTest/kotlin/dev/mdwriter/editor/SelectionToolbarTest.kt` — instrumented (uses T08's `startEditor`)

## Steps
1. Read the STATUS entries for T05–T08. Run
   `grep -rn "SelectionState\|customSelectionActionModeCallback\|fun EditorHost\|selection:" app/src/main/kotlin`.
   Keep any existing names. If `SelectionState` exists, keep its fields and add missing ones.
2. Check the icons: `ls app/src/main/res/drawable/ic_{format_bold,format_italic,format_h1,link,content_copy,content_paste,content_cut,code,more_horiz}.xml`.
   If one is missing, add it to T02's icon list in `scripts/fetch-icons.sh` and re-run the script. If it can't be fetched,
   STOP-AND-ASK.
3. Add strings to `strings.xml`:
   - `tb_bold` Bold, `tb_italic` Italic, `tb_heading` Heading, `tb_link` Link, `tb_copy` Copy, `tb_paste` Paste,
     `tb_cut` Cut, `tb_code` Code, `tb_more` "More formatting"
   - `tb_strike` Strikethrough, `tb_highlight` Highlight, `tb_quote` Quote, `tb_bullets` "Bulleted list",
     `tb_numbered` "Numbered list", `tb_task` Task, `tb_code_block` "Code block", `tb_clear` "Clear formatting",
     `tb_select_all` "Select all"
4. Create `SelectionUi.kt` (A). In `MarkdownEditText`:
   - `init { customSelectionActionModeCallback = HideSystemSelectionToolbar }`
   - `internal var selectionUi: SelectionUi? = null`
   - call `selectionUi?.onSelectionChanged(s, e)` / `onFocusChanged(focused)` next to T08's calls
5. In `EditorController`:
   - create `selectionUi` and set `editText.selectionUi = it`
   - `editText.addOnAttachStateChangeListener(it)`; call `it.onViewAttachedToWindow(editText)` if already attached
   - `val selection = selectionUi.state`
   - wrap T08's `apply` body in `selectionUi.programmatic { }` (re-anchors at once, no 150 ms hide)
   - add `canPaste()` and the a11y actions (D)
6. Add `ToolbarItem`, `MoreEntry` and `ToolbarSlots` to `ToolbarAction.kt` (reference B), plus `ToolbarSlotsTest`.
7. Create `FormatToolbar.kt` (reference C) and `MoreMenu.kt`, plus `PillPositionTest`.
8. Replace the body of `EditorHost` with `EditorSurface(controller, modifier)` (reference E). Keep T05's AndroidView
   arguments (`update`, `onRelease`, …) unchanged.
9. Write `FormatToolbarTest` (AC3) and `SelectionToolbarTest` (AC4). Then take the screenshots (AC5–AC8).
10. Run `make format`, then `make check` and `make test-device DEVICE=emulator-5554`. Add a STATUS entry and commit.

## Reference code
**A. `SelectionUi.kt` — near-final sketch.** Keep it View-layer only: no Compose imports in `dev.mdwriter.editor`.
```kotlin
data class SelectionState(val visible: Boolean, val anchor: RectF, val start: Int, val end: Int) {
    companion object { internal val NONE = RectF(); val Hidden = SelectionState(false, NONE, 0, 0) }   // never mutate NONE
}
/** Hard rule 10: return TRUE and clear in BOTH create and prepare (Editor.java adds items before/after us). */
object HideSystemSelectionToolbar : ActionMode.Callback {
    @VisibleForTesting internal var createCount = 0
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { createCount++; menu.clear(); return true }
    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
    override fun onDestroyActionMode(mode: ActionMode) = Unit
}
internal class SelectionUi(private val editText: MarkdownEditText, private val scrollView: EditorScrollView) :
    ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private val _state = MutableStateFlow(SelectionState.Hidden)
    val state: StateFlow<SelectionState> = _state.asStateFlow()
    private val path = Path(); private val bounds = RectF(); private var programmaticDepth = 0
    private val showRunnable = Runnable { publish(show = true) }
    override fun onViewAttachedToWindow(v: View) { v.viewTreeObserver.addOnPreDrawListener(this) }
    override fun onViewDetachedFromWindow(v: View) { v.viewTreeObserver.removeOnPreDrawListener(this); editText.removeCallbacks(showRunnable) }
    /** Every change hides; re-show 150 ms after the LAST change (handle drags, Shift+arrows). */
    fun onSelectionChanged(s: Int, e: Int) {
        if (programmaticDepth > 0) return
        editText.removeCallbacks(showRunnable); publish(show = false)
        if (s != e && editText.hasFocus()) editText.postDelayed(showRunnable, RESHOW_DELAY_MS)
    }
    fun onFocusChanged(focused: Boolean) = onSelectionChanged(editText.selectionStart, if (focused) editText.selectionEnd else editText.selectionStart)
    /** Our own edits (toolbar/shortcut): no hide flicker, re-anchor immediately. */
    fun <T> programmatic(block: () -> T): T {
        programmaticDepth++
        try { return block() } finally { if (--programmaticDepth == 0) { editText.removeCallbacks(showRunnable); publish(show = true) } }
    }
    /** Covers scroll, relayout and restyle-driven geometry changes; StateFlow dedups equal states (RectF.equals). */
    override fun onPreDraw(): Boolean { if (_state.value.visible) publish(show = true); return true }
    private fun publish(show: Boolean) {
        val s = minOf(editText.selectionStart, editText.selectionEnd); val e = maxOf(editText.selectionStart, editText.selectionEnd)
        val r = if (show && s in 0 until e && editText.hasFocus()) visibleRect(s, e) else null
        _state.value = SelectionState(r != null, r ?: SelectionState.NONE, s, e)   // start/end always published (T15 stats)
    }
    /** Bounds of the VISIBLE part of the selection in EditorScrollView viewport coords; null if off-screen. */
    private fun visibleRect(s: Int, e: Int): RectF? {
        val l = editText.layout ?: return null; val vh = scrollView.height; if (vh <= 0) return null
        val ox = editText.left + editText.totalPaddingLeft - scrollView.scrollX
        val oy = editText.top + editText.totalPaddingTop - scrollView.scrollY   // NOT editText.scrollY (always 0)
        val vs = maxOf(s, l.getLineStart(l.getLineForVertical(-oy))); val ve = minOf(e, l.getLineEnd(l.getLineForVertical(vh - oy)))
        if (vs >= ve) return null
        path.reset(); l.getSelectionPath(vs, ve, path); path.computeBounds(bounds)   // API 31+ overload (2-arg is deprecated)
        val r = RectF(bounds).apply { offset(ox.toFloat(), oy.toFloat()); top = maxOf(top, 0f); bottom = minOf(bottom, vh.toFloat()) }
        return if (r.bottom > r.top) r else null
    }
    private companion object { const val RESHOW_DELAY_MS = 150L }   // = WriterMotion.PILL_RESHOW_DELAY_MS (no Compose import here)
}
```
**B. Slots — copy verbatim** (append to `ToolbarAction.kt`). 02 §6 gives the priority and the rule `n = min(9, floor((w − 32 dp)/48 dp))`,
with the last slot always More. Display order: formatting group, then 1 px divider, then clipboard (Cut, Copy, Paste),
then More.
```kotlin
enum class ToolbarItem(val action: ToolbarAction?, @DrawableRes val icon: Int, @StringRes val label: Int) {
    Bold(ToolbarAction.Bold, R.drawable.ic_format_bold, R.string.tb_bold), Italic(ToolbarAction.Italic, R.drawable.ic_format_italic, R.string.tb_italic),
    Heading(ToolbarAction.HeadingCycle, R.drawable.ic_format_h1, R.string.tb_heading), Link(ToolbarAction.Link, R.drawable.ic_link, R.string.tb_link),
    Copy(ToolbarAction.Copy, R.drawable.ic_content_copy, R.string.tb_copy), Paste(ToolbarAction.Paste, R.drawable.ic_content_paste, R.string.tb_paste),
    Cut(ToolbarAction.Cut, R.drawable.ic_content_cut, R.string.tb_cut), Code(ToolbarAction.Code, R.drawable.ic_code, R.string.tb_code),
    More(null, R.drawable.ic_more_horiz, R.string.tb_more),
}
enum class MoreEntry(val action: ToolbarAction, @StringRes val label: Int) {
    Strike(ToolbarAction.Strike, R.string.tb_strike), Highlight(ToolbarAction.Highlight, R.string.tb_highlight),
    Quote(ToolbarAction.Quote, R.string.tb_quote), Bullets(ToolbarAction.BulletList, R.string.tb_bullets),
    Numbered(ToolbarAction.NumberedList, R.string.tb_numbered), Task(ToolbarAction.TaskList, R.string.tb_task),
    CodeBlock(ToolbarAction.CodeBlock, R.string.tb_code_block), Clear(ToolbarAction.ClearFormatting, R.string.tb_clear),
    SelectAll(ToolbarAction.SelectAll, R.string.tb_select_all),
}
object ToolbarSlots {
    val PRIORITY = listOf(ToolbarItem.Bold, ToolbarItem.Italic, ToolbarItem.Heading, ToolbarItem.Link,
        ToolbarItem.Copy, ToolbarItem.Paste, ToolbarItem.Cut, ToolbarItem.Code)
    data class Layout(val formatting: List<ToolbarItem>, val clipboard: List<ToolbarItem>, val overflow: List<ToolbarItem>, val more: List<MoreEntry>) {
        val buttonCount get() = formatting.size + clipboard.size + 1            // + More
        val hasDivider get() = formatting.isNotEmpty() && clipboard.isNotEmpty()
    }
    fun slotCount(widthDp: Float): Int {
        val usable = widthDp - 2 * WriterDimens.pillScreenPadding.value
        return minOf(WriterDimens.PILL_MAX_SLOTS, floor(usable / WriterDimens.pillButton.value).toInt()).coerceAtLeast(2)
    }
    fun compute(widthDp: Float, highlightEnabled: Boolean): Layout {
        val shown = PRIORITY.take(slotCount(widthDp) - 1).toSet()
        return Layout(
            formatting = listOf(ToolbarItem.Bold, ToolbarItem.Italic, ToolbarItem.Heading, ToolbarItem.Link, ToolbarItem.Code).filter { it in shown },
            clipboard = listOf(ToolbarItem.Cut, ToolbarItem.Copy, ToolbarItem.Paste).filter { it in shown },
            overflow = PRIORITY.filter { it !in shown },                           // shown first in More, priority order
            more = MoreEntry.entries.filter { it != MoreEntry.Highlight || highlightEnabled },
        )
    }
}
```
**C. `FormatToolbar.kt` — sketch.** It is an in-tree overlay (not a `Popup`/`DropdownMenu`: those are separate focusable
windows that steal focus, which closes the IME and hides the pill). The pill size is deterministic, so the first frame
is placed correctly.
```kotlin
@Composable
fun FormatToolbarOverlay(state: SelectionState, highlightEnabled: Boolean, readOnly: Boolean, canPaste: () -> Boolean,
                         onAction: (ToolbarAction) -> Unit, modifier: Modifier = Modifier) {   // caller passes Modifier.matchParentSize()
    val d = LocalDensity.current
    var box by remember { mutableStateOf(IntSize.Zero) }
    var moreOpen by remember { mutableStateOf(false) }
    val last = remember { arrayOf(state) }; if (state.visible) last[0] = state   // keeps the anchor during the exit animation
    val shown = last[0]
    LaunchedEffect(state.visible, state.start, state.end) { moreOpen = false }
    val slots = remember(box.width, highlightEnabled) { ToolbarSlots.compute(with(d) { box.width.toDp().value }, highlightEnabled) }
    val paste = remember(state.visible, state.start, state.end) { canPaste() }
    val btn = with(d) { WriterDimens.pillButton.roundToPx() }; val hair = 1                  // "1 px" divider/border
    val pillW = btn * slots.buttonCount + (if (slots.hasDivider) hair else 0); val pillH = with(d) { WriterDimens.pillHeight.roundToPx() }
    val pos = pillOffset(shown.anchor.top, shown.anchor.bottom, shown.anchor.centerX(), pillW, pillH, box.width, box.height,
        gapAbove = with(d) { WriterDimens.pillGapAboveSelection.roundToPx() }, gapBelow = with(d) { WriterDimens.pillGapBelowSelection.roundToPx() },
        margin = with(d) { WriterDimens.pillScreenPadding.roundToPx() })
    Box(modifier.onSizeChanged { box = it }) {                                  // no pointerInput: taps fall through to the editor
        AnimatedVisibility(
            visible = state.visible && box.width > 0, modifier = Modifier.offset { pos },
            enter = fadeIn(tween(WriterMotion.PILL_IN_MS, easing = WriterMotion.emphasizedDecelerate)) +
                scaleIn(tween(WriterMotion.PILL_IN_MS, easing = WriterMotion.emphasizedDecelerate), initialScale = WriterMotion.PILL_IN_SCALE_FROM) +
                slideInVertically(tween(WriterMotion.PILL_IN_MS, easing = WriterMotion.emphasizedDecelerate)) { with(d) { WriterMotion.pillInOffsetY.roundToPx() } },
            exit = fadeOut(tween(WriterMotion.PILL_OUT_MS, easing = WriterMotion.emphasizedAccelerate)),
        ) {
            FormatToolbar(slots, canPaste = paste, readOnly = readOnly, onItem = { item ->
                if (item == ToolbarItem.More) moreOpen = !moreOpen else { moreOpen = false; item.action?.let(onAction) }
            })
        }
        if (moreOpen && state.visible) MoreMenu(slots, paste, readOnly, pillPos = pos, pillW = pillW, pillH = pillH, box = box,
            onAction = { moreOpen = false; onAction(it) })
    }
}
/** Pure (JVM-tested). Above if it fits; else below (+28 dp handle room); else, when the selection fills the viewport, at the top gap. */
internal fun pillOffset(selTop: Float, selBottom: Float, selCx: Float, pillW: Int, pillH: Int, boxW: Int, boxH: Int,
                        gapAbove: Int, gapBelow: Int, margin: Int): IntOffset {
    val above = selTop.toInt() - gapAbove - pillH; val below = selBottom.toInt() + gapBelow
    val y = when { above >= 0 -> above; below + pillH <= boxH -> below; else -> gapAbove }.coerceIn(0, (boxH - pillH).coerceAtLeast(0))
    val x = if (boxW - 2 * margin < pillW) (boxW - pillW) / 2 else (selCx.toInt() - pillW / 2).coerceIn(margin, boxW - margin - pillW)
    return IntOffset(x, y)
}
```
- `FormatToolbar(slots, canPaste, readOnly, onItem, modifier)`:
  - A `Row` with `height(pillHeight)`, `clip(RoundedCornerShape(pillCornerRadius))`, `background(colors.surface)` and a
    1 px `colors.divider` border. No shadow or elevation.
  - It contains the formatting buttons, then (if `hasDivider`) a `1px × 24.dp` divider box, then the clipboard buttons,
    then More.
- `PillButton`:
  - `Box(Modifier.size(pillButton).focusProperties { canFocus = false }.clickable(interactionSource = null, indication = ripple(bounded = false, radius = 20.dp), enabled, role = Role.Button, onClickLabel = label) {…}.semantics { contentDescription = label })`
  - The icon is `Icon(painterResource(item.icon), null, tint = colors.text.copy(alpha = if (enabled) 1f else 0.38f))` at
    `WriterDimens.icon`.
  - `focusProperties` must come **before** `clickable`, so there's no focus target and the EditText keeps focus and the IME.
  - Enabled: Paste = `canPaste && !readOnly`; Cut and formatting = `!readOnly`; Copy and More are always enabled.
- `MoreMenu`:
  - A `Column`: `width(overflowMenuWidth)`, `heightIn(max = boxH − 16 dp)`, `menuCornerRadius` clip, `surface`
    background, 1 px `divider` border, `focusProperties { canFocus = false }` before `verticalScroll`, 8 dp vertical padding.
  - Rows are 48 dp, text only, `MaterialTheme.typography.bodyLarge` in `colors.text`: first `slots.overflow`, then a 1 px
    divider (only if overflow is non-empty), then `slots.more`.
  - `internal fun moreMenuOffset(pillPos, pillW, pillH, menuW, menuH, boxW, boxH, gap = 4 dp, margin)`:
    - x: end-aligned with the pill, clamped to the margins
    - y: above if it fits, else below, else `gap`
    - menuH is deterministic: rows × 48 dp + 16 dp

**D. Controller additions — sketch.**
```kotlin
fun canPaste(): Boolean {                                  // description only: getPrimaryClip() would trigger the paste toast
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
    return cm.hasPrimaryClip() && cm.primaryClipDescription?.hasMimeType("text/*") == true
}
private var a11yIds = emptyList<Int>()
fun refreshAccessibilityActions() {                        // call from init; T19 calls it when highlightSyntax changes
    a11yIds.forEach { ViewCompat.removeAccessibilityAction(editText, it) }
    a11yIds = A11Y.filter { it.first != ToolbarAction.Highlight || highlightEnabled }.map { (action, label) ->
        ViewCompat.addAccessibilityAction(editText, context.getString(label)) { _, _ -> perform(action); true }
    }
}
private val A11Y = listOf(ToolbarAction.Bold to R.string.tb_bold, ToolbarAction.Italic to R.string.tb_italic,
    ToolbarAction.Strike to R.string.tb_strike, ToolbarAction.Highlight to R.string.tb_highlight, ToolbarAction.Code to R.string.tb_code,
    ToolbarAction.CodeBlock to R.string.tb_code_block, ToolbarAction.Link to R.string.tb_link, ToolbarAction.HeadingCycle to R.string.tb_heading,
    ToolbarAction.Quote to R.string.tb_quote, ToolbarAction.BulletList to R.string.tb_bullets, ToolbarAction.NumberedList to R.string.tb_numbered,
    ToolbarAction.TaskList to R.string.tb_task, ToolbarAction.ClearFormatting to R.string.tb_clear)
```
**E. `EditorHost.kt` — sketch.** The Box and the AndroidView must share their top-left corner: `SelectionState.anchor`
is in EditorScrollView coords. IME/system-bar padding goes on the parent (rule 2), never between the Box and the
AndroidView.
```kotlin
@Composable
internal fun EditorSurface(controller: EditorController, modifier: Modifier = Modifier) {
    val sel by controller.selection.collectAsStateWithLifecycle()
    Box(modifier) {
        AndroidView(factory = { controller.scrollView }, modifier = Modifier.fillMaxSize())   // keep T05's other args
        FormatToolbarOverlay(sel, controller.highlightEnabled, controller.isReadOnly, controller::canPaste, controller::perform,
            Modifier.matchParentSize())                     // last child = drawn above T13's chrome later
    }
}
```

## Acceptance criteria
1. `ToolbarSlotsTest` (JVM):
   - `slotCount`: 448 → 8, 360 → 6, 464 → 9, 600 → 9, 280 → 5, 100 → 2
   - `compute(448f,false)`: formatting [Bold, Italic, Heading, Link], clipboard [Cut, Copy, Paste], overflow [Code]
   - `compute(360f,false)`: formatting [Bold, Italic, Heading, Link], clipboard [Copy], overflow [Paste, Cut, Code]
   - `compute(600f,…)`: overflow is empty and Code is in formatting
   - `MoreEntry.Highlight` is in `more` iff `highlightEnabled`
2. `PillPositionTest` (JVM):
   - above when room; below (`selBottom + gapBelow`) when `selTop` < pillH + gap; `gapAbove` when neither fits
   - x centred on the selection, clamped to `margin` at both edges, centred in a box narrower than pill + 2 × margin
   - `moreMenuOffset`: above / below / fallback
3. `FormatToolbarTest` (Robolectric, `MdWriterTheme { Box(Modifier.size(448.dp, 800.dp)) { FormatToolbarOverlay(…, Modifier.matchParentSize()) } }`
   with a fake `SelectionState(true, RectF(100f, 400f, 300f, 440f), 5, 9)`):
   - Bold, Italic, Heading, Link, Cut, Copy, Paste and "More formatting" are displayed; "Code" doesn't exist; clicking Bold records `ToolbarAction.Bold`
   - `canPaste = { false }` → Paste `assertIsNotEnabled()`; More shows "Code" and "Strikethrough"; "Highlight" appears only when enabled
   - at 360 dp: 5 buttons + More, and More lists Paste, Cut, Code first; `visible = false` + `waitForIdle()` → "Bold" doesn't exist
4. `SelectionToolbarTest` (instrumented; `startEditor("Hello world of words") { EditorSurface-like overlay }`):
   - `longPressSelectsWordAndShowsPill`:
     - `Instrumentation.sendPointerSync` DOWN at the screen position of offset 8, wait `getLongPressTimeout() + 300` ms, UP
     - selection [6, 11); "Bold" displayed; `HideSystemSelectionToolbar.createCount ≥ 1`
   - `boldKeepsSelectionPillAndFocus`: tap Bold → `Hello **world** of words`, selection [8, 13), pill shown, `hasFocus()`;
     undo → original
   - `programmaticSelectionShowsAfter150ms` (the hardware-keyboard path): `setSelection(0, 5)` → hidden at +50 ms,
     visible by +400 ms
   - `hiddenWhileSelectionKeepsChanging` (5 × `setSelection` 50 ms apart → hidden until ≥150 ms after the last)
   - `collapseAndFocusLossHide`
   - `anchorInScrollViewCoords`: `anchor.top` == `editText.top + totalPaddingTop + layout.getLineTop(0) − scrollView.scrollY`
     (±1 px); `scrollBy(0, 100)` on a long document moves it by −100 (±1)
   - `accessibilityActionsExposed`: node-info action labels include Bold, Italic, Link, Quote, Task; performing Bold on
     «world» → `**world**`
5. Screenshot at 448 dp (default AVD), after long-pressing "world": the pill is centred ~8 dp above the word with exactly
   8 buttons (B, I, H1, link | cut, copy, paste | more) and a 1 px divider after link; both handles and the IME are
   visible; no text-labelled system toolbar ("Cut", "Copy" or "Select all" text anywhere).
6. Screenshot at 360 dp (the `wm density` commands below): exactly 6 buttons (B, I, H1, link | copy | more). Tapping more
   shows Paste, Cut, Code, divider, Strikethrough … Select all, opening upward.
7. Flip: select a word on the top visible line (scroll so the line sits at the viewport top). The pill is below the
   selection and doesn't cover the handles.
8. Caret "Paste" pill kept: after Copy, long-press the empty area below the last line (or tap the insertion handle). The
   system floating toolbar with "Paste" appears, so the insertion mode is untouched.
9. After tapping any pill button the IME stays up (screenshot) and a second action chains on the still-selected text.
10. `make check` is green and `make test-device DEVICE=emulator-5554` is green.

## Verification commands
```sh
make test && make install-debug DEVICE=emulator-5554
adb -s emulator-5554 shell am start -n dev.mdwriter.debug/dev.mdwriter.MainActivity
adb -s emulator-5554 shell input text "Hello%sworld%sof%swords"
adb -s emulator-5554 shell input swipe 300 400 300 400 800        # long-press: take X Y from a screenshot first
adb -s emulator-5554 exec-out screencap -p > /tmp/t09-448.png
W=$(adb -s emulator-5554 shell wm size | grep -o '[0-9]*x' | head -1 | tr -d x)
adb -s emulator-5554 shell wm density $((W*160/360))              # ~360 dp wide
adb -s emulator-5554 shell am force-stop dev.mdwriter.debug && adb -s emulator-5554 shell am start -n dev.mdwriter.debug/dev.mdwriter.MainActivity
adb -s emulator-5554 exec-out screencap -p > /tmp/t09-360.png
adb -s emulator-5554 shell wm density reset                        # ALWAYS reset afterwards
adb -s emulator-5554 shell input keycombination KEYCODE_SHIFT_LEFT KEYCODE_DPAD_LEFT   # hardware-selection check
make test-device DEVICE=emulator-5554 && make check
```

## Pitfalls
- **Rule 10:** never return false from `onCreateActionMode` (kills the selection) or null from `startActionMode` (no
  handles); clear the menu in both create and prepare. Don't set `customInsertionActionModeCallback` (AC8).
- **01 §4.4:** the EditText's `scrollY` is always 0; use `scrollView.scrollY`/`editText.top` (the research sketch is wrong here).
- Never mutate a published `RectF` (StateFlow equality); `SelectionState.NONE` is shared and read-only. Bound
  `getSelectionPath` to the visible lines (select-all at 300k chars).
- No `Popup`/`DropdownMenu`/`focusable()`; put `focusProperties { canFocus = false }` before each `clickable`/`verticalScroll`.
- No Compose types in `dev.mdwriter.editor` (01 §3). Clipboard: read only `primaryClipDescription`. Cut/Copy/Paste go
  through `onTextContextMenuItem` (rule 11 + T08 undo groups).
- **Rule 2:** the pill floats and never causes `setPadding`/`setTextSize`. Use `ViewCompat.addAccessibilityAction`, never
  `setAccessibilityDelegate`.
- compose-rules: `modifier: Modifier = Modifier`, one root, hoisted state. Reset `wm density` afterwards; use only the
  emulator serial.

## Definition of done
- [ ] Names match 01 §3/§6.2; `SelectionState(visible, anchor, start, end)` is public in `dev.mdwriter.editor`.
- [ ] AC1–AC10 met; the 448/360 dp screenshots are described in STATUS; `make check` + `make test-device` are green; `wm density` is reset.
- [ ] STATUS notes for T13 (EditorSurface Box order), T15 (the selection flow carries start/end) and T19 (`refreshAccessibilityActions`).
- [ ] Commit `T09: selection toolbar pill, system toolbar suppression, a11y actions`.
