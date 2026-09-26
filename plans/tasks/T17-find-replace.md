# T17 — Find & replace

**Goal** Ctrl+F or the overflow `search` icon opens a find bar docked at the top of the editor. Typing a query
highlights every match in the text, shows `3 / 17`, and scrolls the focused match into view. Enter / Shift+Enter
(or the arrows) step through the matches, and "Match case" toggles case sensitivity. An optional replace row offers
Replace (one undo step each) and Replace all (one undo step in total). Back, Esc, or × closes the bar and leaves the
focused match selected.

**Depends on**
- **T13**: the overflow icon row with a `search` slot, the activity-level shortcut handler (Ctrl+N / Ctrl+L), the
  BackHandler ordering in `MdWriterRoot`, `EditorChrome` visibility, and `uiState.findOpen`.
- **T07 / T08**: `EditorController.apply(TextEdit)` counts as one undo step; `undo()` and `redo()`.
- **T05**: `EditorScrollView` honours `requestChildRectangleOnScreen`.
- **T15**, if already committed: the stats line, which this task hides while find is open.

Read the STATUS entries of T07, T08, T13 and T15 first. Where they name something differently, use their names.

**Read first**
- `plans/01-architecture.md` §4.4 (EditText inside the ScrollView), §6.2 (the controller `find*` API, which this
  task refines), §6.4 (`findOpen`), §7 (threading), §10 rules 3, 5 and 11.
- `plans/02-design-spec.md` §2 (tokens), §9 (overflow icon row), §11 (motion), §14 (icon names).
- `plans/research/platform.md` §10.1 (lines 685–691: shortcuts, Esc closes the find bar).
- `plans/research/factcheck.md` line 103 (open question 2: find was never specified; this task specifies it).

## Scope — In / Out
**In**
- Pure search and replace functions.
- `FindSession`, which owns the highlight state and hands it to TextView through the API 34 search-result
  highlight API.
- The controller API: `find`, `findNext`, `findPrevious`, `replaceCurrent`, `replaceAll`, `clearFind`,
  `findResult`.
- The `FindBar` UI, the Ctrl+F shortcut, Back and Esc handling, the overflow wiring, and hiding the chrome while
  the bar is open.
- The search colour tokens.

**Out**
- Regex and whole-word search: not planned. Do not add them.
- Search across files: the library search is T12's.
- Search inside Preview: not planned.
- Accessibility audit and large-screen polish belong to **T20**. Performance sign-off belongs to **T21**.
- Do not touch span classes, the restyler, or `MdEditable`. Search highlights are **not** spans.

## Files to create / modify
(`M` = `app/src/main/kotlin/dev/mdwriter`, `U` = `app/src/test/kotlin/dev/mdwriter`,
`I` = `app/src/androidTest/kotlin/dev/mdwriter`)
- `M/editor/TextSearch.kt` — new, pure (no `android.*`). Contains `findAll`, `indexAtOrAfter`, `shift`,
  `dropRange`, `window`, `replaceOne`, `replaceAll`.
- `M/editor/FindSession.kt` — new. `data class FindResult` plus `internal class FindSession` (a TextWatcher and
  the TextView highlight calls).
- `M/editor/EditorController.kt` — modify. Adds the find API from Reference §C, `selectedTextForFind()`, and
  `install()` calls `findSession.clear()`.
- `M/editor/spans/EditorStyle.kt` and its `WriterColors` builder — add `searchMatchColor: Int` and
  `searchFocusedColor: Int`. `setStyle` applies them.
- `M/ui/theme/WriterColors.kt` — add the tokens `searchMatch` and `searchMatchFocused` to all 3 palettes.
- `plans/02-design-spec.md` §2 — add the same two rows to the token table.
- `M/ui/find/FindBar.kt` — new. `FindBar` (stateless) and `FindBarHost` (the controller wiring).
- `M/ui/editor/EditorScreen.kt` — modify. A `Column { FindBarHost?; editor Box(weight 1) }`, and the chrome and
  stats are hidden while find is open.
