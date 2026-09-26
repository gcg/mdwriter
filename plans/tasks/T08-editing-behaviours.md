# T08 — Editing behaviours: undo/redo, smart Enter/Backspace/Tab, shortcuts, task toggle

**Goal** — Lists, quotes and fences behave like iA Writer: Enter continues or ends a list and closes an open fence;
Backspace at a list item's content start removes the marker; hardware Tab/Shift+Tab nests and un-nests items.
Undo/redo is our own (word-grouped, each smart or formatting edit one step, exposed as `canUndo`/`canRedo`). Ctrl
shortcuts go through the `ToolbarAction` enum that the pill (T09) reuses. Tapping `[ ]`/`[x]` toggles the task.

**Depends on**
- **T04:** `SmartEdit` (Enter/Backspace/indent/outdent/wrap/link/heading/quote/task + list/code-block/clear toggles) and
  `TextEdit.minimize`.
- **T07** (T05/T06 through it): `MarkdownEditText` (wrap_content) in `EditorScrollView`; `EditorController.install`;
  the highlighter updated **synchronously** in the Restyler's `onTextChanged`; `TaskSpan` on `MdKind.TASK_MARKER`; `EditEvent`s.
- Read the STATUS entries of T04–T07 first and use the names they actually shipped.

**Read first** — plans/01-architecture.md §3, §6.1, §6.2, §7, §10 (rules 2, 3, 5, 8, 11) · plans/02-design-spec.md §5 and
the last two bullets of §6 · plans/research/editor-engine.md §5.6, §5.10, §5.11, §6.10, §6.11 (corrected below) ·
plans/research/markdown.md §8 · plans/research/platform.md §10.1

## Scope — In / Out
**In:** `UndoHistory` + `MdUndoManager`; `SmartInput` (IC wrapper + keys); `EditorController.apply/perform/undo/redo/
canUndo/canRedo/toggleTaskAt`; `ToolbarAction`; `FormatCommands`; editor shortcuts; task tap-toggle; platform undo off;
tests and the IME matrix.

**Out (do NOT build):**
- The pill, `SelectionUi`, `HideSystemSelectionToolbar`, a11y custom actions → **T09**.
- Overflow Undo/Redo buttons, Activity-level shortcuts (Ctrl+N/O/L/R/D/S), Keyboard Shortcuts Helper → **T13**.
  Ctrl+F → **T17**. Focus/typewriter → **T15**. Settings → **T19**.
