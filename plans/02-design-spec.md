# 02 — Design spec (the iA Writer look, without iA's name, logo or trademarks)

Authoritative values for every UI task. Evidence, screenshots analysis and alternatives: `plans/research/design.md`.
Where this file differs from `design.md`, **this file wins** (conflicts were resolved in `research/factcheck.md` §6).

Guiding idea: **the page is the app.** Grey-white paper, near-black text, one electric-blue caret, Markdown stays
visible but quiet, nothing else on screen while you type.

---

## 1. Fonts

- Bundle the **iA Writer Duo S, Quattro S, Mono S** static TTFs (SIL OFL 1.1, Reserved Font Name "iA Writer"),
  **byte-for-byte unmodified** (no subsetting/conversion — that would be a "Modified Version" and force a rename).
- Files (12, total 1,303,688 B) — download URLs in `plans/reference/fonts.md`:
  `res/font/{duo,quattro,mono}_{regular,bold,italic,bold_italic}.ttf` + family XMLs `duo.xml`, `quattro.xml`, `mono.xml`
  that **declare weights explicitly** (Quattro Bold files wrongly say weight 400).
- Ship the licence as `app/src/main/assets/licenses/iA-Writer-fonts-OFL.txt` and show it from Settings › About.
- Default editor font **Duo**. Code spans/blocks, tables, front matter always **Mono**. UI chrome (drawer, sheets,
  menus) uses the same family as the editor at smaller sizes.
- Metrics to use in math: UPM 1000, natural line box **1.30 em**, "n-width" = **0.6 × textSize** (Duo `m/w` are 0.9).

## 2. Colour tokens

| Token | Light | Dark | Pure black (option) | Use |
|---|---|---|---|---|
| `bg` | `#F7F7F7` | `#1A1A1A` | `#000000` | editor, window, splash |
| `surface` | `#FCFCFC` | `#141414` | `#0A0A0A` | drawer, sheets, toolbar pill, menus |
| `surfaceHover` | `#EFEFEF` | `#242424` | `#161616` | pressed/selected row |
| `text` | `#1A1A1A` | `#D0D0D0` | `#C8C8C8` | body, headings, **and the prose markers `# * _ - + 1.`** |
| `textSecondary` | `#6E6E6E` | `#8C8C8C` | `#8A8A8A` | excerpts, dates, stats, H6, done tasks, settings subtitles |
| `markup` | `#868686` | `#7A7A7A` | `#707070` | non-prose syntax (see §4) |
| `focusDim` | `#C0C0C0` | `#5E5E5E` | `#4A4A4A` | text outside the focus in Focus Mode (decorative, not for reading) |
| `accent` | `#00B2FF` | `#00B2FF` | `#00B2FF` | caret, selection handles, switches, active-file bar, "new" icon |
| `selection` | `#4000B2FF` | `#4D00B2FF` | `#5900B2FF` | selected-text background (ARGB) |
| `codeBg` | `#EDEDED` | `#242424` | `#141414` | code span background, code-block line band |
| `highlightBg` | `#59FFD900` | `#2DFFD900` | `#2DFFD900` | `==mark==` background |
| `highlightText` | = `text` | `#DAD094` | `#DAD094` | text inside `==mark==` |
| `divider` | `#E2E2E2` | `#2E2E2E` | `#1F1F1F` | 1 **px** hairlines |
| `scrim` | `#52000000` | `#99000000` | `#B3000000` | drawer/sheet scrim |
| `danger` | `#D93A2B` | `#FF6B5E` | `#FF6B5E` | "Delete" text only |

- **No dynamic colour (Material You).** Build `lightColorScheme/darkColorScheme` from the tokens
  (`background=bg, surface=surface, surfaceContainer*=surface, onBackground=onSurface=text,
  onSurfaceVariant=textSecondary, outline=outlineVariant=divider, primary=accent, onPrimary=#FFFFFF,
  secondary=text, scrim=scrim, error=danger, surfaceTint=Transparent`). Elevation everywhere: `tonalElevation = 0.dp`,
  `shadowElevation = 0.dp`. **Flat.**