- `M/ui/editor/EditorViewModel.kt` — modify. `openFind()`, `closeFind()`, `findFocusToken`, and close on
  document change.
- `M/ui/editor/OverflowMenu.kt` — modify. The `search` icon calls `onFind`.
- `M/ui/root/MdWriterRoot.kt` — modify. `BackHandler(enabled = findOpen)` in T13's order.
- `M/MainActivity.kt` (or wherever T13 put the shortcut dispatcher) — Ctrl+F, plus a "Find" entry in
  `onProvideKeyboardShortcuts` if T13 implemented that.
- `app/src/main/res/values/strings.xml` — strings from step 6.
- `app/src/main/res/drawable/ic_{search,find_replace,match_case,expand_less,expand_more,close}.xml` — should exist
  from T02 (`ls`). If any is missing, download it per 02 §14.
- Tests:
  - `U/editor/TextSearchTest.kt`
  - `U/ui/theme/SearchColorsTest.kt`
  - `U/ui/find/FindBarTest.kt` (Robolectric + Compose)
  - `I/editor/FindDeviceTest.kt`

## Steps
1. **Verify the platform API before writing code.** Run:

   `javap -cp ~/Library/Android/sdk/platforms/android-37.0/android.jar android.widget.TextView | grep -iE "searchresult|bringPointIntoView"`

   Expected methods:
   - `setSearchResultHighlights(int...)`
   - `getSearchResultHighlights()`
   - `setFocusedSearchResultIndex(int)`
   - `getFocusedSearchResultIndex()`
   - `setSearchResultHighlightColor(int)`
   - `setFocusedSearchResultHighlightColor(int)`
   - the constant `FOCUSED_SEARCH_RESULT_INDEX_NONE`
   - `bringPointIntoView(int, boolean)`

   Use the exact names printed and paste the output into STATUS. If these methods are absent, **STOP-AND-ASK**. Do
   not fall back to spans.
2. **`TextSearch` plus `TextSearchTest`** (Reference §A). Write the tests first. Every test case:

   | Input | Call | Expected |
   |---|---|---|
   | `"cat Cat CAT"`, query `"cat"` | case-insensitive | 3 matches `[0,3,4,7,8,11]` |
   | same | case-sensitive | `[0,3]` |
   | `"aaaa"` / `"aa"` | find | `[0,2,2,4]` (non-overlapping, left to right) |
   | `"a👍b👍"` / `"👍"` | find | `[1,3,4,6]` |
   | `"Café CAFÉ café"` / `"café"` | case-insensitive | 3 matches |
   | same | case-sensitive | `[10,14]` |
   | `"Straße STRASSE"` / `"strasse"` | case-insensitive | only `[7,14]` (no full case folding; documented limitation) |
   | empty query | find | empty |
   | query longer than text | find | empty |
   | `"aaaa"` / `"a"`, `limit = 3` | find | 3 matches |
   | `"a\nb a"` / `"a"` | find | a match right after `\n` is found |

   Then the range helpers:
   - `indexAtOrAfter`: exact start, between matches, past the last match (wraps to 0), empty (−1).
   - `shift` with an insert before a match, an insert at a match start (the match moves), an insert at a match end
     (the match stays), an insert strictly inside a match (dropped), a deletion that spans a match (dropped), and
     a replace after all matches (unchanged).
   - `dropRange`.
   - `window` with n = 1000 and max = 500: focused 900 → first 500 and local index 400; focused 10 → first 0;
     focused −1 → local −1.

   Then replacement:
   - `replaceAll("aaa", findAll("a"), "aa", caret = 3)` produces the text `"aaaaaa"` with the caret at 6.
   - `replaceAll` with no matches returns null.
   - `replaceAll` moves a caret between matches by the accumulated delta. A caret inside a match lands at the end
     of that match's replacement.
   - `replaceCurrentNeverReselectsReplacedText`: on `"cat cat cat"`, replace index 1 with `"cats"` and simulate the
     minimized insert `shift(r, 7, 0, 1)`, then `dropRange(r, 4, 8)`. The result is `[0,3,9,12]`, and
     `indexAtOrAfter(r, 8) == 1`.