- SmartEdit semantics are T04's: fix only real bugs, in `:core:markdown`, test first. Undo persistence is a non-goal.

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/ui/toolbar/ToolbarAction.kt` — **create** sealed `ToolbarAction` (T09 appends slot types)
- `app/src/main/kotlin/dev/mdwriter/editor/UndoHistory.kt` — **create** pure steps/merge/groups (no `android.*`)
- `app/src/main/kotlin/dev/mdwriter/editor/MdUndoManager.kt` — **create** `TextWatcher` adapter + undo/redo replay
- `app/src/main/kotlin/dev/mdwriter/editor/FormatCommands.kt` — **create** pure `ToolbarAction` → `TextEdit`
- `app/src/main/kotlin/dev/mdwriter/editor/SmartInput.kt` — **create** `EditorCommands`, `ShortcutCommand`, `EditorShortcuts`, `SmartInput`, `SmartInputConnection`
- `app/src/main/kotlin/dev/mdwriter/editor/MarkdownEditText.kt` — **modify** IC wrapper, key/shortcut/context-menu/touch hooks
- `app/src/main/kotlin/dev/mdwriter/editor/EditorController.kt` — **modify** undo wiring, `apply`, `perform`, flows, install hook
- `app/src/main/res/values/editor_styles.xml` (T05 owns it; T02 must not define this style) — **modify only if needed**: `Widget.MdWriter.Editor` gets `android:allowUndo=false`
- `app/src/test/kotlin/dev/mdwriter/editor/{UndoHistoryTest,FormatCommandsTest,EditorShortcutsTest}.kt` — JVM
- `app/src/androidTest/kotlin/dev/mdwriter/editor/EditorTestHost.kt` — helper (reuse T05/T07's if one exists)
- `app/src/androidTest/kotlin/dev/mdwriter/editor/{SmartEditingTest,UndoRedoTest,TaskToggleTest}.kt` — instrumented

## Steps
1. Run `grep -rn "class TaskSpan\|MarkdownHighlighter(\|onTextContextMenuItem\|allowUndo\|readOnly\|suspend fun install\|onSelectionChanged\|onTouchEvent" app/src/main/kotlin`.
2. **Platform undo off** (if T05 hasn't done it): add a style `Widget.MdWriter.Editor` (parent
   `android:Widget.Material.EditText`, `<item name="android:allowUndo">false</item>`) and construct
   `MarkdownEditText(ctx, null, 0, R.style.Widget_MdWriter_Editor)`. There is no setter.
3. Create A, B (+ `UndoHistoryTest`) and C.
4. Create `FormatCommands` (F) + `FormatCommandsTest`. T04's names are fixed (01 §6.1): `toggleList(text, selStart,
   selEnd, kind: ListKind)` with `ListKind { BULLET, ORDERED, TASK }`, `toggleCodeBlock(text, selStart, selEnd)`,
   `codeToggle(text, selStart, selEnd)` and `clearFormatting(text, selStart, selEnd, enableHighlight = false)`, all `: TextEdit`.
5. Create D + `EditorShortcutsTest`, then modify `MarkdownEditText` (E). New fields are nullable `var`s set to `null`,
   because TextView's constructor calls overrides first.
6. Modify `EditorController` (F):
   - add `MdUndoManager(editText)` as a text watcher **after** the Restyler's; its `onChanged` updates the flows behind
     `canUndo`/`canRedo`; set `editText.mdUndo/smartInput/commands`
   - `install()`: `mdUndo.ignoring { setText… }`, then `mdUndo.clear()`; `release()` removes the watcher
   - add `val highlightEnabled` (the highlighter's flag) and `val isReadOnly`
   - **T08 owns engine-side read-only enforcement** (01 §6.2): `install()` sets `editText.readOnly = doc.readOnly`; while
     true, the SmartInput IC wrapper drops `commitText`/`setComposingText`/`deleteSurroundingText`/`commitContent`,
     printable/Enter/Del keys are consumed, cut/paste/undo/redo/apply/perform are no-ops; select + copy still work.
     Add `readOnlyBlocksTyping` to the instrumented tests. T11 only computes the flag and skips autosave.
7. Write G + the instrumented tests (AC4–AC6) and run them. Do the IME matrix (AC8).
8. Run `make format`, then `make check` and `make test-device DEVICE=emulator-5554`. Add a STATUS entry and commit.

## Reference code
**A. `ToolbarAction.kt` — copy verbatim.**
```kotlin
package dev.mdwriter.ui.toolbar
/** Every formatting/clipboard command: shortcuts (T08), pill + a11y actions (T09). Plain Kotlin, no Compose. */
sealed interface ToolbarAction {
    data object Bold : ToolbarAction; data object Italic : ToolbarAction; data object Strike : ToolbarAction
    data object Highlight : ToolbarAction; data object Code : ToolbarAction; data object CodeBlock : ToolbarAction
    data object Link : ToolbarAction; data object HeadingCycle : ToolbarAction
    data class SetHeading(val level: Int) : ToolbarAction { init { require(level in 0..6) } }
    data object Quote : ToolbarAction; data object BulletList : ToolbarAction; data object NumberedList : ToolbarAction
    data object TaskList : ToolbarAction; data object ClearFormatting : ToolbarAction
    data object Cut : ToolbarAction; data object Copy : ToolbarAction; data object Paste : ToolbarAction; data object SelectAll : ToolbarAction
}
/** Inline wraps are no-ops on verbatim lines (code/HTML/front matter); Code counts only for single-line selections. */
val ToolbarAction.isInlineWrap: Boolean get() = this == ToolbarAction.Bold || this == ToolbarAction.Italic ||
    this == ToolbarAction.Strike || this == ToolbarAction.Highlight || this == ToolbarAction.Link