- Never use `accent` for text or icon-only affordances on light backgrounds (2.2:1 contrast).
- Theme setting: System (default) · Light · Dark, plus "Pure black in dark".
- System bars transparent (edge-to-edge); light theme → dark bar icons, dark theme → light icons.
- **Focus Mode overlay alpha** (the dim is drawn as a translucent `bg` overlay, see §5): per theme
  `alpha = (focusDim − text) / (bg − text)` on the grey channel → light ≈ **0.75**, dark ≈ **0.63**, black ≈ **0.63** (the formula wins; tokens #4A4A4A/#C8C8C8/#000000).

## 3. Typography & layout

| Setting | Phone (< 600 dp) | Medium (600–839 dp) | Expanded (≥ 840 dp) |
|---|---|---|---|
| Body size (step "M") | 17 sp | 18 sp | 18 sp |
| Size steps (setting) | XS 15 · S 16 · **M 17** · L 19 · XL 21 · XXL 24 sp (+1 sp at ≥ 600 dp) | | |
| Line pitch (Duo/Mono) | **1.65 ×** size | 1.75 × | 1.75 × |
| Line pitch (Quattro) | 1.55 × | 1.65 × | 1.65 × |
| Side margin (min, each side) | 24 dp | 32 dp | 48 dp |
| Max measure | none (fills width ≈ 36–40 chars) | 64 / 72 / 80 chars (setting, default 64) | same |
| Heading-marker gutter (hang) | **0** (markers inline) | 4 chars | 6 chars |
| Top room (first line below status bar) | 56 dp | 64 dp | 72 dp |
| Bottom room (end of document) | 50 % of window height | same | same |
| Typewriter mode top/bottom room | 45 % / 55 % of window height | same | same |

- Column math (≥ 600 dp): `columnPx = measure × 0.6 × textSizePx`; `side = max(minMargin, (width − columnPx)/2)`;
  EditText `paddingStart = side − gutterPx`, `paddingEnd = side`, with the gutter **clamped** so start padding never goes
  negative: `gutterPx = min(gutterChars × 0.6 × textSizePx, side)` (the hang only borrows from the start margin; e.g.
  600 dp, measure 64 at 18 sp → side 32 dp < 43 dp unclamped gutter).
- Line pitch is implemented with `setLineSpacing(extraPx, 1f)` where `extraPx = pitch − 1.30 × textSize`, plus a
  caret drawable that extends `extraPx/2` above and below the glyph box so the caret spans the full pitch (iA look).
- **No extra paragraph spacing** — a blank line is one pitch. Headings get no extra padding.
- Letter spacing 0. Break strategy SIMPLE, hyphenation NONE (EditText defaults; cheaper while typing).

### Heading scale (editor = preview) — the user explicitly wants headings bigger

| Level | × body | @17 sp | Weight | Colour |
|---|---|---|---|---|
| H1 `#` / setext `===` | 1.60 | 27.2 | Bold | text |
| H2 `##` / setext `---` | 1.40 | 23.8 | Bold | text |
| H3 | 1.25 | 21.25 | Bold | text |
| H4 | 1.10 | 18.7 | Bold | text |
| H5 | 1.00 | 17 | Bold | text |
| H6 | 1.00 | 17 | Bold | textSecondary |

The heading **size span starts after the marker run** (`## `), so the `#` marks stay at body size and body colour
(iA-faithful) and the hang width is predictable. The line grows as soon as the first heading character is typed.

## 4. Per-construct editor styling (maps `MdKind` → look)

"markup" = grey token. "—" = no span at all (fewer spans = faster).

| MdKind / construct | Editor look |
|---|---|
| `HEADING(level)` | size × scale + bold over **content after the marker** (H6 also `textSecondary`) |
| `HEADING_MARKER` (ATX `#` run) | — (text colour, body size). ≥ 600 dp: hangs in the gutter |
| `HEADING_MARKER` (setext underline `===`/`---`) | markup |
| `EMPHASIS` / `STRONG` | italic / bold (bold+italic combine to BoldItalic) over the whole construct incl. delimiters |
| `EMPHASIS_MARKER` `* _ ** __` | — (text colour; iA-faithful). Constant `DIM_EMPHASIS_MARKERS = false` |
| `EMPHASIS_MARKER` `~ ~~ ==` | markup |
| `STRIKETHROUGH` | strike-through (whole construct) |
| `HIGHLIGHT` | `highlightBg` + `highlightText` (only when the setting enables `==`) |
| `CODE_SPAN` / `CODE_SPAN_MARKER` | Mono + `codeBg` / markup |
| `CODE_BLOCK` lines + fence lines | Mono, text colour, full-column `codeBg` line band (fence lines too, so the block reads as one) |
| `CODE_FENCE`, `CODE_INFO` | markup |
| `BLOCKQUOTE(depth)` | text colour, no italics; wrapped lines indent under the text (hanging indent) |
| `QUOTE_MARKER` | markup |
| `LIST_MARKER` | — (text colour); wrapped lines indent under the item text |
| `TASK_MARKER` `[ ]` / `[x]` | markup; checked item text → `textSecondary` + strike-through; tap toggles |
| `LINK_TEXT` | — (text colour, no underline) |
| `LINK_MARKER`, `LINK_URL`, `LINK_TITLE`, `LINK_LABEL` | markup |
| `AUTOLINK` (`<…>`, bare URL, e-mail) | text colour + 1 dp underline; its `<` `>` markup (its inner `LINK_URL` is **not** greyed) |
| `FOOTNOTE_REF`, `THEMATIC_BREAK`, `HTML_BLOCK`, `HTML_INLINE`, `ESCAPE_MARKER`, `LINK_DEF` | markup |
| `TABLE_ROW` | Mono for the whole row (columns align); header row bold; delimiter row markup |
| `TABLE_PIPE` | markup |
| `FRONT_MATTER`, `FRONT_MATTER_FENCE` | Mono + markup |
| `ENTITY`, `HARD_BREAK` | — |

## 5. Editor behaviours

- **Caret:** 2 dp wide, 1 dp corner radius, `accent`, full line pitch, platform blink (500 ms).
- **Selection:** `selection` background; handles tinted `accent`.
- **Chrome:** the editor is 100 % text. Two floating glyph buttons only — library (`left_panel_open`, top-start) and
  overflow (`more_vert`, top-end), 48 dp targets, `textSecondary`, over the text. They **fade out (150 ms) on the
  first keystroke** and fade back in (220 ms) when the IME hides, on scroll-up ≥ 24 dp, on a tap in the top 56 dp,
  or after 1.5 s idle with the IME hidden. No app bar, no FAB, no keyboard bar, no bottom bar.
- A status-bar protection strip (`bg` @ 94 %) sits under the transparent status bar so scrolled text never collides
  with system icons.
- **Stats (off by default):** one 12 sp `textSecondary` line centred in the glyph row: `1,204 words · 6 min` (reading time = ceil(words / 238)).
  Tap cycles words → characters → sentences → reading time. With a selection it shows selection stats. Stays
  visible while typing at 60 % alpha.
- **Focus Mode:** Off / Sentence / Paragraph. Everything outside the active sentence/paragraph is dimmed by the
  overlay (§2). Selections extend the focus range.
- **Typewriter scrolling** (independent toggle): keeps the caret line at 45 % of the visible height (above the IME),
  150 ms animated; never fights a user fling.
- **Empty note placeholder:** "Start writing…" in `focusDim`, disappears on the first character.

## 6. Selection toolbar (the only formatting UI; appears ONLY while text is selected)

- One **pill** replaces the system floating toolbar (which is suppressed): 48 dp tall, fully rounded (24 dp radius),
  `surface`, 1 px `divider` border, **no shadow**, icons 24 dp tinted `text`, buttons 48 × 48 dp.
- Position: centred on the selection, 8 dp **above** its top; flips **below** (bottom + 28 dp for the handles) if there
  is no room; clamped inside the editor. Hidden while handles are being dragged (re-shows 150 ms after the last change),
  when the selection collapses, or when the editor loses focus. Also shown for hardware-keyboard selections.
- Visible slots `n = min(9, floor((width − 32 dp) / 48 dp))`; the last slot is always **More**. Priority order:
  1. Bold (`format_bold`) · 2. Italic (`format_italic`) · 3. Heading (`format_h1`, cycles none→#→##→###→none) ·
  4. Link (`link`) · 5. Copy (`content_copy`) · 6. Paste (`content_paste`) · 7. Cut (`content_cut`) ·
  8. Code (`code`: inline, or fenced block for multi-line selections) · **More** (`more_horiz`).
  A thin 1 px `divider` separates formatting from clipboard buttons.
- **More** menu (opens upward, `surface`, 12 dp radius, 1 px border, no shadow): Strikethrough, Highlight (only if
  `==` enabled), Quote, Bulleted list, Numbered list, Task, Code block, Clear formatting, Select all — plus any
  primary button that did not fit.
- After an action the transformed text stays selected (actions chain). Each action = one undo step. No toast, no haptic.
- Every button has a `contentDescription`; the same actions are exposed as accessibility custom actions on the editor.

## 7. Library drawer

- Opens with a **horizontal swipe anywhere in the editor** (start→end) or the library glyph; ≥ 840 dp it is a
  permanent 320 dp pane (+1 px divider) toggled by the glyph. Modal width `min(360 dp, screenWidth − 56 dp)`, `surface`.
- Content top → bottom:
  1. Header (56 dp): "Library" 20 sp bold; end: search (`search`), new note (`edit_square`, tinted `accent`).
  2. Search field (replaces the title while active; filters by name + first 2 KB of content).
  3. "Locations" (13 sp `textSecondary`): `phone_android` "On this device" · `folder` <linked folder name>… ·
     `create_new_folder` "Use a folder…" (`textSecondary`).
  4. Hairline divider.
  5. Breadcrumb "On this device › Drafts" (13 sp, tap to go up) + sort (`sort`: Date modified ✓ / Name; newest/oldest).
  6. Folder rows 48 dp (`folder` 20 dp + name 16 sp).
  7. File rows 72 dp: title (file name without extension, 16 sp, **bold + 3 dp accent bar at start** if it is the open
     document), excerpt (first non-empty line after the title, markers stripped, 13 sp `textSecondary`, 1 line),
     relative date end-aligned (12 sp: "14:02", "Yesterday", "Mon", "21 Sep", "21 Sep 2025").
  8. Long-press row → menu: Rename · Duplicate · Move… · Share · Delete (`danger`). Delete is **undoable** via
     snackbar "Deleted ‘X’ · Undo" (5 s) — no confirmation dialogs.
  9. Empty folder: "No notes yet" + text button "New note".
- New note: creates `Untitled.md` in the current folder, closes the drawer, focuses the editor, opens the IME. An
  untitled note left empty is deleted on leave. Auto-named from its first line on leave until the user renames it.
- Selecting a file: close drawer, open at its remembered caret/scroll, IME stays hidden (reading first).

## 8. Preview

- Swipe end→start in the editor (or overflow › Preview, Ctrl+R). Full-screen overlay, same `bg`, same column.
  Exit: swipe start→end, Back (predictive), Esc, or the `arrow_back` glyph. `share` glyph top-end.
- Theme = a class on `<html>`: `"light"`, `"dark"` or `"dark black"` (pure black); colours come from CSS variables.
  Never `prefers-color-scheme`. Known trade-off: a horizontally scrolled code block can compete with the exit swipe (QA in T22).
- Same fonts and heading scale, markers hidden, body pitch 1.6 ×, one pitch between blocks; lists with en-dash
  bullets hanging in the margin; blockquote 2 dp left rule; code Mono 0.9 × on `codeBg` (12 dp padding, 6 dp radius,
  horizontal scroll); tables in body font with 1 px row lines; links `text` colour with a 1 dp `markup` underline
  (tap → system browser); tasks as read-only checkbox glyphs; footnotes after an hr.

## 9. Overflow menu (the only menu in the editor)

Anchored under the top-end glyph; `surface`, 1 px border, 12 dp radius, no shadow, 240 dp wide:
- Icon row: `undo` · `redo` · `search` (Find) · `share`
- `edit_square` New note · `preview` Preview · `center_focus_strong` Focus ▸ (Off / Sentence / Paragraph)
- Typewriter scrolling ☐ · Word count ☐ · `tune` Settings

## 10. Settings (bottom sheet) — only these rows

| Group | Row | Values (default **bold**) |
|---|---|---|
| Appearance | Theme | **System** · Light · Dark |
| | Pure black in dark | switch (**off**) |
| Text | Typeface | **Duo** · Quattro · Mono (each label rendered in its own face) |
| | Text size | 6-stop slider XS–XXL (**M**), live sample line |
| | Line length (≥ 600 dp only) | **64** · 72 · 80 |
| Writing | Focus | **Off** · Sentence · Paragraph |
| | Typewriter scrolling | switch (**off**) |
| | Word count | switch (**off**) |
| | Swipe to library & preview | switch (**on**) |
| | `==highlight==` syntax | switch (**off**) |
| Files | New note extension | **.md** · .txt |
| | Show file extensions | switch (**off**) |
| | Export all notes… | creates a .zip via the system file picker |
| About | About mdwriter | version + font credit: "iA Writer Duo, Quattro, Mono by Information Architects Inc., based on IBM Plex. SIL Open Font License 1.1" → licence text |

Deliberately absent: accounts, sync, PIN, templates, style check, analytics, rate-us, help centre, keyboard bar,
line-height slider.

## 11. Motion

| What | Duration | Easing |
|---|---|---|
| Chrome fade out / in | 150 / 220 ms | LinearOutSlowIn / FastOutSlowIn |
| Toolbar pill in / out | 120 ms (alpha 0→1, scale 0.96→1, y +8→0 dp) / 90 ms | `CubicBezierEasing(0.05f,0.7f,0.1f,1f)` / `(0.3f,0f,0.8f,0.15f)` |
| Drawer, sheets | Material3 defaults | — |
| Preview enter/exit | 250 ms horizontal slide + fade | emphasized |
| Typewriter re-centre | 150 ms | Decelerate |
Respect "Remove animations" (`ValueAnimator.areAnimatorsEnabled()` for View animations).

## 12. Wireframes (phone, 448 dp)

```
Editor, idle                                  Text selected
│ 9:41                          ▾ ▴ ▮ │       │   ## A walk in the rain               │
│ ≡              412 words        ⋮ │       │  ╭────────────────────────────────────╮ │
│                                     │       │  │ B  I  H  🔗 │ ⧉  📋  ✂  <>  ⋯ │ │ ← pill above selection
│   ## A walk in the rain             │       │  ╰────────────────────────────────────╯ │
│                                     │       │   It had been ▐raining since noon▌, and│
│   It had been raining since noon,   │       │   the city smelled of wet stone.       │
│   and the city smelled of wet       │
│   stone. She took the long way▌     │       Library drawer (modal)
│                                     │       │┌──────────────────────────────┐░░░░░│
│   - bread                           │       ││ Library               🔍  ✎  │░░░░░│
│   - [ ] call **Ana**                │       ││ Locations                    │░░░░░│
│   See [notes](https://example.com). │       ││ 📱 On this device            │░░░░░│
│                                     │       ││ ⊕ Use a folder…              │░░░░░│
│            (nothing else)           │       ││──────────────────────────────│░░░░░│
                                              ││ On this device › Drafts   ⇅  │░░░░░│
Typing: ≡ ⋮ and stats fade out; IME only,     ││▌A walk in the rain     14:02 │░░░░░│
no keyboard bar.                              ││ It had been raining since…   │░░░░░│
                                              │└──────────────────────────────┘░░░░░│
```
More wireframes (preview, settings, first launch, tablet with permanent pane): `plans/research/design.md` §6.

## 13. Launcher icon & name

- Label: **"mdwriter"** (`@string/app_name`; debug: "mdwriter (debug)"). The design track proposed "Margin" as a
  friendlier label — trivial to change later in `strings.xml`.
- Adaptive icon (foreground/background/**monochrome** layers): off-white square, grey `#` hanging left of one heavy
  bar and two text bars, small blue caret. Exact VectorDrawable path data: `plans/research/design.md` §8.2 (copy it).

## 14. Icons (Material Symbols Rounded, weight 400, fill 0, 24 dp)

Download Android XML from
`https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml`
into `res/drawable/ic_<name>.xml` and **delete the `android:tint="?attr/colorControlNormal"` line** (that attribute
only exists with AppCompat; without it AAPT fails). Needed names:
`left_panel_open, left_panel_close, more_vert, more_horiz, arrow_back, share, undo, redo, search, edit_square,
preview, center_focus_strong, tune, format_bold, format_italic, format_h1, format_h2, format_h3, format_h4, format_h5,
format_h6, notes, link, code, code_blocks, content_copy, content_paste, content_cut, select_all, format_quote,
format_list_bulleted, format_list_numbered, checklist, format_strikethrough, ink_highlighter, format_clear, close,
phone_android, folder, folder_open, create_new_folder, sort, check, chevron_right, link_off,
drive_file_rename_outline, drive_folder_upload, delete, check_box, check_box_outline_blank, info, expand_less,
expand_more, match_case, find_replace`. (`footnote` does not exist.)

## 15. "iA-isms" acceptance checklist (used in T22)

- [ ] `#F7F7F7` page, `#1A1A1A` text; dark `#1A1A1A`/`#D0D0D0`; optional true black.
- [ ] Electric-blue 2 dp caret spanning the full line; blue handles.
- [ ] Duo by default; Quattro/Mono selectable; code always Mono.
- [ ] Generous line pitch (1.65× phone, 1.75× large).
- [ ] Large screens: 64/72/80-char centred column with `#` hanging in the margin.
- [ ] Markdown stays visible; headings/emphasis restyle live while typing; URLs/brackets grey.
- [ ] Wrapped list/quote lines indent under the text.
- [ ] No chrome while typing; glyphs return when you stop.
- [ ] No keyboard bar / FAB / app bar / bottom bar.
- [ ] Swipe right → library, swipe left → preview (one setting turns it off).
- [ ] Focus mode (sentence/paragraph) + typewriter scrolling.
- [ ] Stats opt-in, tiny, top, selection-aware.
- [ ] One settings sheet; no accounts, no cloud, no analytics.
- [ ] Reopening a note restores caret + scroll; autosave, no Save button.
- [ ] Flat surfaces, hairline dividers, no shadows or tonal tint.
- [ ] Completed tasks faded + struck through; `==highlight==` soft yellow (when enabled).