3. **Colour tokens** (02 §2, new rows; also add them to `WriterColors` and the three palettes):

   | Token | Light | Dark | Pure black | Use |
   |---|---|---|---|---|
   | `searchMatch` | `#4DFFB000` | `#26FFB84D` | `#26FFB84D` | every find match |
   | `searchMatchFocused` | `#99FF9F00` | `#55FFB84D` | `#55FFB84D` | the focused match |

   `SearchColorsTest` (JVM) checks each palette:
   - WCAG contrast of `text` over (`searchMatchFocused` composited on `bg`) is ≥ 4.5.
   - The luminance of the composited focused colour differs from the composited `searchMatch` by ≥ 0.03.

   Map both tokens into `EditorStyle` (`toArgb()`). In `setStyle`, call `editText.setSearchResultHighlightColor(…)`
   and `editText.setFocusedSearchResultHighlightColor(…)`.
4. **`FindSession` and the controller API** (Reference §B, §C):
   - A TextWatcher is attached only while there are ranges. It shifts the ranges on **every** text change: typing,
     undo, replace, and edits made by other tasks. This keeps highlights aligned before the debounced re-search
     arrives.
   - `install()` calls `findSession.clear()` first.
   - Track `readOnly` from the `InstallRequest`. `replace*` does nothing (returns false / 0) when read-only.
5. **`FindBar` UI** (Reference §D, design below). `FindBarHost` runs the debounce
   `LaunchedEffect(query, matchCase, editVersion) { delay(150); controller.find(query, matchCase) }`. A key change
   cancels the previous run, which is the debounce. `editVersion` comes from `controller.edits`.
6. **Wiring:**
   - `vm.openFind()`: `findOpen = true`, `findFocusToken++`.
   - `vm.closeFind()`: `findOpen = false`.
   - Close find when `uiState.doc` changes.
   - Overflow `search` calls `vm.openFind()` and dismisses the menu.
   - Ctrl+F: add `AppCommand.Find` and `KEYCODE_F -> Find` (no shift) to T13's `AppShortcuts.map` in `ui/root/AppCommands.kt`
     (reached because T08's `onKeyShortcut` returns false for F); MdWriterRoot's `commands` collector calls `vm.openFind()`. When find is already open, the token change re-focuses the field and selects all.
   - Back: T13's `RootBackHandlers` already composes `BackHandler(enabled = ui.findOpen)` (below drawer, preview and
     settings, above the editor defaults). Point it at the find bar's `onClose`; do not compose a second find handler.
     Keep T13's mutual exclusion: opening the drawer or preview calls `closeFind()`.
   - In `EditorScreen`, the glyph row and stats line are visible only when `chromeVisible && !findOpen`. The
     status-bar protection strip is hidden while find is open, because the bar paints `surface` behind the status
     bar itself.
   - Strings: `find_hint` "Find", `replace_hint` "Replace with", `find_previous` "Previous match",
     `find_next` "Next match", `find_close` "Close find", `find_match_case` "Match case", `find_replace_toggle`
     "Replace", `find_replace_one` "Replace", `find_replace_all` "All" (content description "Replace all"),
     `find_replaced` "%d replaced", `shortcut_find` "Find".