```
**B. `UndoHistory` — sketch close to final.** Corrects editor-engine §6.11: delete runs over existing text merge; a new
word starts a new step even inside one multi-char (glide) commit; both selection ends are restored; the history is capped.
```kotlin
internal class UndoHistory(private val maxSteps: Int = 1_000, private val maxChars: Int = 1_000_000, private val windowMs: Long = 1_500) {
    class Op(var start: Int, var old: String, var new: String)
    class Step(val ops: MutableList<Op>, val beforeStart: Int, val beforeEnd: Int, var afterStart: Int, var afterEnd: Int, var time: Long) {
        fun revert(r: (Int, Int, String) -> Unit) { for (op in ops.asReversed()) r(op.start, op.start + op.new.length, op.old) }
        fun reapply(r: (Int, Int, String) -> Unit) { for (op in ops) r(op.start, op.start + op.old.length, op.new) }
        val chars: Int get() = ops.sumOf { it.old.length + it.new.length }
    }
    private val undo = ArrayDeque<Step>(); private val redo = ArrayDeque<Step>()
    private var breakNext = true; private var depth = 0; private var groupStep: Step? = null
    private var gStart = 0; private var gEnd = 0; private var approxChars = 0
    var expectedCaret = -1; private set                                  // caret after the last recorded edit
    val canUndo get() = undo.isNotEmpty(); val canRedo get() = redo.isNotEmpty(); val size get() = undo.size
    fun hardBreak() { breakNext = true }
    fun beginGroup(s: Int, e: Int) { if (depth++ == 0) { groupStep = null; gStart = s; gEnd = e; breakNext = true } }
    fun endGroup(s: Int, e: Int) { check(depth > 0); if (--depth == 0) { groupStep?.let { it.afterStart = s; it.afterEnd = e }; groupStep = null; breakNext = true } }
    fun record(start: Int, old: String, new: String, selStart: Int, selEnd: Int, now: Long) {
        if (old == new) return
        redo.clear(); approxChars += old.length + new.length; val caret = start + new.length
        if (depth > 0) {
            val g = groupStep ?: Step(mutableListOf(), gStart, gEnd, caret, caret, now).also { undo.addLast(it); groupStep = it }
            g.ops += Op(start, old, new); g.time = now
        } else {
            val top = undo.lastOrNull(); val op = top?.ops?.singleOrNull()
            if (!breakNext && op != null && now - top.time <= windowMs && tryMerge(op, start, old, new)) {
                top.afterStart = caret; top.afterEnd = caret; top.time = now
                if (op.old.isEmpty() && op.new.isEmpty()) undo.removeLast()      // typed, then fully backspaced
            } else undo.addLast(Step(mutableListOf(Op(start, old, new)), selStart, selEnd, caret, caret, now))
            breakNext = false
        }
        expectedCaret = caret; trim()
    }
    private fun tryMerge(op: Op, start: Int, old: String, new: String): Boolean {
        val opEnd = op.start + op.new.length
        return when {
            old.isEmpty() && op.new.isNotEmpty() && start == opEnd -> if (startsNewWord(op.new.last(), new)) false else { op.new += new; true }
            new.isEmpty() && start + old.length == opEnd && op.new.endsWith(old) -> { op.new = op.new.dropLast(old.length); true }
            new.isEmpty() && op.new.isEmpty() && start + old.length == op.start -> { op.start = start; op.old = old + op.old; true }
            new.isEmpty() && op.new.isEmpty() && start == op.start -> { op.old += old; true }           // forward delete
            old.isNotEmpty() && start >= op.start && start + old.length <= opEnd &&
                op.new.regionMatches(start - op.start, old, 0, old.length) -> {                       // composing/autocorrect
                val r = start - op.start; op.new = op.new.substring(0, r) + new + op.new.substring(r + old.length); true
            }
            else -> false
        }
    }
    private fun startsNewWord(prev: Char, new: String): Boolean { var p = prev; for (c in new) { if (p.isWhitespace() && !c.isWhitespace()) return true; p = c }; return false }
    private fun trim() {
        if (undo.size <= maxSteps && approxChars <= maxChars) return
        approxChars = undo.sumOf { it.chars }
        while (undo.size > 1 && (undo.size > maxSteps || approxChars > maxChars)) approxChars -= undo.removeFirst().chars
    }
    fun popUndo(): Step? = undo.removeLastOrNull()?.also { redo.addLast(it); breakNext = true }
    fun popRedo(): Step? = redo.removeLastOrNull()?.also { undo.addLast(it); breakNext = true }
    fun clear() { undo.clear(); redo.clear(); approxChars = 0; breakNext = true; expectedCaret = -1 }
}
```
**C. `MdUndoManager(view: EditText, clock: () -> Long = SystemClock::uptimeMillis) : TextWatcher` — spec.**
- Wraps a private `UndoHistory`, with `onChanged`, `canUndo`/`canRedo`, `hardBreak()`, `clear()` (fires `onChanged`),
  and `ignoring { }` (sets `applying`).
- `fun <T> group(block: () -> T): T` is **not inline** (an inline function can't touch the private history):
  `beginGroup(sel)`, run `block`, then `finally { endGroup(sel); onChanged() }`.
- `beforeTextChanged`: set `inChange`. Unless `applying`, capture `pendingOld = TextUtils.substring(s, start, start + count)`
  (never `subSequence`) and both `Selection` ends.
- `onTextChanged`: unless `applying`, `history.record(start, pendingOld, TextUtils.substring(…), min, max, clock())` and
  fire `onChanged`. `afterTextChanged` clears `inChange`.
- `onSelectionChanged(s, e)`: when not `inChange`/`applying` and `(s != e || s != expectedCaret)` → `hardBreak()` (a caret jump).
- `undo()`/`redo()` pop a step and replay it:
  - `removeComposingSpans`, `applying = true`, then `beginBatchEdit` (rule 3)
  - `revert`/`reapply` via `ed.replace`, `Selection.setSelection(before/after)`
  - `finally { endBatchEdit(); applying = false }`
  - `restartInput(view)` if it was composing; fire `onChanged`

**D. `SmartInput.kt` — sketch.**
- `internal interface EditorCommands { fun perform(action: ToolbarAction); fun undo(); fun redo(); fun toggleTaskAt(offset: Int) }`
- `internal sealed interface ShortcutCommand { Undo; Redo; data class Action(val action: ToolbarAction) }`
- `internal object EditorShortcuts { fun map(keyCode: Int, meta: Int): ShortcutCommand? }` is pure (the `KeyEvent` ints
  are inlined, so it runs on the JVM):
  - null unless `META_CTRL_ON` is set and neither `META_ALT_ON` nor `META_META_ON` is
  - Z → Undo, Shift+Z → Redo, Y → Redo, B → Bold, I → Italic, K → Link, Shift+C → Code, Shift+X → Strike,
    0..6 → `SetHeading(n)`; Shift must be absent unless listed
  - everything else → null, so TextView keeps Ctrl+C/X/V/A and Ctrl+Shift+V
```kotlin
internal class SmartInputConnection(target: InputConnection, private val smart: SmartInput) : InputConnectionWrapper(target, false) {
    private var swallowUp = -1
    private fun intercept(t: CharSequence?): Boolean {
        if (t == null || !t.contains('\n')) return false
        if (t.length == 1 && smart.onEnter()) return true; smart.breakUndo(); return false   // multi-char with '\n' = IME paste
    }
    override fun commitText(text: CharSequence?, pos: Int) = intercept(text) || super.commitText(text, pos)
    // API 33 overload: InputConnectionWrapper forwards it straight to the target, bypassing the 2-arg override
    override fun commitText(text: CharSequence, pos: Int, attr: TextAttribute?) = intercept(text) || super.commitText(text, pos, attr)
    override fun sendKeyEvent(event: KeyEvent): Boolean {
        val k = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN && event.hasNoModifiers() &&
            ((k == KeyEvent.KEYCODE_ENTER && smart.onEnter()) || (k == KeyEvent.KEYCODE_DEL && smart.onBackspace()))) { swallowUp = k; return true }
        if (event.action == KeyEvent.ACTION_UP && k == swallowUp) { swallowUp = -1; return true }
        return super.sendKeyEvent(event)
    }
    override fun deleteSurroundingText(b: Int, a: Int) = (b == 1 && a == 0 && smart.onBackspace()) || super.deleteSurroundingText(b, a)
    override fun deleteSurroundingTextInCodePoints(b: Int, a: Int) = (b == 1 && a == 0 && smart.onBackspace()) || super.deleteSurroundingTextInCodePoints(b, a)
}
internal class SmartInput(private val view: MarkdownEditText, private val hl: () -> MarkdownHighlighter?,
                          private val undo: MdUndoManager, private val apply: (TextEdit) -> Unit) {
    private fun caret(): Int? {                                             // collapsed, editable, NOT composing (markdown.md §8)
        val ed = view.text ?: return null; val s = view.selectionStart
        return if (view.readOnly || s < 0 || s != view.selectionEnd || BaseInputConnection.getComposingSpanStart(ed) != -1) null else s
    }
    fun onEnter(): Boolean {                                                // ONE replace = one undo step + one highlighter.update
        val c = caret() ?: return false; val h = hl() ?: return false; val li = h.lineInfoAt(c)
        apply(SmartEdit.onEnter(view.text.toString(), c, li.type, h.isFenceUnclosed(li.line)) ?: return false); return true
    }
    fun onBackspace(): Boolean {                                            // runs on EVERY Backspace: O(line), never O(doc)
        val c = caret() ?: return false; val li = hl()?.lineInfoAt(c) ?: return false
        if (c == li.start || li.type.isVerbatim || (li.listDepth == 0 && li.quoteDepth == 0)) return false
        val e = SmartEdit.onBackspace(TextUtils.substring(view.text, li.start, li.end), c - li.start) ?: return false
        apply(TextEdit(e.start + li.start, e.end + li.start, e.replacement, e.selStart + li.start, e.selEnd + li.start)); return true
    }
    /** Tab is ALWAYS consumed: TextView.shouldAdvanceFocusOnTab() is true for our inputType, so "default" = focus leaves. */
    fun onTab(outdent: Boolean) {
        val c = caret(); val li = c?.let { hl()?.lineInfoAt(it) }
        if (c != null && li != null && !li.type.isVerbatim && li.listDepth > 0) {
            val t = view.text.toString()
            (if (outdent) SmartEdit.outdentListItem(t, c) else SmartEdit.indentListItem(t, c))?.let { apply(it); return }
        }
        val a = minOf(view.selectionStart, view.selectionEnd)
        if (!outdent && !view.readOnly) apply(TextEdit(a, maxOf(view.selectionStart, view.selectionEnd), "\t", a + 1))   // literal tab
    }                                                                       // Shift+Tab outside a list: no-op
    fun breakUndo() = undo.hardBreak()
}
```
**E. `MarkdownEditText` — sketch.** Merge into T05/T06's overrides; don't duplicate them.
- Fields: `internal var mdUndo: MdUndoManager? = null`, `smartInput`, `commands`; private `swallowKeyUp`, `taskDown = -1`,
  `downX`, `downY`. `onCreateInputConnection` wraps `super`'s IC in `SmartInputConnection` when `smartInput` is set.
- `onKeyDown`:
  - Tab (no modifiers or Shift only) → `onTab(isShiftPressed)`, always handled.
  - With `hasNoModifiers()`: Enter/NumpadEnter → `onEnter()`, Del → `onBackspace()`.
  - When handled, set `swallowKeyUp = keyCode` (`onKeyUp` swallows the matching up); else `super`.
- `onKeyShortcut`: `EditorShortcuts.map(keyCode, metaState)` → `commands.undo/redo/perform`, return true; on null → `super`.
- `onTextContextMenuItem`:
  - `undo`/`redo` → `commands`
  - `paste`/`pasteAsPlainText`/`cut` → `hardBreak()`, then the existing rule-11 body inside `mdUndo.group { }`
  - `copy` unchanged
- `onSelectionChanged` → `mdUndo?.onSelectionChanged(s, e)`; `onFocusChanged(false)` → `hardBreak()`; add a
  `performClick()` override (lint).
```kotlin
override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; taskDown = if (readOnly) -1 else taskMarkerAt(event.x, event.y) }
        MotionEvent.ACTION_UP -> if (taskDown >= 0) {
            val at = taskDown; taskDown = -1; val slop = ViewConfiguration.get(context).scaledTouchSlop
            if (abs(event.x - downX) < slop && abs(event.y - downY) < slop && event.eventTime - event.downTime < ViewConfiguration.getLongPressTimeout()) {
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                super.onTouchEvent(cancel); cancel.recycle()                 // caret doesn't move, IME not requested
                commands?.toggleTaskAt(at); performClick(); return true
            }
        }
        MotionEvent.ACTION_CANCEL -> taskDown = -1
    }
    return super.onTouchEvent(event)
}
/** EditText-local coords; the EditText's scrollX/scrollY are always 0 (EditorScrollView scrolls), so no scroll maths. */
private fun taskMarkerAt(x: Float, y: Float): Int {
    val l = layout ?: return -1; val t = text ?: return -1; val off = getOffsetForPosition(x, y); if (off < 0) return -1
    val line = l.getLineForVertical((y - totalPaddingTop).toInt()); val lx = x - totalPaddingLeft; val pad = 8 * resources.displayMetrics.density
    for (sp in t.getSpans((off - 3).coerceAtLeast(0), (off + 3).coerceAtMost(t.length), TaskSpan::class.java)) {
        val s = t.getSpanStart(sp); if (l.getLineForOffset(s) != line) continue
        val a = l.getPrimaryHorizontal(s); val b = l.getPrimaryHorizontal(t.getSpanEnd(sp))
        if (lx in (minOf(a, b) - pad)..(maxOf(a, b) + pad)) return s
    }
    return -1
}
```
**F. Controller + `FormatCommands` — sketch.** Don't make `EditorController` implement the internal `EditorCommands`
(exposed-supertype error). Pass an `object : EditorCommands` that delegates instead.
```kotlin
fun apply(edit: TextEdit) {
    val ed = editText.text ?: return; if (editText.readOnly) return
    val m = edit.minimize(ed); mdUndo.hardBreak(); editText.beginBatchEdit()
    try { mdUndo.group {
        val cs = BaseInputConnection.getComposingSpanStart(ed); val ce = BaseInputConnection.getComposingSpanEnd(ed)
        if (cs != -1 && cs <= m.end && ce >= m.start) BaseInputConnection.removeComposingSpans(ed)
        ed.replace(m.start, m.end, m.replacement); Selection.setSelection(ed, m.selStart.coerceIn(0, ed.length), m.selEnd.coerceIn(0, ed.length))
    } } finally { editText.endBatchEdit() }
}
fun perform(action: ToolbarAction) { when (action) {
    ToolbarAction.Cut -> editText.onTextContextMenuItem(android.R.id.cut); ToolbarAction.Copy -> editText.onTextContextMenuItem(android.R.id.copy)
    ToolbarAction.Paste -> editText.onTextContextMenuItem(android.R.id.paste); ToolbarAction.SelectAll -> editText.onTextContextMenuItem(android.R.id.selectAll)
    else -> {
        if (editText.readOnly || (action == ToolbarAction.Highlight && !highlightEnabled)) return
        val a = minOf(editText.selectionStart, editText.selectionEnd).coerceAtLeast(0); val b = maxOf(editText.selectionStart, editText.selectionEnd)
        val text = snapshot(); val nl = text.indexOf('\n', a)
        if (action.isInlineWrap && highlighter.lineInfoAt(a).type.isVerbatim) return   // Code: codeToggle handles fences itself
        FormatCommands.edit(action, text, a, b, highlightEnabled)?.let(::apply)
    }
} }
fun toggleTaskAt(offset: Int) { val e = SmartEdit.toggleTask(snapshot(), offset) ?: return; apply(e.copy(selStart = editText.selectionStart, selEnd = editText.selectionEnd)) }
```
- `FormatCommands.edit(action, text, s, e, enableHighlight: Boolean): TextEdit?` (pure, `s <= e`):
  - Bold/Italic/Strike/Highlight → `toggleWrap` with `"**"`/`"*"`/`"~~"`/`"=="`
  - Code → `SmartEdit.codeToggle(text, s, e)` (T04 decides inline backticks vs fenced block)
  - CodeBlock/Link/Quote → `toggleCodeBlock`/`insertLink`/`toggleQuote`
  - BulletList/NumberedList/TaskList → `toggleList(text, s, e, ListKind.BULLET/ORDERED/TASK)`
  - ClearFormatting → `clearFormatting(text, s, e, enableHighlight)` (the controller passes `highlightEnabled`); clipboard → `null`
  - HeadingCycle / SetHeading(l) → `keepSelection(cycleHeading(text, s) / setHeading(text, s, l), s, e)`
- `keepSelection(ed, s, e) = if (s == e) ed else ed.copy(selStart = mapPos(ed, s), selEnd = mapPos(ed, e))`, where
  `mapPos(ed, p) = when { p < ed.start -> p; p >= ed.end -> p + ed.replacement.length - (ed.end - ed.start); else -> ed.start + ed.replacement.length }`

**G. `EditorTestHost.kt`.**
- `fun AndroidComposeTestRule<*, ComponentActivity>.startEditor(text, sel = text.length, overlay: @Composable BoxScope.(EditorController) -> Unit = {}): EditorController`:
  `setContent { MdWriterTheme { Box(fillMaxSize) { AndroidView({ c.scrollView }, fillMaxSize); overlay(c) } } }` (`c = remember { EditorController(ctx, style) }`, style as in T07's layout-equality test), then `runBlocking { c.install(InstallRequest(text, sel, 0, false)) }`, `runOnUiThread { c.requestFocus() }`, `waitForIdle()`
- The rule comes from `…junit4.v2` (01 §1). If `ComponentActivity` is missing from the debug manifest, add `debugImplementation`
  of the existing `ui-test-manifest` catalog alias. If there is no alias, STOP-AND-ASK.

## Acceptance criteria
1. `UndoHistoryTest` (JVM) passes:
   - `typingRunIsOneStep`, `newWordStartsNewStep` ("hello world" → 2; undo → "hello "), `glideSpacePlusWordIsNewStep`, `pauseOverWindowBreaks`
   - `backspaceInsideRunShrinks`, `backspaceRunOverExistingTextIsOneStep`, `forwardDeleteRunIsOneStep`, `composingRewriteMerges`
   - `hardBreakPreventsMerge`, `groupIsOneStepWithSeveralOps`, `newEditClearsRedo`, `capAt1000Steps`, `charBudgetTrims`, `restoresBothSelectionEnds`
2. `FormatCommandsTest` (JVM):
   - Bold `make «this» bold` → `make **«this»** bold`; HeadingCycle / SetHeading(2) keep `«Title»` selected
   - a 2-line Code selection → a fence; `mapPos` before / inside / after / insert-at; clipboard actions → null
3. `EditorShortcutsTest` (JVM): every mapping in D, plus the negatives: Ctrl+C/X/V/A, Alt or Meta held, no Ctrl → null.
4. `SmartEditingTest` (instrumented; the IC is `editText.onCreateInputConnection(EditorInfo())`, a `SmartInputConnection`):
   - `commitNewlineContinuesList` (`- item|` → `- item\n- `, caret 9), `sendKeyEnterRenumbers` (`1. one|⏎2. two` → `1. one⏎2. ⏎3. two`)
   - `hardwareEnterViaDispatchKeyEvent`, `enterOnEmptyItemEndsList`, `enterClosesUnclosedFence`, `enterWhileComposingIsPlainNewline`
   - `deleteSurroundingAtContentStartRemovesMarker` (`- |item` → `|item`), `keyDelSameAsDeleteSurrounding`
   - `tabIndentsAndShiftTabOutdents`, `tabOutsideListInsertsTabAndKeepsFocus`, `smartEnterIsOneUndoStep`
5. `UndoRedoTest` (instrumented):
   - `typedWordsUndoByWord` ("one two" → undo → "one ")
   - `ctrlBThenCtrlZ` (Ctrl+B on «word» → `**word**` still selected; Ctrl+Z restores text and selection; Ctrl+Shift+Z redoes)
   - `canUndoFlowsTrack`, `platformUndoRoutesToOurs` (`android.R.id.undo` removes one word only), `pasteIsOneStep`, `installClearsHistory`
6. `TaskToggleTest` (instrumented; `dispatchTouchEvent` DOWN/UP 50 ms apart at the `[ ]` centre):
   - `- [ ] task` → `- [x] task`; caret unchanged; undo restores it
   - with the caret off-screen, `scrollView.scrollY` is unchanged. If it jumps: move the caret to the marker end first and
     log a deviation.
7. Checks:
   - `grep -rn beginBatchEdit app/src/main/kotlin/dev/mdwriter/editor/Restyler.kt` → nothing
   - `grep -rn allowUndo app/src/main/res/values/` → `false`
   - `make check` and `make test-device DEVICE=emulator-5554` are green
8. IME matrix (Gboard on the emulator; undo = `adb shell input keycombination KEYCODE_CTRL_LEFT KEYCODE_Z`) recorded in
   STATUS as OK or with a note:
   - (a) "Hello world" + undo → "Hello "; (b) autocorrect "teh " → "the " + undo stays consistent; (c) glide two words → 2 steps (or N/A with a reason)
   - (d) "- item" + Enter → "- "; (e) Enter on an empty item ends the list; (f) Backspace at `- |` removes the marker
   - (g) "```kotlin" + Enter closes the fence
   - (h) Enter right after typing a word: if the continuation is missing because Gboard is still composing, call
     `super.finishComposingText()` in the IC before `smart.onEnter()` and log it.