7. **`FindBarTest`** (Robolectric, `createComposeRule` from `androidx.compose.ui.test.junit4.v2`, stateless
   `FindBar` with recording lambdas):
   - The counter shows `3 / 17` for `FindResult(17, 2, false)` with a non-empty query, and `3 / 100000+` when
     truncated. It does not exist for an empty query.
   - `performKeyInput { pressKey(Key.Enter) }` on the `find.query` tag calls `onNext` once.
     `withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) }` calls `onPrevious` once. `Key.Escape` calls `onClose`.
     `performImeAction()` calls `onNext`.
   - Previous and next are `assertIsNotEnabled()` when the count is 0.
   - Clicking match case calls `onMatchCase(true)`. With `matchCase = true`, the node passes `assertIsOn()`.
   - The replace toggle shows the `find.replace` field and the Replace and All buttons. With `readOnly = true`,
     the toggle does not exist.
   - Every icon button has its content description.
   - Under `@Config(qualifiers = "w360dp-h800dp")`, `find.query` passes `assertWidthIsAtLeast(120.dp)`. With
     replace open, `find.replace` passes `assertWidthIsAtLeast(96.dp)`.
8. **`FindDeviceTest`** (reuse the T07/T08 instrumented harness: a controller hosted in a compose rule, with
   `install` on a real document). The cases:
   1. `"cat cat Cat"`, `find("cat", false)`: count 3, index 0, `getSearchResultHighlights()` equals
      `[0,3,4,7,8,11]`, and the focused index is 0.
   2. `findNext` ×3 wraps to 0. Then `findPrevious` gives 2.
   3. `find("cat", true)` gives count 2.
   4. `apply(TextEdit(0,0,"x",1,1))`: **immediately**, with no delay, the highlights are `[1,4,5,8,9,12]`.
   5. `replaceCurrent("dog")` at index 0: the text becomes `"dog cat Cat"`, the focused match is `[4,7)`, and one
      `undo()` restores the text.
   6. `replaceAll("cow")` returns 3 and the text becomes `"cow cow cow"`. **One** `undo()` restores
      `"cat cat Cat"`.
   7. `clearFind(selectFocused = true)`: the selection equals the focused match and the highlights are empty.
   8. On a 2,000-line document with a unique word on line 1,500: after `find`, `scrollView.scrollY > 0`, and that
      line's top lies inside `childViewport` (or `scrollY..scrollY + height`).
   9. Read-only install: `replaceCurrent` returns false and the text is unchanged.
   10. On a 300,000-char document, `find("e", false)` finishes in under 500 ms, and
       `getSearchResultHighlights().size ≤ 2 * TextSearch.HIGHLIGHT_WINDOW`.
9. **Manual emulator pass and screenshots** (see Verification). Then update `01-architecture.md`:
   - §6.2: the final find signatures.
   - §3: `editor/TextSearch.kt` and `editor/FindSession.kt`.
   - §7: a row "Find search: snapshot on main, search on Default, 150 ms debounce".

   Update 02 §2 with the tokens, add the STATUS entry, and commit.

## Reference code
**§A TextSearch.kt — copy the logic verbatim; KDoc is up to you.**
```kotlin
object TextSearch {
    const val MAX_MATCHES = 100_000
    const val HIGHLIGHT_WINDOW = 500                 // ranges handed to TextView (it builds a Path per range on edit)
    private val EMPTY = IntArray(0)
    /** Non-overlapping matches left to right as [s0,e0,s1,e1,…]. Case-insensitive = per-char (no ß→SS folding). */
    fun findAll(text: String, query: String, matchCase: Boolean, limit: Int = MAX_MATCHES): IntArray {
        if (query.isEmpty() || query.length > text.length) return EMPTY
        var out = IntArray(64); var n = 0; var from = 0
        while (n / 2 < limit) {
            val i = text.indexOf(query, from, ignoreCase = !matchCase); if (i < 0) break
            if (n + 2 > out.size) out = out.copyOf(out.size * 2)
            out[n++] = i; out[n++] = i + query.length; from = i + query.length
        }
        return out.copyOf(n)
    }
    /** Pair index of the first match with start >= offset; wraps to 0; -1 when empty. */
    fun indexAtOrAfter(r: IntArray, offset: Int): Int {
        if (r.isEmpty()) return -1
        var i = 0; while (i < r.size && r[i] < offset) i += 2
        return if (i >= r.size) 0 else i / 2
    }
    /** Re-map after replacing [start, start+removed) with `added` chars: keep before, shift after, drop overlapping. */
    fun shift(r: IntArray, start: Int, removed: Int, added: Int): IntArray {
        val d = added - removed; val end = start + removed; val out = IntArray(r.size); var n = 0
        var i = 0
        while (i < r.size) {
            val s = r[i]; val e = r[i + 1]
            when { e <= start -> { out[n++] = s; out[n++] = e }; s >= end -> { out[n++] = s + d; out[n++] = e + d } }
            i += 2
        }
        return out.copyOf(n)
    }
    /** Drop every match intersecting [from, to). */
    fun dropRange(r: IntArray, from: Int, to: Int): IntArray {
        val out = IntArray(r.size); var n = 0; var i = 0
        while (i < r.size) { if (r[i + 1] <= from || r[i] >= to) { out[n++] = r[i]; out[n++] = r[i + 1] }; i += 2 }
        return out.copyOf(n)
    }
    data class HighlightWindow(val ranges: IntArray, val focused: Int, val first: Int)
    fun window(r: IntArray, focused: Int, max: Int = HIGHLIGHT_WINDOW): HighlightWindow {
        val n = r.size / 2; if (n <= max) return HighlightWindow(r, focused, 0)
        val first = (focused.coerceAtLeast(0) - max / 2).coerceIn(0, n - max)
        return HighlightWindow(r.copyOfRange(2 * first, 2 * (first + max)), if (focused < 0) -1 else focused - first, first)
    }
    fun replaceOne(r: IntArray, index: Int, repl: String): TextEdit {
        val s = r[2 * index]; return TextEdit(s, r[2 * index + 1], repl, s + repl.length, s + repl.length)
    }
    /** ONE edit covering first..last match (keeps the undo step and the restyle range small). */
    fun replaceAll(text: String, r: IntArray, repl: String, caret: Int): TextEdit? {
        if (r.isEmpty()) return null
        val a = r[0]; val b = r[r.size - 1]; val sb = StringBuilder(b - a + (r.size / 2) * repl.length)
        var pos = a; var delta = 0; var mapped = -1; var i = 0
        while (i < r.size) {
            val s = r[i]; val e = r[i + 1]
            sb.append(text, pos, s).append(repl); pos = e
            if (mapped < 0) { if (e <= caret) delta += repl.length - (e - s) else if (s < caret) mapped = s + delta + repl.length }
            i += 2
        }
        val c = if (mapped >= 0) mapped else caret + delta
        return TextEdit(a, b, sb.toString(), c, c)
    }
}
```
`TextEdit` comes from `dev.mdwriter.markdown`. Check its KDoc: `selStart` / `selEnd` must be offsets in the text
*after* the edit, the same convention as `SmartEdit`. If the convention differs, adapt `replaceOne` /
`replaceAll` and their tests.