9. Screenshot after `adb shell input text "-%sitem"` + `input keyevent 66`: the second line starts with "- ", with the caret after it.

## Verification commands
```sh
make test
make install-debug DEVICE=emulator-5554 && adb -s emulator-5554 shell am start -n dev.mdwriter.debug/dev.mdwriter.MainActivity
adb -s emulator-5554 shell input text "-%sitem" && adb -s emulator-5554 shell input keyevent 66
adb -s emulator-5554 exec-out screencap -p > /tmp/t08-list.png
make test-device DEVICE=emulator-5554 && make check
```

## Pitfalls
- **Rule 3:** batch edits only around our *text* edits, never around reconcile. **Rule 5:** only `install()` calls
  `setText`; everything else uses `editable.replace`; no `clearSpans`.
- **Rule 11:** keep T05/T06's plain-text copy/cut/paste bodies when wrapping them in undo groups.
- One replace per smart edit. A post-hoc `TextWatcher` rewrite means 2 undo steps and watcher re-entry (markdown.md §8).
- Smart paths need a collapsed selection and no composing; Backspace is O(line). The 3-arg `commitText` (API 33)
  bypasses a 2-arg-only wrapper.
- `MarkdownEditText` fields are touched from super-constructor callbacks. Keep them nullable and use `?.`.

## Definition of done
- [ ] Names match 01 §3/§6.2; STATUS lists the additions (`UndoHistory.kt`, `FormatCommands.kt`, `highlightEnabled`, `isReadOnly`, `toggleTaskAt`), the SmartEdit mapping, the IME matrix and deviations.
- [ ] AC1–AC9 met; `make check` + `make test-device DEVICE=emulator-5554` are green.
- [ ] Commit `T08: undo/redo, smart Enter/Backspace/Tab, editor shortcuts, task toggle`.