**§B FindSession.kt — sketch.**
```kotlin
data class FindResult(val count: Int, val index: Int, val truncated: Boolean) { companion object { val NONE = FindResult(0, -1, false) } }
internal class FindSession(private val et: MarkdownEditText) {
    val result = MutableStateFlow(FindResult.NONE)
    var query = ""; private set; var matchCase = false; private set
    private var ranges = IntArray(0); private var focused = -1; private var truncated = false; private var watching = false
    private var cs = 0; private var cRem = 0; private var cAdd = 0
    private val watcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { cs = start; cRem = before; cAdd = count }
        override fun afterTextChanged(s: Editable?) {
            if (ranges.isEmpty()) return
            val old = focusedStart(); ranges = TextSearch.shift(ranges, cs, cRem, cAdd)
            val mapped = when { old < 0 -> 0; old >= cs + cRem -> old + cAdd - cRem; old < cs -> old; else -> cs }
            focused = TextSearch.indexAtOrAfter(ranges, mapped); push(reveal = false)
        }
    }
    fun focusedStart() = if (focused >= 0) ranges[2 * focused] else -1
    fun focusedRange(): IntRange? = if (focused >= 0) ranges[2 * focused] until ranges[2 * focused + 1] else null
    fun set(q: String, mc: Boolean, r: IntArray, idx: Int, trunc: Boolean, reveal: Boolean) {
        query = q; matchCase = mc; ranges = r; focused = idx; truncated = trunc
        if (!watching) { et.addTextChangedListener(watcher); watching = true }
        push(reveal)
    }
    fun step(d: Int) { val n = ranges.size / 2; if (n == 0) return; focused = ((focused + d) % n + n) % n; push(reveal = true) }
    fun afterReplace(s: Int, replLen: Int) {             // never re-target the text we just wrote
        ranges = TextSearch.dropRange(ranges, s, s + replLen); focused = TextSearch.indexAtOrAfter(ranges, s + replLen); push(true)
    }
    private fun push(reveal: Boolean) {
        val w = TextSearch.window(ranges, focused)
        et.setSearchResultHighlights(*w.ranges)
        et.setFocusedSearchResultIndex(if (w.focused >= 0) w.focused else TextView.FOCUSED_SEARCH_RESULT_INDEX_NONE)
        if (reveal && focused >= 0) et.bringPointIntoView(ranges[2 * focused], true)   // 2-arg: the EditText is NOT focused
        result.value = FindResult(ranges.size / 2, focused, truncated)
    }
    fun clear() {
        if (watching) { et.removeTextChangedListener(watcher); watching = false }
        ranges = IntArray(0); focused = -1; query = ""
        et.setSearchResultHighlights(*IntArray(0)); et.setFocusedSearchResultIndex(TextView.FOCUSED_SEARCH_RESULT_INDEX_NONE)
        result.value = FindResult.NONE
    }
}
```

**§C Controller additions — sketch; these signatures are the new contract (01 §6.2).**
```kotlin
val findResult: StateFlow<FindResult> get() = findSession.result
suspend fun find(query: String, matchCase: Boolean): FindResult {           // main thread; was non-suspend in 01
    if (query.isEmpty()) { findSession.clear(); return FindResult.NONE }
    val newQuery = query != findSession.query || matchCase != findSession.matchCase
    val text = snapshot(); val v = version
    val r = withContext(Dispatchers.Default) { TextSearch.findAll(text, query, matchCase) }
    if (v != version) return findResult.value                                // stale; the edit re-triggers find
    val anchor = if (newQuery) editText.selectionStart.coerceAtLeast(0) else findSession.focusedStart().coerceAtLeast(0)
    findSession.set(query, matchCase, r, TextSearch.indexAtOrAfter(r, anchor), r.size / 2 >= TextSearch.MAX_MATCHES, reveal = newQuery)
    return findResult.value
}
fun findNext() = findSession.step(+1); fun findPrevious() = findSession.step(-1)
fun replaceCurrent(replacement: String): Boolean {
    val m = findSession.focusedRange() ?: return false; if (readOnly) return false
    apply(TextEdit(m.first, m.last + 1, replacement, m.first + replacement.length, m.first + replacement.length))
    findSession.afterReplace(m.first, replacement.length); return true
}
suspend fun replaceAll(replacement: String): Int {
    val q = findSession.query; if (q.isEmpty() || readOnly) return 0
    val mc = findSession.matchCase; val text = snapshot(); val v = version; val caret = editText.selectionStart
    val (edit, n) = withContext(Dispatchers.Default) {
        val r = TextSearch.findAll(text, q, mc, Int.MAX_VALUE); TextSearch.replaceAll(text, r, replacement, caret) to r.size / 2
    }
    if (v != version || edit == null) return 0
    apply(edit); return n                                                    // exactly ONE undo step
}
fun clearFind(selectFocused: Boolean = false) {
    val m = findSession.focusedRange(); findSession.clear()
    if (selectFocused && m != null) editText.setSelection(m.first, m.last + 1)
}
fun selectedTextForFind(): String? { /* selection 1..200 chars, no '\n' → that text, else null */ }
```

**§D FindBar design and key handling (sketch).** The bar is a `Surface(color = surface)` padded by
`WindowInsets.safeDrawing.only(Top + Horizontal)`, with a 1 px `divider` bottom border, flat (no elevation). It
enters and exits with `AnimatedVisibility(expandVertically + fadeIn(120) / shrinkVertically + fadeOut(90))`.
- **Row 1 (56 dp):**
  - `ic_search`, 20 dp, `textSecondary`, decorative.
  - The query `BasicTextField(TextFieldValue)` (tag `find.query`, `weight(1f)`). It sits on a `surfaceHover`
    capsule 40 dp high with corner `WriterDimens.searchFieldCornerRadius`. Text is 16 sp `text` with an `accent`
    cursor; the placeholder "Find" is `textSecondary`.
  - Keyboard options: `singleLine`, `KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false)`,
    `KeyboardActions(onSearch = { onNext() })`.
  - The counter: 12 sp `textSecondary`, `liveRegion = Polite`. Format: `"${index + 1} / $count"`, with `+` when
    truncated, and `"0 / 0"` when there are no hits. After Replace all it shows `find_replaced` for 2 s.
  - `IconButton`s `ic_expand_less` (Previous match), `ic_expand_more` (Next match), `ic_close` (Close find).
- **Row 2 (48 dp):**
  - `IconToggleButton`s `ic_match_case` and `ic_find_replace` (the latter is absent when `readOnly`). Checked:
    tint `text` on a `surfaceHover` circle. Unchecked: `textSecondary`.
  - When replace is on: the replacement `BasicTextField` (tag `find.replace`, `weight(1f)`, hint "Replace with"),
    then `TextButton` "Replace" and `TextButton` "All". Both are enabled only when `count > 0`.
- On first composition, request focus on the query field and select all. The query is pre-filled with
  `controller.selectedTextForFind()`. Each `findFocusToken` change repeats this.
- `onClose`:
  1. `controller.clearFind(selectFocused = true)`
  2. `controller.requestFocus()`
  3. `vm.closeFind()`
- A `DisposableEffect` calls `controller.clearFind()` as a safety net.
```kotlin
Modifier.onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (e.key) {
        Key.Enter, Key.NumPadEnter -> { if (e.isShiftPressed) onPrevious() else onNext(); true }
        Key.Escape -> { onClose(); true }
        else -> false
    }
}
```
Keep `query`, `matchCase`, `replaceOpen` and `replacement` in `rememberSaveable` inside `FindBarHost`
(`TextFieldValue.Saver` for the fields). `FindBar` itself takes plain values and lambdas, so it is testable.

## Acceptance criteria
1. `make test` passes `TextSearchTest` (every case in step 2), `SearchColorsTest` and `FindBarTest` (every case in
   step 7).
2. `make test-device DEVICE=emulator-5554` passes `FindDeviceTest` cases 1–10.
3. The javap output from step 1 is in STATUS, and the code uses exactly those method names.
4. On the emulator, Ctrl+F (hardware keyboard, or `adb shell input keycombination 113 34`) opens the bar with the
   IME on the query field. Typing `the` in the welcome note gives:
   - every match tinted `searchMatch` and the focused one tinted `searchMatchFocused`;
   - the counter `1 / N`;
   - Enter moves to `2 / N` and scrolls to it;
   - Back hides the IME, a second Back closes the bar and leaves the focused match selected (our selection pill
     from T09 shows, no system toolbar).
5. Screenshots `t17-find-light.png`, `t17-find-dark.png` and `t17-replace-360dp.png` (replace row open at 360 dp):
   - all icons are visible;
   - nothing is clipped;
   - the query field is at least as wide as the Replace button.
6. With find open, typing in the editor keeps the highlights aligned on every frame. There is no highlight on
   stale text and no crash. The counter updates within about 300 ms of the last keystroke.
7. Replace all on the welcome note, then one Ctrl+Z: the note is byte-identical to before (compare
   `controller.snapshot()` before and after in the device test; eyeball it on the emulator).
8. `make check` is green. `grep -rn "setSpan\|beginBatchEdit" app/src/main/kotlin/dev/mdwriter/editor/FindSession.kt`
   finds nothing.

## Verification commands
```sh
javap -cp ~/Library/Android/sdk/platforms/android-37.0/android.jar android.widget.TextView | grep -iE "searchresult|bringPointIntoView"
make test && make check
make test-device DEVICE=emulator-5554
make install-debug DEVICE=emulator-5554 && make run-debug DEVICE=emulator-5554
adb -s emulator-5554 shell input keycombination 113 34        # CTRL_LEFT + F
adb -s emulator-5554 shell input text the
adb -s emulator-5554 exec-out screencap -p > /tmp/t17-find-light.png
adb -s emulator-5554 shell cmd uimode night yes; adb -s emulator-5554 exec-out screencap -p > /tmp/t17-find-dark.png
adb -s emulator-5554 shell wm size                            # read physical width W px, then: 360 dp →
adb -s emulator-5554 shell wm density $((W*160/360)); adb -s emulator-5554 exec-out screencap -p > /tmp/t17-replace-360dp.png
adb -s emulator-5554 shell wm density reset; adb -s emulator-5554 shell cmd uimode night no
```

## Pitfalls
- **Use the two-argument `bringPointIntoView(start, true)`.** While the find field has focus the EditText is
  unfocused, and the one-argument form only scrolls a focused TextView. The call goes up through
  `requestRectangleOnScreen` to `EditorScrollView`. When typewriter mode (T15) is on, the match is centred at
  45 %, which is intended.
- Highlights are TextView search results, not spans (hard rules 5 and 6). They are **not** shifted by the
  platform on edits. Offsets that are out of range after a deletion can crash at draw time, hence the watcher
  `shift`.
- Never search on the main thread. Snapshot on main, search on Default, and drop the result if `version` changed.
- `replaceCurrent` must `afterReplace` (the `dropRange` step). Otherwise `"cat"`→`"cats"` keeps the old range
  (the minimized edit is an insert *at* the match end) and the next Replace yields `"catss"`.
- Replace all is **one** `apply()` covering the first to last match. Do not call `apply` in a loop (that is N undo
  steps and N restyles). Do not replace the whole document either (it restyles everything).
- Hard rule 3: `apply()` already does the batch edit. Do not add another around the find calls.
- Hard rule 11 does not change here: the find field is a Compose field, and its clipboard is Compose's own.
- Esc in the EditText falls back to BACK (Generic.kcm), and the BackHandler catches it. Esc in the query field is
  handled explicitly by `onPreviewKeyEvent`.
- `performKeyInput` / `withKeyDown` need `@OptIn(ExperimentalTestApi::class)`. Import `createComposeRule` from
  `androidx.compose.ui.test.junit4.v2` (01 §1).
- Large match counts: only 500 ranges go to TextView (`HIGHLIGHT_WINDOW`), because TextView rebuilds a Path per
  range after each edit. The counter still reports the full count, up to 100,000.

## Definition of done
- [ ] All files above exist or are modified; no hard rule is broken; the find highlights use no spans.
- [ ] `make check` is green, and `make test-device DEVICE=emulator-5554` passes `FindDeviceTest`.
- [ ] Screenshots from acceptance item 5 were taken and described in STATUS, along with the javap output.
- [ ] `01-architecture.md` (§3, §6.2, §7) and `02-design-spec.md` §2 are updated in the same commit.
- [ ] The STATUS.md entry is appended.
- [ ] One commit: `T17: find & replace bar with TextView search highlights`.
