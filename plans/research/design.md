# mdwriter — Design Spec (a focused-writing look in the iA Writer tradition, no iA name/logo/trademarks)

Status: COMPLETE (2026-09-25). Written by the design track, salvaged from an interrupted run, then gap-filled.
Audience: the plan author (no web access). Every value below is final unless marked **[DECIDE]** (open for the user) or **[UNVERIFIED]**.

Confidence legend: **H** = measured or primary source · **M** = inferred from several secondary signals · **L** = design choice without source.

---

## 0. TL;DR (decisions)

| # | Topic | Decision | Conf. |
|---|---|---|---|
| 1 | Default typeface | **iA Writer Duo S** (static TTF, 4 styles) bundled in `res/font`. Optional: Quattro S, Mono S (picker). Code spans/blocks always use Mono S. | H (license) / M (Android default = Duo, measured from Play screenshots) |
| 2 | License | SIL OFL 1.1, RFN "iA Writer" (and "Plex" upstream). Ship the files **unmodified** (no subsetting/conversion), include `OFL.txt` + copyright lines in an About › Licenses screen. Showing "iA Writer Duo" in the font picker is allowed (unmodified font). | H |
| 3 | Light palette | bg `#F7F7F7`, text `#1A1A1A`, markup `#868686`, focus-dim `#C0C0C0`, caret `#00B2FF`, selection `#00B2FF @ 25%` | H (sampled) |
| 4 | Dark palette | bg `#1A1A1A`, text `#D0D0D0`, markup `#7A7A7A`, focus-dim `#5E5E5E`, caret `#00B2FF`, selection `#00B2FF @ 30%`; optional pure-black `#000000` bg | H (sampled) |
| 5 | Caret | 2 dp wide, 1 dp corner radius, height = full line pitch, accent blue, default 500 ms blink | H (measured) |
| 6 | Body type | 17 sp, line pitch 1.65× (Duo/Mono) / 1.55× (Quattro) on phones; 1.75× / 1.65× at ≥ 600 dp. 5 size steps 15/17/19/21/24 sp | M |
| 7 | Headings | **Bigger than iA** (the user's wish): H1 1.60×, H2 1.40×, H3 1.25×, H4 1.10×, H5 1.00×, H6 1.00× (secondary colour). All Bold. `#` markers are drawn at body size, baseline-aligned. | L (user req.) |
| 8 | Markup colour | iA-faithful: `#`, `*`, `_`, `-` markers keep the **text colour**. Only "non-prose" syntax is grey: link/image brackets + URLs, backticks, code fences, table pipes, HTML, footnote labels, hr, escapes, front matter. | H (Android + Mac screenshots) |
| 9 | Hanging markers | Phones (< 600 dp): **no hanging**, same as iA (its margin = 0 chars on Compact). ≥ 600 dp: heading `#` runs hang in the start margin (gutter 4 chars at 600–839 dp, 6 chars ≥ 840 dp). Lists/quotes never hang, but wrapped lines indent under the text (hanging indent). | H (iA config) |
| 10 | Measure | Phones: fill width minus 24 dp margins (≈ 36–40 chars). ≥ 600 dp: centred column capped at **64 / 72 / 80** chars (setting; default 64 [UNVERIFIED default]). | H (options) |
| 11 | Chrome | Editor is 100 % text. Two floating glyph buttons only (library, overflow), top edge, visible when idle, fade out 150 ms after typing starts. No app bar, no keyboard bar, no FAB in the editor. | H (iA Android hides menu while typing) |
| 12 | Selection toolbar | Keep the **system** floating text toolbar (cut/copy/paste/select all/share, smart actions). Add our own **formatting pill** docked bottom-centre above IME/nav bar, shown **only while selection is non-empty**. Order: Bold · Italic · Heading · Link · Code · Quote · List · More(…). | L (fits the user req.) |
| 13 | Library | Modal drawer (swipe right anywhere, platform track), 360 dp max width; permanent 320 dp pane at ≥ 840 dp. Rows: title + 1-line excerpt + relative date. Sort: modified ↓ (default), name. `+` new note in header. "Use a folder…" at the bottom of Locations. | H (iA Android doc list) / L |
| 14 | Focus mode | Off / Sentence / Paragraph + independent Typewriter scrolling toggle. | H (iA iPhone/Android docs) |
| 15 | Stats | Off by default. When on: one subtle 12 sp line at top centre ("1,204 words · 5 min"), switches to selection stats while text is selected. | H (iA Android/iPhone docs) |
| 16 | Name | Proposed: **"Margin"** (label), alternatives "Longhand", "Draftline". Package keeps `mdwriter`. | L |
| 17 | Icon | Off-white disc, grey `#` hanging left of one heavy bar + two text bars, small blue caret. Exact VectorDrawable paths in §8. | L |
| 18 | Icons in UI | Material Symbols **Rounded, weight 400, fill 0**, 24 dp, downloaded as Android XML (list in §9); strip `android:tint`. | H (names verified) |

**Ethics/trade-dress note (read this):** iA's font README says: "If you fork or use our fonts, please reference iA Writer clearly" and "Don't be a copycat… do not clone our products" (github.com/iaolo/iA-Fonts README.md, fetched 2026-09-25). The "A Typographic Christmas" post says "Don't recreate iA Writer or iA Writer Themes. Use them creatively." These are requests, not license terms. OFL 1.1 legally permits bundling. This spec therefore: uses its own name and icon, never shows "iA" except in the font credit/licence screen (required attribution), and the app is side-loaded for personal use. **[DECIDE]** If the user ever publishes the app, switch the default font to upstream **IBM Plex Mono** (OFL, RFN "Plex") and re-think the blue-caret + grey-background combination, which iA calls its "iconic" look. Record this in the plan's "risks".

---

## 1. Fonts

### 1.1 License (H)
- Source repo: https://github.com/iaolo/iA-Fonts (default branch `master`, last push 2023-06-16, GitHub API shows `license: null` because the licence lives per family). Each family folder has an identical `LICENSE.md` (md5 `24cd6c256d592d23fdc3ef640e05f0ed`). Header:
  - `Copyright © 2018 Information Architects Inc. with Reserved Font Name "iA Writer"`
  - `Based on IBM Plex Typeface — Copyright © 2017 IBM Corp. with Reserved Font Name "Plex"`
  - `This Font Software is licensed under the SIL Open Font License, Version 1.1.`
- OFL FAQ (https://openfontlicense.org/ofl-faq/, fetched 2026-09-25):
  - 1.4 / 1.20: bundling with any software (free or commercial) is allowed. "At a minimum you must include the copyright statement, the license notice and the license text". Placing them in "your About box or Changelog, with a link to where the font package is from" is acceptable.
  - RFN applies only to **Modified Versions**. Unmodified files keep their names, so the picker may show "iA Writer Duo / Quattro / Mono". (FAQ 5.3 context.)
  - 2.2 / 2.6 / 2.8: subsetting, removing glyphs, or converting format (TTF→WOFF2 etc.) **is modification** → would force a rename. So **ship the TTFs byte-for-byte** (AAPT/R8 don't alter files in `res/font`).
  - 1.12: embedding in exported PDFs (full or subset) is fine.
  - OFL condition 4: iA's name must not be used to promote our app. Keep "iA" out of the app name, description and icon.
- Only the Duo files carry the licence text in their `name` table (ID 13). Mono S/Quattro S have empty ID 13, so the separate licence text is **mandatory**, not optional.

### 1.2 Files to bundle (sizes measured, raw URLs verified with HTTP 200 + matching content-length)
Base URL: `https://raw.githubusercontent.com/iaolo/iA-Fonts/master/` + path (spaces as `%20`).

| Res name (`res/font/…`) | Repo path | Bytes |
|---|---|---|
| `duo_regular.ttf` | `iA Writer Duo/Static/iAWriterDuoS-Regular.ttf` | 120 696 |
| `duo_bold.ttf` | `iA Writer Duo/Static/iAWriterDuoS-Bold.ttf` | 121 460 |
| `duo_italic.ttf` | `iA Writer Duo/Static/iAWriterDuoS-Italic.ttf` | 105 824 |
| `duo_bold_italic.ttf` | `iA Writer Duo/Static/iAWriterDuoS-BoldItalic.ttf` | 105 252 |
| `quattro_regular.ttf` | `iA Writer Quattro/Static/iAWriterQuattroS-Regular.ttf` | 119 772 |
| `quattro_bold.ttf` | `iA Writer Quattro/Static/iAWriterQuattroS-Bold.ttf` | 120 404 |
| `quattro_italic.ttf` | `iA Writer Quattro/Static/iAWriterQuattroS-Italic.ttf` | 105 028 |
| `quattro_bold_italic.ttf` | `iA Writer Quattro/Static/iAWriterQuattroS-BoldItalic.ttf` | 104 520 |
| `mono_regular.ttf` | `iA Writer Mono/Static/iAWriterMonoS-Regular.ttf` | 97 044 |
| `mono_bold.ttf` | `iA Writer Mono/Static/iAWriterMonoS-Bold.ttf` | 96 168 |
| `mono_italic.ttf` | `iA Writer Mono/Static/iAWriterMonoS-Italic.ttf` | 104 120 |
| `mono_bold_italic.ttf` | `iA Writer Mono/Static/iAWriterMonoS-BoldItalic.ttf` | 103 400 |
| **Total** | | **1 303 688 B ≈ 1.24 MiB** (Duo 443 KiB, Quattro 439 KiB, Mono 391 KiB) |

Licence: `iA Writer Duo/LICENSE.md` (4 506 B) → ship as `app/src/main/assets/licenses/iA-Writer-fonts-OFL.txt`.
The files are already downloaded (identical) in `scratchpad/fonts/`. A plan task can `curl -fL` the URLs above, or copy from that folder. Android resource names must be lowercase `[a-z0-9_]`, hence the renames. Renaming a **file** is not a font modification (the internal name table is unchanged).

Do **not** bundle the variable (`V`) fonts in v1. They have `wght 400–700` and `SPCG 0–150` axes and named instances Regular 400 / Text 450 / Semibold 650 / Bold 700. iA itself renders body text at wght ≈ 455–460 and −20 in dark mode (TypographyKit config, §1.4), which is nice, but variable fonts + `StyleSpan(BOLD)` inside an EditText need custom Typeface plumbing. Future enhancement only.

### 1.3 Font facts that affect code (H, fontTools on the actual files)
- All: UPM 1000; hhea ascender 1025 / descender −275 / lineGap 0 → natural line box **1.30 em**; OS/2 typo 780/−220/300; xHeight 516, capHeight 698; `fsType 8` (editable embedding permitted).
- Advance widths: **Mono**: every glyph 600 (0.60 em). **Duo**: 600, except `m M w W` = 900 (1.5×). **Quattro**: 600 base, `i` 300, space 450, `m M W` 900.
- Regular/Bold: 1182 glyphs; Italic/BoldItalic: 850 glyphs (fewer non-Latin glyphs; system fallback covers the rest).
- **Metadata bug:** `iAWriterQuattroS-Bold.ttf` and `-BoldItalic.ttf` declare `usWeightClass = 400`. Always declare weight explicitly in the font-family XML (below) and never rely on the file.
- Character-width math (use `0.6 × textSizePx` as the "n-width" for measure calculations; Duo `m/w` lines run slightly long, which is fine).

```xml
<!-- res/font/duo.xml  (same pattern for quattro.xml, mono.xml) -->
<font-family xmlns:android="http://schemas.android.com/apk/res/android">
    <font android:font="@font/duo_regular"     android:fontStyle="normal" android:fontWeight="400"/>
    <font android:font="@font/duo_italic"      android:fontStyle="italic" android:fontWeight="400"/>
    <font android:font="@font/duo_bold"        android:fontStyle="normal" android:fontWeight="700"/>
    <font android:font="@font/duo_bold_italic" android:fontStyle="italic" android:fontWeight="700"/>
</font-family>
```
- Editor (View): `editText.typeface = resources.getFont(R.font.duo)` (API 26+). Then `StyleSpan(Typeface.BOLD / ITALIC / BOLD_ITALIC)` resolves to the real Bold/Italic files through the family (no fake bold/skew). Code: `TypefaceSpan(resources.getFont(R.font.mono))` (the `TypefaceSpan(Typeface)` constructor exists since API 28).
- Compose UI (drawer, sheets, preview): `val Duo = FontFamily(Font(R.font.duo_regular, FontWeight.Normal), Font(R.font.duo_italic, FontWeight.Normal, FontStyle.Italic), Font(R.font.duo_bold, FontWeight.Bold), Font(R.font.duo_bold_italic, FontWeight.Bold, FontStyle.Italic))`.
- UI chrome font (drawer, settings, dates): use **the same family as the editor** (Duo by default) at smaller sizes. iA's own Android UI used its writing font in the editor and Material/system UI elsewhere. For a calmer, more "one-material" feel we use Duo everywhere except the system text toolbar (which we don't control). **[DECIDE]** alternative: system sans (`FontFamily.Default`, i.e. Google Sans Flex/Roboto Flex on Pixels) for chrome.
- CJK/emoji: rely on system fallback. Nothing to do.

### 1.4 What iA uses by default (evidence)
- **Android:** iA Writer for Android 2.0 (80) "Added iA Writer Font to Editor" (release notes, wb/writer_support_help_version_history_release_notes_android.txt, archived 2026-04-12). The Android settings list has **no typeface option**, only Font Size (Automatic, Small, Medium, Large, XLarge, XXLarge), Line Height and Keyboard Extension (wb/…settings_android.txt). The Play Store screenshots are **Duo**. Measured: per-character advance is constant only when `m/w` count as 1.5 (17.71–17.75 px/unit on three lines with different m/w counts; Mono would give 17.7–18.7). → Android default = **Duo** (M-H).
- **Mac/iOS:** a picker offers Mono, Duo, Quattro (settings pages). Line length options 64/72/80 on Mac (H).
- iA's own typography tables (TypographyKit `Duo.plist`/`Quattro.plist`/`Mono.plist` in the locally installed iA Writer 8.0.7 for Mac; extracted to `scratchpad/typo_*.json`):
  - Sizes: M = 16 pt, L = 18 pt (screen), XL = 20 pt …
  - Line height at M (16 pt): Duo/Mono **166 % Compact / 176 % Regular / 186 % Wide**. Quattro 155 / 165 / 175 %. At L (18 pt): Duo 165 / 175 / 185 %.
  - `MarginCharacterCounts` (width of the hanging-marker margin, in characters): **Compact 0**, Regular ≤ 512 w 3, 512–768 w 4, > 768 w 6, Wide/Full 8.
  - Body weight (variable axis) 455–470 depending on size, **−20 in Dark**. Strong = 700. Emphasis = Italic.

---

## 2. Colours

All values are sRGB hex. Sampled with PIL from primary screenshots (iA landing page and Play Store listing images; darkest-pixel core colours, so thin glyphs read slightly lighter than true). Cross-checked against community themes that copy iA (mrowa44 Obsidian theme, rcvd theme) and ia.net CSS.

### 2.1 Evidence table
| Thing | Measured / found | Source | Conf. |
|---|---|---|---|
| Editor bg light | `#F7F7F7` (Mac), `#F9F9F9` (iOS, Android Play shots) | shots/*.webp, Play hi4.png | H |
| Text light | core `#000000`–`#141414`. Community themes and iA preview use `#1A1A1A` | shots; mrowa44; iA Mac Duo template | H |
| Link/URL markup light | `#828282`–`#888888` (URL), `#6A6A6A`–`#717171` (`![`) | Play hi4.png "![desert](https://…)" | H |
| Heading `#`, `*` markers | **same as text** (black) on Android; same as text (`#CCCCCC`) on Mac dark | Play hi4.png; iAW-alice-writing-formatting-desktop.png | H |
| Focus-dim light | `#BDBCBB`–`#C1C1C1` on `#F7F7F7`/`#F9F9F9` | 3 screenshots | H |
| Editor bg dark | `#191919`–`#1A1A1A` | Play hi7.png; Mac shot | H |
| Text dark | `#CCCCCC` (Mac), `#D1D1D1`–`#D7D7D7` (Android marketing) | same | H |
| Focus-dim dark | `#727272`–`#767676` | Play hi7.png | H |
| Caret | `#00B2FF` (Android), `#00C3FF`/`#01C4FF` (Mac/iOS). Assets.car P3 colours convert to `#00D7FF` / `#00ADFF`. ia.net accent `#00B2FF` | screenshots; Assets.car dump; ia_main.css | H |
| Caret size | iOS 3×: 6 px = **2 pt** wide; Mac 2×: 6 px = 3 pt; height ≈ full line pitch (49 of 50 px; 87 px vs ~80 px pitch) | measured | H |
| Selection | ia.net `::selection rgba(47,190,234,0.25)`; rcvd `#C9E9F4` light / `#20434E` dark. Play shot: blue highlight + blue teardrop handles | CSS; Play sheet | M |
| Highlight `==x==` | yellow `rgb(255,217,0)`; bg alpha 0.35 light / 0.175 dark; 0.175 em underline; dark text blended to ≈ `#DAD094` | iA Mac `Kit.framework/…/mark.css` (local app) | H |
| hr / quote rules (preview) | light `#BBBBBB`→`#1A1A1A` (by width), night `#444`–`#CCCCCC` | iA Duo template style.css (local app) | H |
| Muted UI | mrowa44 muted `#B5B3B1` light / `#707070` dark; rcvd faint `#9C9C9C` / `#8E8F92` | themes/ | M |
| Secondary bg | mrowa44 `#FCFCFC` / `#141414`; iA preview `#FCFCFC` | themes; screenshot | M |

### 2.2 Final colour tokens
Contrast ratios (WCAG) computed against the editor background, order Light / Dark / Pure-black.

| Token | Light | Dark | Pure-black (opt.) | Use | Contrast (L / D) |
|---|---|---|---|---|---|
| `bg` | `#F7F7F7` | `#1A1A1A` | `#000000` | editor, window, splash | — |
| `surface` | `#FCFCFC` | `#141414` | `#0A0A0A` | drawer, library pane, sheets, pill | — |
| `surfaceHover` | `#EFEFEF` | `#242424` | `#161616` | pressed row, selected file row bg | — |
| `text` | `#1A1A1A` | `#D0D0D0` | `#C8C8C8` | body, headings, markers `# * _ -` | 16.3 / 11.3 / 12.6 |
| `textSecondary` | `#6E6E6E` | `#8C8C8C` | `#8A8A8A` | excerpts, dates, stats, H6, settings subtitles | 4.8 / 5.2 / 6.1 (AA for small text) |
| `markup` | `#868686` | `#7A7A7A` | `#707070` | link/image brackets + URLs, backticks, fences, pipes, HTML, hr, escapes, footnote labels, front matter, setext underline | 3.4 / 4.1 / 4.2 |
| `focusDim` | `#C0C0C0` | `#5E5E5E` | `#4A4A4A` | non-focused text in Focus mode (all kinds, markup too) | 1.7 / 2.7 / 2.4 (intentionally low) |
| `accent` | `#00B2FF` | `#00B2FF` | `#00B2FF` | caret, selection handles, "+ New", switches, active row bar | 2.2 / 7.3 |
| `selection` | `#4000B2FF` | `#4D00B2FF` | `#5900B2FF` | selected text bg (ARGB: 25 % / 30 % / 35 %) | — |
| `codeBg` | `#EDEDED` | `#242424` | `#141414` | code span bg, fenced-block line band | — |
| `highlightBg` | `#59FFD900` | `#2DFFD900` | `#2DFFD900` | `==mark==` bg (ARGB 35 % / 17.5 %) | — |
| `highlightLine` | `#FFD900` | `#CCAE00` | `#CCAE00` | 0.175 em underline under `==mark==` (optional) | — |
| `highlightText` | = `text` | `#DAD094` | `#DAD094` | text inside `==mark==` | — |
| `divider` | `#E2E2E2` | `#2E2E2E` | `#1F1F1F` | hairline 1 px: pane edge, table rows, hr in preview | — |
| `scrim` | `#52000000` | `#99000000` | `#B3000000` | modal drawer / sheet scrim | — |
| `danger` | `#D93A2B` | `#FF6B5E` | `#FF6B5E` | "Delete" menu text only | — |

Notes
- Accent on light bg is 2.2:1. That's fine for the caret (2 dp, saturated, iA uses it), but **don't use `accent` for text or icon-only affordances** on light. For the "+ New note" label use `text`, with the icon tinted `accent`.
- The platform track wants dimmed markers ≥ 3:1. `markup` meets that (3.4 / 4.1 / 4.2). `focusDim` deliberately doesn't: it's a mode the user turns on.
- System bars: transparent (edge-to-edge). Light theme → dark status/nav icons (`isAppearanceLightStatusBars = true`); dark → light icons.
- Splash: `android:windowSplashScreenBackground` = `bg` (values / values-night).
- Night mode setting: System (default) / Light / Dark, plus a "Pure black in dark" toggle (iA Android had "Ultra dark night mode": "Turn ON/OFF AMOLED night mode"; the 2017 blog also mentions "an OLED optimized night mode").
- **No Material You dynamic colour.** The look is monochrome plus one accent. Build a `lightColorScheme(...)`/`darkColorScheme(...)` from the tokens: `background=bg, surface=surface, surfaceContainer*=surface, onBackground=onSurface=text, onSurfaceVariant=textSecondary, outline=outlineVariant=divider, primary=accent, onPrimary=#FFFFFF, secondary=text, scrim=scrim, error=danger, surfaceTint=Transparent`. Set `tonalElevation = 0.dp` everywhere.

---

## 3. Typography & layout

### 3.1 Body text
| Setting | Phone (width < 600 dp) | Medium 600–839 dp | Expanded ≥ 840 dp |
|---|---|---|---|
| Body size (default step "M") | 17 sp | 18 sp | 18 sp |
| Size steps (setting "Text size") | XS 15 · S 16 · **M 17** · L 19 · XL 21 · XXL 24 sp (phone; add +1 sp at ≥ 600 dp) | | |
| Line pitch (Duo/Mono) | 1.65 × size (17 sp → 28.05 sp) | 1.75 × | 1.75 × |
| Line pitch (Quattro) | 1.55 × | 1.65 × | 1.65 × |
| Letter spacing | 0 (the fonts are spaced for screen; iA's SPCG axis isn't available in static fonts) | | |
| Horizontal margin (each side, min) | 24 dp | 32 dp | 48 dp |
| Max measure | none (fills width) | 64/72/80 chars (setting, default 64) | 64/72/80 |
| Hanging-marker gutter (inside the start margin) | 0 chars (inline markers) | 4 chars | 6 chars |
| Top padding (below status bar inset) | 56 dp (room for the idle glyph row) | 64 dp | 72 dp |
| Bottom padding (end of doc) | 50 % of viewport height, so the last line can scroll to mid-screen (typewriter-friendly) | | |

- Why 17 sp: on the Pixel 10 Pro XL AVD (1344 px, 480 dpi → **448 dp**) with 24 dp margins, Duo gives (448 − 48) / (0.6 × 17) = **39 chars/line**; on a 412 dp phone ≈ 36. The iPhone screenshot measures ≈ 36 chars/line (iA's phone look). (M)
- Column math (≥ 600 dp): `columnPx = measureChars × 0.6 × textSizePx`. Then `sidePadding = max(minMargin, (viewWidth − columnPx) / 2)`. EditText `paddingStart = sidePadding − gutterPx`, `paddingEnd = sidePadding`, where `gutterPx = gutterChars × 0.6 × textSizePx` (see 3.3). Recompute on width change and text-size change.
- Line pitch implementation: prefer a **single `LineHeightSpan.Standard(pitchPx)`** over the whole text (API 29+; heading lines get their own). Reason (H, AOSP `Editor.java` line ~2440): the cursor is drawn from `layout.getLineTop(line)` to `layout.getLineBottom(line, includeLineSpacing = false)`. So with `setLineSpacing(extra, mult)` / `setLineHeight()` the caret covers only the 1.30 em font box. With `LineHeightSpan` the ascent/descent themselves grow, and the caret spans the full line, as in iA. (Implementation detail belongs to the editor track, which must verify it on device.)
- Paragraph spacing: **none added**. Markdown blank lines are real empty lines, so a paragraph gap is exactly one line pitch (iA behaviour, plain-text honesty). Don't add `paddingTop` to headings in the editor.

### 3.2 Heading scale (editor = preview)
The user explicitly wants headings visibly bigger than body (iA itself keeps them body-size and bold).

| Level | Size × body | @17 sp | Weight | Line pitch | Colour |
|---|---|---|---|---|---|
| H1 `#` / setext `===` | 1.60 | 27.2 sp | Bold 700 | 1.25 × own size (34 sp) | text |
| H2 `##` / setext `---` | 1.40 | 23.8 sp | Bold | 1.30 × (30.9 sp) | text |
| H3 | 1.25 | 21.25 sp | Bold | 1.35 × (28.7 sp) | text |
| H4 | 1.10 | 18.7 sp | Bold | 1.50 × (28.05 sp) | text |
| H5 | 1.00 | 17 sp | Bold | body pitch | text |
| H6 | 1.00 | 17 sp | Bold | body pitch | textSecondary |

- Rule: heading pitch = `max(bodyPitch, round(size × factor))`. H1–H3 pitches ≥ body pitch, so the vertical rhythm only ever grows.
- Implement with `RelativeSizeSpan(scale)` + `StyleSpan(BOLD)` + a heading `LineHeightSpan.Standard` on the heading line. The **marker run** (`#…# ` incl. the space, and a closing `#` run) gets `RelativeSizeSpan(1/scale)`, so markers render at body size (keeps the hang width predictable). Its colour stays `text` (iA-faithful). Setext underline line (`===`/`---`) → `markup` colour, body size.
- Keep scales ≤ 1.6: at 200 % font scale H1 becomes 54 sp and still has to wrap sensibly on a 360 dp phone.

### 3.3 Hanging markers (the iA signature)
- **Phones (< 600 dp): no hanging.** iA's own table puts Compact margin = 0 chars. Markers sit inline: `## Title` with small (body-size) `##`.
- **≥ 600 dp:** heading `#` runs hang in the start margin so the heading text aligns with the body column (Play Store tablet shot: `# Dune`, where `# ` sits left of the text column; Mac shot: `#` and `##` right-aligned to the column).
- Mechanism (text is clipped to the padding box: AOSP `TextView.onDraw` clips at `compoundPaddingLeft`, so nothing can be drawn *in* the padding):
  1. EditText `paddingStart = sidePadding − gutterPx`.
  2. One document-wide `LeadingMarginSpan.Standard(gutterPx)` on `[0, length]`, flags `SPAN_INCLUSIVE_INCLUSIVE`. All text is then indented by the gutter.
  3. Each ATX heading paragraph gets an extra `LeadingMarginSpan.Standard(first = −markerWidthPx, rest = 0)`, where `markerWidthPx = paint.measureText("## ")` at body size. Margins add up, so the first line starts `gutter − markerWidth` and the `#`s end exactly at the column edge. Only when `markerWidthPx ≤ gutterPx`; otherwise leave it inline.
  - **[UNVERIFIED]** that StaticLayout accepts a negative per-span margin when the total stays ≥ 0 (it sums `getLeadingMargin()` arithmetically, so it should work). The editor track must prototype it. Fallback: skip hanging (phone behaviour everywhere).
- Lists, task items, blockquotes: **don't hang**. Instead use a hanging *indent* (iA: "Wrapped Line Indent Includes Markdown Block Markers… will also indent to the same degree as the start of the text above", iPhone settings doc). `LeadingMarginSpan.Standard(first = 0, rest = widthOf("- ") / widthOf("1. ") / widthOf("> ")…)`, nested widths added. Also for plain indented paragraphs (leading whitespace).

### 3.4 Per-construct styling (maps to the markdown track's `MdKind` in research/proto/MdModel.kt)
| MdKind | Style (editor) |
|---|---|
| `HEADING(arg=level)` | §3.2 size/weight/pitch on the whole line |
| `HEADING_MARKER` | body size (`RelativeSizeSpan(1/scale)`), colour `text`; setext underline → `markup`; hang per §3.3 |
| `EMPHASIS` / `STRONG` | `StyleSpan(ITALIC)` / `StyleSpan(BOLD)` (both → BOLD_ITALIC) over the range incl. delimiters |
| `EMPHASIS_MARKER` (`* _ ** __`) | inherits the style, colour `text` (iA-faithful). **[DECIDE]** user may prefer `markup`. Put it behind one constant `DIM_EMPHASIS_MARKERS = false` |
| `STRIKETHROUGH` + `~~` marker | `StrikethroughSpan`, colour `text`; markers `markup` |
| `HIGHLIGHT` `==x==` | bg `highlightBg`, text `highlightText`; `==` markers `markup` |
| `CODE_SPAN` | `TypefaceSpan(mono)` + `BackgroundColorSpan(codeBg)`; backticks (`CODE_SPAN_MARKER`) `markup` |
| `CODE_BLOCK` lines | mono typeface, colour `text`, full-column background band `codeBg` (custom `LineBackgroundSpan` drawing from `left` to `right` of the line) |
| `CODE_FENCE`, `CODE_INFO` | mono, `markup` |
| `BLOCKQUOTE(depth)` | content colour `text`, no italics; hanging indent after `> `; optional 2 dp `divider`-coloured bar at x = column start − 12 dp (only ≥ 600 dp) |
| `QUOTE_MARKER` | `markup` |
| `LIST_MARKER` | colour `text` (like iA); hanging indent |
| `TASK_MARKER` `[ ]` / `[x]` | `[ ]` `markup`. Checked: `[x]` `markup` **and** the item text in `textSecondary` + `StrikethroughSpan` (iA: "Completed Tasks: Strikethrough, Fade the text, or both", default here: both) |
| `LINK` / `LINK_TEXT` | link text `text` (no underline); `LINK_MARKER` `[ ] ( ) !` and `LINK_URL`, `LINK_TITLE`, `LINK_LABEL` → `markup` (Play screenshot) |
| `AUTOLINK` (`<…>`, bare URL) | `text` with 1 dp `UnderlineSpan`; `<` `>` `markup` |
| `FOOTNOTE_REF` `[^1]` | `markup` whole ref (for definitions: label `markup`, text normal) |
| `THEMATIC_BREAK` `---` `***` | `markup` |
| `TABLE_ROW(arg)` | mono typeface for the whole row, so columns align with the monospaced pipes; header row Bold; delimiter row `markup` |
| `TABLE_PIPE` | `markup` |
| `HTML_BLOCK` / `HTML_INLINE` | `markup` |
| `ENTITY` | `text` |
| `ESCAPE_MARKER` `\` | `markup` |
| `HARD_BREAK` (trailing spaces) | nothing visible |
| `FRONT_MATTER`, `FRONT_MATTER_FENCE` | mono, `markup` |
| `LINK_DEF` | `markup` whole line |

Focus mode (when on) overrides colour only: every span outside the active sentence/paragraph gets `ForegroundColorSpan(focusDim)` (highlight bg also dims to 40 % alpha). Active range keeps normal colours.

### 3.5 Preview typography (read-only rendering)
- Same family as editor (Duo default), same heading scale, markers hidden, body line pitch 1.6×, paragraph spacing = 1 line pitch between blocks (like iA's Duo template: block margins = 29/16 em ≈ 1.8 em at 16 pt).
- Lists: bullet `–` (en dash) hanging in the margin, 1.5 em indent. Numbered: tabular numbers right-aligned in the margin.
- Blockquote: 2 dp left rule `divider`-dark (`#BBBBBB` light / `#444444` dark), 1 em padding, text `textSecondary`.
- Code: mono 0.9×, `codeBg` block with 12 dp padding, 6 dp radius, horizontal scroll for long lines.
- Tables: body font (not mono), 1 px `divider` row lines, header Bold, cells 8 dp × 12 dp padding, horizontal scroll if wider than column.
- hr: 1 px `divider`, 1 line pitch above/below. Images: fill column width, 6 dp radius, alt text under in `textSecondary` 13 sp. Footnotes: superscript numbers in `textSecondary`; list at the end after an hr.
- Links: `text` colour with 1 dp underline in `markup`; tap opens `ACTION_VIEW` (no INTERNET permission needed; the browser handles it).
- Task items: Material Symbols `check_box_outline_blank` / `check_box` at 18 dp, `textSecondary`, not interactive in v1.

---

## 4. Interaction model

### 4.1 Reference behaviour: iA Writer for Android (from the archived support pages in scratchpad/wb/, all H)
- Three parts: **Library, Editor, Preview**. Library opens by "tapping the ← blue arrow on the top-left" **or "swiping from left to right"**. A setting "Horizontal Swipes: When switched on, you can swipe between the Library, Editor and Preview."
- Editor: "tap the document body and start typing… The menu at the top of the screen will be hidden… To reveal the menu, dismiss the keyboard… or by swiping down on the text area." Menu bar: Return to Library · New Document · Sharing & Export · ⋮ (Preview, Dark Mode toggle, Word Count toggle, Settings).
- Document list: folders + files of a location; tap folder / tap file; long-press → context menu (rename, delete, move…) and multi-select; "blue + button (down-right) to create a new file"; sort by Name / Date / Size; show/hide file extensions; default extension `.md` or `.txt`; Quick Search (🔍 top-right).
- Locations: "Device" (private app storage, with a warning that uninstall deletes it), Dropbox, Google Drive, "Open From" (SAF-linked). A "Quick Start" document is pre-installed.
- Keyboard bar (optional setting): Quick Search · caret `<` `>` · dismiss keyboard · Undo/Redo · Lightning (headers, lists…, Focus Mode, Style Check) · Find & Replace.
- Focus Mode: "the active sentence – where your cursor is placed – is highlighted while the surrounding text is dimmed". It also vertically centres the caret ("Focus Mode's attempt to vertically center the cursor"). iPhone adds Paragraph and Typewriter scopes.
- Stats: "⋮ → Word Count… character, word, and sentence counts subtly displayed at the top of the Editor". iPhone: tap the stats to pick which stat. With a selection, the stats cover the selection.
- Settings (Android, 4 sections): General (Horizontal swipes, Ultra-dark night mode, PIN, Account, Support, Privacy, Line height), Editor (Font size: Automatic/S/M/L/XL/XXL, Keyboard extension), Style Check, Export (Medium/WordPress).
- Export: PDF, .docx, HTML, Markdown. Share from Editor or Preview via the share button. Preview uses templates.
- 3.0: "Cursor now maintains position on reopen of documents". 3.1.4.1: backups every few seconds.

### 4.2 Our mapping (what we keep, what we drop)
| iA Android | mdwriter | Why |
|---|---|---|
| Top menu bar (hidden while typing) | **Two floating glyph buttons** (library, overflow) over the text, top edge, only while idle | ~100 % writing area |
| ← arrow / swipe right → Library | Swipe right anywhere (platform track §4.2) + the library glyph. At ≥ 840 dp the library is permanent | same |
| Swipe left → Preview | Swipe left anywhere → Preview (full-screen overlay). Overflow › Preview | same as iA "Horizontal swipes" |
| New Document button / blue + FAB | `+` in the drawer header; overflow › New note; Ctrl+N; launcher shortcut "New note" | no FAB over text |
| Share & Export | Overflow › Share… (Markdown text/file; `ACTION_SEND`). Export HTML/PDF = phase 2 via Preview (print → PDF) | minimal |
| ⋮ Dark mode / Word count | Settings sheet toggles (Word count also in overflow as a checkable row) | fewer menus |
| Keyboard bar | **Dropped.** Formatting moves to the selection pill. Undo/redo → overflow icon row + Ctrl+Z/Ctrl+Shift+Z. Caret nav → system. Find → overflow › Find. | user req. |
| Lightning menu (headers, lists, focus) | Selection pill + typing Markdown directly; Focus in overflow | user req. |
| Style Check, Accounts, PIN, publish, collaboration, cloud | **Dropped** | no accounts/cloud |
| Templates | One preview style | minimal |
| Quick Start doc | "Welcome.md" created on first launch (short: Markdown cheat-sheet + 3 gestures) | same idea |

### 4.3 Editor states
1. **Idle** (keyboard hidden or no typing for ≥ 1.5 s after scroll-up/tap): library glyph (top-start) and overflow glyph (top-end) visible at 100 % of `textSecondary` alpha. Optional stats line centred on the same row.
2. **Typing:** on the first keystroke, glyphs and stats fade to 0 over 150 ms and stop receiving touches. The status bar stays (edge-to-edge, transparent; we don't hide system bars: iA-style immersion without fighting the system). They return on: IME hidden, scroll *up* by ≥ 24 dp, single tap in the top 56 dp band, or 1.5 s without input while the IME is hidden.
3. **Selection non-empty:** system floating toolbar (cut/copy/paste/…) near the selection + our formatting pill docked at the bottom. The stats line (if on) shows selection counts. The drawer swipe is disarmed (platform track).
4. **Focus mode:** active sentence/paragraph normal, rest `focusDim`. Typewriter scrolling (optional) keeps the caret line at 45 % of the visible height (from the top of the visible area above the IME).

### 4.4 Formatting pill (only while text is selected)
- Placement: bottom-centre, 12 dp above the IME (or above the nav-bar inset when no IME). Uses `Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))`. Height 48 dp, fully rounded (24 dp radius), bg `surface`, 1 px `divider` border, **no shadow** (flat; see elevation tokens), horizontal padding 4 dp, buttons 48 × 48 dp with 24 dp icons tinted `text`. Hidden when the selection is empty or the editor loses focus.
- Visible slots: `n = min(8, floor((width − 32 dp) / 48 dp))`. The last slot is always **More**; the others fill in priority order:
  1. **Bold** `format_bold`: toggle `**…**`
  2. **Italic** `format_italic`: toggle `*…*` (iA screenshots use `*`)
  3. **Heading** `format_h1`: cycles the line prefix of every selected line: none → `# ` → `## ` → `### ` → none. Long-press opens a level menu H1–H6 / Body
  4. **Link** `link`: `[sel](|)` with the caret inside `()`. If the selection itself looks like a URL: `[|](sel)`
  5. **Code** `code`: single line → `` `sel` ``. Multi-line → fenced ```` ``` ```` block around whole lines
  6. **Quote** `format_quote`: toggle `> ` on selected lines
  7. **List** `format_list_bulleted`: toggle `- ` on selected lines
  8. **More** `more_horiz` → a small menu anchored above the pill: Strikethrough `format_strikethrough` (`~~`), Highlight `ink_highlighter` (`==`), Numbered list `format_list_numbered`, Task `checklist` (`- [ ] `), Code block `code_blocks`, Clear formatting `format_clear` (strip inline markers inside the selection, and block prefixes of the selected lines)
- On a 448 dp phone: n = 8 → all 7 primary + More. On 360 dp: n = 6 → Bold, Italic, Heading, Link, Code, More (Quote/List move into More).
- After an action: keep the transformed text selected (so actions chain: Bold then Italic), single undo step per action, haptic `CONFIRM` off (too noisy), no toast.
- Accessibility: each button has `contentDescription` ("Bold", …) and the editor exposes the same actions as custom accessibility actions.
- Keyboard: reuse the shortcut set in platform.md §10.1 (Ctrl+B, Ctrl+I, Ctrl+K, Ctrl+Shift+C code, Ctrl+1…6 heading, Ctrl+0 body).

### 4.5 Library drawer
- Width: `min(360 dp, screenWidth − 56 dp)` (M3 modal drawer max 360 dp). ≥ 840 dp: permanent pane 320 dp + 1 px `divider` at its end edge (platform track).
- Content (top → bottom):
  1. Header row (56 dp): title "Library" 20 sp Bold `text` at 20 dp start padding; end: search `search`, new `edit_square` (tint accent), 48 dp targets.
  2. Search field (appears in the header row on tap, replaces title; filters by file name + first 2 KB of content; Esc/back clears).
  3. **Locations** (compact list, 44 dp rows, 13 sp caps-free label "Locations" in `textSecondary`):
     - `phone_android` "On this device" (internal library)
     - `folder` "<linked folder name>" (for each SAF tree; long-press → "Stop using this folder")
     - `create_new_folder` "Use a folder…" (launches `ACTION_OPEN_DOCUMENT_TREE`), text `textSecondary`
  4. 1 px `divider`
  5. Current location header: breadcrumb "On this device › Drafts" (13 sp `textSecondary`, tap a crumb to go up) + sort button `sort` (menu: "Date modified" ✓ / "Name"; second group: "Newest first" / "Oldest first"; checkbox "Show extensions").
  6. Folder rows (48 dp): `folder` icon 20 dp + name 16 sp.
  7. File rows (72 dp, 20 dp horizontal padding):
     - Line 1: title = file name without extension (unless "Show extensions"), 16 sp, `text`, Bold when it's the open document + 3 dp accent bar on the start edge (mrowa44-style active marker).
     - Line 2: excerpt = first non-empty line after the first line, markers stripped, 1 line ellipsised, 13 sp `textSecondary`.
     - End-aligned on line 1: relative date 12 sp `textSecondary` ("14:02" today, "Yesterday", "Mon", "21 Sep", "21 Sep 2025"). Use `DateUtils.getRelativeTimeSpanString`-like rules via `java.time` + locale formatting.
     - Long-press → menu: Rename · Duplicate · Move… · Share · Delete (Delete in `danger`, undoable with a snackbar "Deleted · Undo" for 5 s).
  8. Empty state (no files): centred 16 sp `textSecondary` "No notes yet" + text button "New note".
- New-note flow: tap `+` → creates `Untitled.md` in the current location (auto-named later from the first line, platform track §2.6), closes the drawer, focuses the editor, opens the IME, caret at 0. An empty untitled note that is left empty is deleted on leave.
- Drawer open: IME hides (platform track). Selecting a file: close the drawer (modal), open the doc at its saved caret position (iA 3.0 behaviour), IME stays hidden (reading first). Tapping text shows it.

### 4.6 Preview
- Entry: swipe left (end direction) in the editor, overflow › Preview, Ctrl+R. Exit: swipe right, back gesture, Esc, or `arrow_back` glyph.
- Full-screen, same `bg`, same column/margins as editor, rendered Markdown (§3.5). Floating glyphs: `arrow_back` (top-start), `share` (top-end). No title bar (iA had "← Preview", we drop the label). Scroll position synced to the editor caret paragraph on open (iA iPhone "Scroll in Sync").
- On ≥ 840 dp **[DECIDE]**: side-by-side editor | preview toggle (overflow › "Preview beside"), each half with its own measure. Phase 2.

### 4.7 Overflow menu (the only menu in the editor)
Anchored under the top-end glyph, M3 `DropdownMenu` restyled (bg `surface`, 1 px `divider` border, 12 dp radius, no shadow), width 240 dp:
- Row 0 (icon row, 48 dp): `undo` · `redo` · `search` (Find in note) · `share`
- `edit_square` New note
- `preview` Preview
- `center_focus_strong` Focus ▸ (sub-choices: Off / Sentence / Paragraph, radio)
- Typewriter scrolling (checkable row, no icon)
- Word count (checkable row, no icon)
- `tune` Settings

(Only those. No "Help", no "About" here; About lives in Settings.)

### 4.8 Stats (optional, off by default)
- Where: centred in the idle glyph row (top). 12 sp, `textSecondary`, tabular digits. Hidden while typing like the glyphs **[DECIDE]**. iA Android kept stats visible while typing ("When typing you'll see… subtly displayed at the top"). Default here: stays visible while typing at 60 % alpha, because users who enable it want it.
- Content cycle on tap: `1,204 words` → `7,380 characters` → `58 sentences` → `5 min read` → back. With a selection: "Selected: 42 words". Counting rules = markdown track's `TextStats` (words exclude markup and URLs; reading time 238 wpm).

### 4.9 Settings (bottom sheet from overflow › Settings; full-screen on phones in landscape)
Only these rows (grouped, 56 dp rows, 16 sp label + 13 sp `textSecondary` value):

| Group | Row | Values | Default |
|---|---|---|---|
| Appearance | Theme | System · Light · Dark | System |
| | Pure black in dark | switch | off |
| Text | Typeface | Duo · Quattro · Mono (each label rendered in its own face) | Duo |
| | Text size | 6-stop slider XS–XXL with live sample line "The quick brown fox" | M |
| | Line length (only ≥ 600 dp) | 64 · 72 · 80 characters | 64 |
| Writing | Focus | Off · Sentence · Paragraph | Off |
| | Typewriter scrolling | switch | off |
| | Word count | switch | off |
| | Swipe between library, editor and preview | switch (iA: "Horizontal Swipes") | on |
| Files | New note extension | .md · .txt | .md |
| | Show file extensions | switch | off |
| About | About mdwriter | version, "Fonts: iA Writer Duo, Quattro, Mono by Information Architects Inc., based on IBM Plex. SIL Open Font License 1.1" → opens licence text | — |

Deliberately absent ("useless menus"): accounts, sync, style check, PIN, templates, export tokens, analytics, rate us, help centre, keyboard bar, line-height slider (derived from the typeface).

### 4.10 First launch / empty state
- First launch: the library is empty → create `Welcome.md` (≈ 25 lines: what the app is, "Swipe right for your notes, left for preview", "Select text to format", Markdown cheat-sheet showing `# Heading`, `**bold**`, `*italic*`, `- list`, `- [ ] task`, `> quote`, `` `code` ``, `[link](https://example.com)`). Open it with the caret at the end and the IME hidden. The live styling itself demonstrates the product.
- Later launches: reopen the last document at its last caret/scroll position. If it's gone, open the newest file. If there are no files, open a fresh empty `Untitled` with the IME shown and a placeholder hint "Start writing…" in `focusDim` (disappears on first char).

### 4.11 Motion (see tokens §5.6): chrome fades, pill fades+scales, drawer uses M3 defaults, focus dimming animates colour over 120 ms (or instantly if the diff is large; don't animate per keystroke).

---

## 5. Design tokens

### 5.1 Colours
See §2.2 (authoritative). Kotlin sketch:
```kotlin
@Immutable data class WriterColors(
  val bg: Color, val surface: Color, val surfaceHover: Color, val text: Color, val textSecondary: Color,
  val markup: Color, val focusDim: Color, val accent: Color, val selection: Color, val codeBg: Color,
  val highlightBg: Color, val highlightLine: Color, val highlightText: Color, val divider: Color,
  val scrim: Color, val danger: Color,
)
val LightWriterColors = WriterColors(
  bg = Color(0xFFF7F7F7), surface = Color(0xFFFCFCFC), surfaceHover = Color(0xFFEFEFEF),
  text = Color(0xFF1A1A1A), textSecondary = Color(0xFF6E6E6E), markup = Color(0xFF868686),
  focusDim = Color(0xFFC0C0C0), accent = Color(0xFF00B2FF), selection = Color(0x4000B2FF),
  codeBg = Color(0xFFEDEDED), highlightBg = Color(0x59FFD900), highlightLine = Color(0xFFFFD900),
  highlightText = Color(0xFF1A1A1A), divider = Color(0xFFE2E2E2), scrim = Color(0x52000000),
  danger = Color(0xFFD93A2B),
)
val DarkWriterColors = WriterColors(
  bg = Color(0xFF1A1A1A), surface = Color(0xFF141414), surfaceHover = Color(0xFF242424),
  text = Color(0xFFD0D0D0), textSecondary = Color(0xFF8C8C8C), markup = Color(0xFF7A7A7A),
  focusDim = Color(0xFF5E5E5E), accent = Color(0xFF00B2FF), selection = Color(0x4D00B2FF),
  codeBg = Color(0xFF242424), highlightBg = Color(0x2DFFD900), highlightLine = Color(0xFFCCAE00),
  highlightText = Color(0xFFDAD094), divider = Color(0xFF2E2E2E), scrim = Color(0x99000000),
  danger = Color(0xFFFF6B5E),
)
val BlackWriterColors = DarkWriterColors.copy(
  bg = Color(0xFF000000), surface = Color(0xFF0A0A0A), surfaceHover = Color(0xFF161616),
  text = Color(0xFFC8C8C8), textSecondary = Color(0xFF8A8A8A), markup = Color(0xFF707070),
  focusDim = Color(0xFF4A4A4A), selection = Color(0x5900B2FF), codeBg = Color(0xFF141414),
  divider = Color(0xFF1F1F1F), scrim = Color(0xB3000000),
)
```
EditText wiring: `setTextColor(text)`, `highlightColor = selection`, `setTextCursorDrawable(caretDrawable)`, handles `setTextSelectHandle/Left/Right(...)` tinted `accent` (API 29 setters; or theme attr `android:colorControlActivated = accent` on the activity theme), `setBackgroundColor(Transparent)` (the Compose container paints `bg`).

### 5.2 Type scale (sp; phone default step M)
| Role | Size | Weight | Line pitch | Font |
|---|---|---|---|---|
| Editor body | 17 | 400 | 28 (1.65×) | Duo (user choice) |
| H1…H6 | §3.2 | 700 | §3.2 | same |
| Code span/block | 17 (same as body) | 400 | body pitch | Mono |
| Drawer title "Library" | 20 | 700 | 28 | UI font (= Duo) |
| File row title | 16 | 400 (700 if open) | 22 | UI |
| File row excerpt | 13 | 400 | 18 | UI |
| File row date / section labels / breadcrumb | 12–13 | 400 | 16–18 | UI |
| Stats line | 12 | 400 | 16 | UI, `fontFeatureSettings = "tnum"` |
| Settings row label / value | 16 / 13 | 400 | 22 / 18 | UI |
| Menu items | 15 | 400 | 20 | UI |
| Placeholder "Start writing…" | = body | 400 | body | editor font, `focusDim` |

### 5.3 Spacing (dp)
| Token | Phone | Medium | Expanded |
|---|---|---|---|
| `marginMin` (editor side) | 24 | 32 | 48 |
| `gutterChars` (hang) | 0 | 4 | 6 |
| `measureChars` | ∞ | 64/72/80 | 64/72/80 |
| `editorTop` (below status-bar inset) | 56 | 64 | 72 |
| `editorBottomOverscroll` | 50 % of visible height | same | same |
| `chromeGlyphInset` (from safe-area edge) | 4 (48 dp target → 12 dp visual edge offset) | 8 | 12 |
| `drawerWidth` | min(360, w − 56) | 360 | 320 (permanent) |
| `rowPaddingH` | 20 | 20 | 20 |
| `fileRowHeight` / `folderRowHeight` / `locationRowHeight` | 72 / 48 / 44 | | |
| `pillHeight` / `pillButton` / `pillGapAboveIme` | 48 / 48 / 12 | | |
| `sheetPadding` | 24 | 24 | 32 |

### 5.4 Shapes & strokes
| Token | Value |
|---|---|
| Caret | 2 dp × line pitch, corner 1 dp, `accent` (GradientDrawable: `setSize(2dp, 0)`, `cornerRadius = 1dp`, `setColor(accent)`) |
| Pill | 24 dp radius (stadium), 1 px `divider` border |
| Menus / sheets | 12 dp radius menus; 20 dp top corners sheets |
| Code span bg | square (BackgroundColorSpan); preview code block 6 dp |
| Search field | 20 dp radius, `surfaceHover` fill, no border |
| Active file marker | 3 dp wide bar, full row height, `accent`, square |
| Hairlines | 1 physical px (not dp): `val hairline = with(LocalDensity.current) { 1.toDp() }` then `HorizontalDivider(thickness = hairline, color = colors.divider)` / `BorderStroke(hairline, colors.divider)`. (`Dp.Hairline` also means 1 px for `BorderStroke`, but not for layout sizes.) |

### 5.5 Elevation
Flat everywhere: `tonalElevation = 0.dp`, `shadowElevation = 0.dp`. Layers are separated by `surface` vs `bg` plus 1 px `divider` and the scrim. Exception: none. (iA's look is flat; M3 tonal tint would turn surfaces blue-grey.)

### 5.6 Motion
| What | Duration | Easing |
|---|---|---|
| Chrome glyphs + stats fade out on typing | 150 ms | `LinearOutSlowInEasing` |
| Chrome fade in | 220 ms, 0 ms delay on IME hide / 1 500 ms idle timer | `FastOutSlowInEasing` |
| Formatting pill in / out | 120 ms in (alpha 0→1, scale 0.96→1, translateY 8→0 dp) / 90 ms out | M3 emphasized decelerate `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)` / accelerate `(0.3f, 0f, 0.8f, 0.15f)` |
| Drawer open/close | M3 default (`ModalNavigationDrawer`) | — |
| Preview enter/exit | 250 ms horizontal slide (full width) + fade | emphasized decelerate / accelerate |
| Focus dim change (sentence moves) | 120 ms colour crossfade, or none if > 2 000 chars change | linear |
| Typewriter scroll adjust | 180 ms `scrollTo` animation (`Scroller`/`smoothScrollBy`) | platform default |
| Caret blink | platform (Editor `BLINK = 500` ms on/off; AOSP) | — |
| Settings sheet | M3 `ModalBottomSheet` default | — |
Respect "Remove animations" (`Settings.Global.ANIMATOR_DURATION_SCALE == 0` → Compose honours it automatically; for View animations use `ValueAnimator.areAnimatorsEnabled()`).

---

## 6. Wireframes (phone 448 dp unless noted; `│` = screen edge; `▌` = caret; `░` = dimmed text)

### 6.1 Editor, idle (keyboard hidden)
```
│ 9:41                              ▾ ▴ ▮ │  ← transparent status bar
│ ≡                  412 words          ⋯ │  ← 48dp glyph targets, textSecondary; stats only if enabled
│                                         │
│   ## A walk in the rain                 │  ← "## " body-size, text colour; title 1.4× Bold (inline on phones)
│                                         │
│   It had been raining since noon, and   │  ← 17sp Duo, 28sp pitch, 24dp margins, ≈39 chars
│   the city smelled of wet stone. She    │
│   took the long way home.▌              │  ← 2dp #00B2FF caret, full line height
│                                         │
│   - bread                               │
│   - [ ] call **Ana**                    │  ← "[ ]" markup grey; ** text colour, Bold
│   See [notes](https://example.com).     │  ← "[", "](https://example.com)" markup grey
│                                         │
│                                         │
│                                   (bg #F7F7F7, nothing else)
│               ─────                     │  ← gesture nav handle (system)
```
`≡` = Material Symbol `left_panel_open` (library). `⋯` = `more_vert`.

### 6.2 Editor while typing (chrome hidden)
```
│ 9:41                              ▾ ▴ ▮ │
│                                         │  ← glyphs faded out (150 ms)
│   ## A walk in the rain                 │
│                                         │
│   It had been raining since noon, and   │
│   the city smelled of wet stone. She    │
│   took the long way home. Puddles▌      │
│                                         │
├─────────────────────────────────────────┤
│  q  w  e  r  t  y  u  i  o  p           │  ← IME only; NO keyboard bar
│   a  s  d  f  g  h  j  k  l             │
│  ⇧  z  x  c  v  b  n  m  ⌫              │
│  ?123  ,  ␣␣␣␣␣␣␣␣␣␣␣␣  .  ⏎            │
```
Focus mode (Sentence) variant: all lines except "Puddles…" sentence rendered `░` `#C0C0C0`; typewriter keeps the caret line at 45 % of the visible area.

### 6.3 Text selected + formatting pill
```
│   ## A walk in the rain                 │
│        ┌─────────────────────────────┐  │  ← SYSTEM floating toolbar (unchanged)
│        │ Cut  Copy  Paste  Share  ⋮  │  │
│        └─────────────────────────────┘  │
│   It had been ▐raining since noon▌, and │  ← selection #00B2FF @25 %, blue teardrop handles
│   the city smelled of wet stone.        │
│                                         │
│  ╭───────────────────────────────────╮  │  ← our pill: 48dp, surface #FCFCFC, 1px #E2E2E2, r=24
│  │  B   I   H   🔗   <>   ❝   •≡   ⋯ │  │    Bold · Italic · Heading · Link · Code · Quote · List · More
│  ╰───────────────────────────────────╯  │  ← 12dp above IME
├─────────────────────────────────────────┤
│  (IME)                                  │
```
More menu (opens upward from `⋯`): `S̶` Strikethrough · `🖍` Highlight · `1.` Numbered list · `☑` Task · `{ }` Code block · `T̸` Clear formatting.

### 6.4 Library drawer open (modal)
```
│┌────────────────────────────────┐░░░░░░│  ← drawer 360dp max, surface; scrim over editor
││ Library                 🔍   ✎ │░░░░░░│  ← 20sp Bold; search; new note (accent)
││                                │░░░░░░│
││ Locations                      │░░░░░░│  ← 13sp textSecondary
││ 📱 On this device              │░░░░░░│  ← selected location: text Bold
││ 📁 Notes (Syncthing)           │░░░░░░│  ← SAF folder name
││ ⊕ Use a folder…                │░░░░░░│  ← textSecondary
││────────────────────────────────│░░░░░░│  ← 1px divider
││ On this device › Drafts    ⇅   │░░░░░░│  ← breadcrumb + sort
││ 📁 Archive                     │░░░░░░│  ← 48dp folder row
││▌A walk in the rain       14:02 │░░░░░░│  ← open doc: 3dp accent bar, Bold title
││ It had been raining since…     │░░░░░░│  ← excerpt 13sp textSecondary
││ Groceries             Yesterday│░░░░░░│
││ bread, call Ana                │░░░░░░│
││ Ideas for the talk       21 Sep│░░░░░░│
││ Start with the question…       │░░░░░░│
│└────────────────────────────────┘░░░░░░│
```
Icons: `phone_android`, `folder`, `create_new_folder`, `search`, `edit_square`, `sort`, `folder` (rows).

### 6.5 Preview
```
│ ←                                    ⇪ │  ← arrow_back, share (floating glyphs, idle only)
│                                         │
│   A walk in the rain                    │  ← H2 1.4× Bold, no markers
│                                         │
│   It had been raining since noon, and   │
│   the city smelled of wet stone. She    │
│   took the long way home.               │
│                                         │
│   – bread                               │  ← en-dash bullet hangs in margin
│   ☐ call Ana                            │  ← "Ana" Bold
│   See notes.                            │  ← "notes" underlined 1dp markup-grey
```

### 6.6 Settings sheet
```
│░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│  ← scrim
│╭────────────────────────────────────────╮│  ← ModalBottomSheet, surface, r=20 top
││              ─────                     ││  ← drag handle
││ Appearance                             ││  ← 13sp textSecondary
││ Theme                         System ▾ ││
││ Pure black in dark               ( ○) ││
││ Text                                   ││
││ Typeface            Duo · Quattro · Mono││ ← segmented, each in its own face
││ Text size   A ──────●──────── A        ││ ← live sample below
││ The quick brown fox jumps over…        ││
││ Writing                                ││
││ Focus               Off · Sentence · ¶ ││
││ Typewriter scrolling             ( ○) ││
││ Word count                       ( ○) ││
││ Swipe to library & preview       (● ) ││
││ Files                                  ││
││ New note extension           .md  ▾   ││
││ Show file extensions             ( ○) ││
││ About mdwriter                      ›  ││
│╰────────────────────────────────────────╯│
```
Switch colours: checked track `accent`, thumb `#FFFFFF`; unchecked track `surfaceHover`, border `divider`.

### 6.7 First launch / empty state
```
│ ≡                                     ⋯ │
│                                         │
│   # Welcome                             │
│                                         │
│   This is a quiet place to write.       │
│   Swipe right for your notes, swipe     │
│   left to preview. Select text to       │
│   format it.                            │
│                                         │
│   ## Markdown in ten seconds            │
│   **bold**  *italic*  `code`            │
│   - list    - [ ] task    > quote       │
│   [a link](https://example.com)▌        │
```
Drawer empty state (a location with no files): centred "No notes yet" (16 sp `textSecondary`) + text button "New note" (`text` colour, `edit_square` icon in `accent`).

### 6.8 Tablet / expanded (≥ 840 dp, e.g. 1280 × 800 dp landscape)
```
│┌──────────────────────┐│                                                          │
││ Library        🔍  ✎ ││  ≡  (hides pane)                                     ⋯   │
││ Locations            ││                                                          │
││ 📱 On this device    ││          # A walk in the rain                            │  ← "# " HANGS in the 6-char gutter
││ 📁 Notes             ││            ↑ text column starts here                     │
││ ⊕ Use a folder…      ││          It had been raining since noon, and the city   │  ← 64-char measure, centred
││──────────────────────││          smelled of wet stone. She took the long way    │
││ Drafts           ⇅   ││          home.▌                                         │
││▌A walk in the rain   ││                                                          │
││ Groceries            ││          - bread                                         │
││ Ideas for the talk   ││            wrapped list lines align under "bread"        │
│└──────────────────────┘│                                                          │
   320dp permanent pane + 1px divider   editor area: bg #F7F7F7, 18sp, 1.75× pitch
```
The `≡` glyph toggles pane visibility (`left_panel_close`/`left_panel_open`). With the pane hidden, the column re-centres. 600–839 dp: modal drawer (as phone), 4-char gutter, measure applies.

---

## 7. "iA-isms" checklist (for acceptance testing)
- [ ] Warm-neutral light grey page `#F7F7F7`, near-black `#1A1A1A` text; dark `#1A1A1A` / `#D0D0D0`; optional true black.
- [ ] Electric-blue 2 dp caret spanning the full line height; blue selection handles.
- [ ] Duospace writing font by default (m/w wider); Quattro and Mono selectable; code always Mono.
- [ ] Generous line height (≈ 1.65× on phones, 1.75× on large).
- [ ] Text column limited to 64/72/80 characters on large screens, centred, with the heading `#`s hanging in the margin.
- [ ] Markdown syntax stays visible (no hiding, no WYSIWYG). Headings/emphasis restyle live while you type; links' URLs and brackets turn grey.
- [ ] Wrapped list/quote lines indent under the text, not under the marker.
- [ ] No chrome while typing: the top glyphs vanish on the first keystroke and come back when you stop/hide the keyboard.
- [ ] No keyboard bar, no FAB, no app bar, no bottom bar in the editor.
- [ ] Swipe right → library, swipe left → preview (one setting to turn off).
- [ ] Focus mode: sentence or paragraph, everything else greyed; typewriter scrolling keeps the caret line centred.
- [ ] Stats are opt-in, tiny, at the top, and switch to selection stats.
- [ ] Settings fit on one sheet; no accounts, no cloud, no analytics, no nag.
- [ ] Reopening a note restores caret and scroll.
- [ ] Autosave: there is no Save button (a Ctrl+S tick is fine).
- [ ] Night mode reduces glare (`#D0D0D0`, not white) and the pure-black variant for OLED.
- [ ] Flat surfaces: no shadows, no tonal tint, hairline dividers only.
- [ ] `==highlight==` in soft yellow.
- [ ] Completed tasks both faded and struck through.

---

## 8. App name & launcher icon

### 8.1 Name
- Working/package name stays `mdwriter` (don't rename modules or packages).
- Launcher label proposals **[DECIDE]**:
  1. **Margin** (primary): nods to the hanging markers and the "writing in the margin" idea. Short, 6 letters, fits under an icon.
  2. **Longhand**: writing by hand, unhurried.
  3. **Draftline**.
  Avoid anything containing "Writer", "iA", "Focused Writing", "Duo/Quattro" (those are iA names/fonts).
- `android:label` in the manifest via `@string/app_name`. The debug build suffix label: "Margin (debug)".

### 8.2 Adaptive icon: geometry (VectorDrawable, viewport 108 × 108, safe zone = circle r 33 centred at 54,54)
Concept: a grey `#` hangs to the left of a heavy heading bar; two lighter text lines below; a small blue caret at the end of the last line. It's the app's typographic idea (hanging markup + caret), with no letterforms from iA's logo.

`res/drawable/ic_launcher_background.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <path android:fillColor="#F7F7F7" android:pathData="M0,0H108V108H0Z"/>
</vector>
```
`res/drawable/ic_launcher_foreground.xml` (colours: hash `#9A9A9A`, ink `#1A1A1A`, caret `#00B2FF`)
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
  <!-- hanging '#': two vertical bars x=34.5/39.5 w=2.5 y=37..51, two horizontal bars x=32..45 y=40.5/45.5 h=2.5, r=1.25 -->
  <path android:fillColor="#9A9A9A" android:pathData="M35.75,37H35.75A1.25,1.25 0,0 1 37,38.25V49.75A1.25,1.25 0,0 1 35.75,51H35.75A1.25,1.25 0,0 1 34.5,49.75V38.25A1.25,1.25 0,0 1 35.75,37Z M40.75,37H40.75A1.25,1.25 0,0 1 42,38.25V49.75A1.25,1.25 0,0 1 40.75,51H40.75A1.25,1.25 0,0 1 39.5,49.75V38.25A1.25,1.25 0,0 1 40.75,37Z M33.25,40.5H43.75A1.25,1.25 0,0 1 45,41.75V41.75A1.25,1.25 0,0 1 43.75,43H33.25A1.25,1.25 0,0 1 32,41.75V41.75A1.25,1.25 0,0 1 33.25,40.5Z M33.25,45.5H43.75A1.25,1.25 0,0 1 45,46.75V46.75A1.25,1.25 0,0 1 43.75,48H33.25A1.25,1.25 0,0 1 32,46.75V46.75A1.25,1.25 0,0 1 33.25,45.5Z"/>
  <!-- heading bar x=49 y=39.5 w=27 h=8.5 r=2.25; text lines x=49 y=55 w=25 h=4 r=2 and x=49 y=63 w=15 h=4 r=2 -->
  <path android:fillColor="#1A1A1A" android:pathData="M51.25,39.5H73.75A2.25,2.25 0,0 1 76,41.75V45.75A2.25,2.25 0,0 1 73.75,48H51.25A2.25,2.25 0,0 1 49,45.75V41.75A2.25,2.25 0,0 1 51.25,39.5Z M51,55H72A2,2 0,0 1 74,57V57A2,2 0,0 1 72,59H51A2,2 0,0 1 49,57V57A2,2 0,0 1 51,55Z M51,63H62A2,2 0,0 1 64,65V65A2,2 0,0 1 62,67H51A2,2 0,0 1 49,65V65A2,2 0,0 1 51,63Z"/>
  <!-- caret x=67 y=60 w=2.5 h=10 r=1.25 -->
  <path android:fillColor="#00B2FF" android:pathData="M68.25,60H68.25A1.25,1.25 0,0 1 69.5,61.25V68.75A1.25,1.25 0,0 1 68.25,70H68.25A1.25,1.25 0,0 1 67,68.75V61.25A1.25,1.25 0,0 1 68.25,60Z"/>
</vector>
```
`res/drawable/ic_launcher_monochrome.xml`: identical to the foreground, but **all three paths `android:fillColor="#FFFFFFFF"`** (the system tints the monochrome layer; only alpha matters). Optionally merge them into one path.

`res/mipmap-anydpi/ic_launcher.xml`
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@drawable/ic_launcher_background"/>
  <foreground android:drawable="@drawable/ic_launcher_foreground"/>
  <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>
</adaptive-icon>
```
Bounding box of the artwork: x 32–76, y 37–70 (44 × 33 units, centred at ≈ 54, 53.5), well inside the 66-unit safe zone. Rendered previews (light/dark/themed) are in `scratchpad/icon/row2.png`. The path data was generated by a script (`scratchpad/icon/paths.txt`), not hand-typed.
Splash: platform default uses this icon on `windowSplashScreenBackground = bg`. No `windowSplashScreenIconBackgroundColor` needed.
A dark variant of the launcher icon is not needed (Android has no per-theme launcher icon except themed/monochrome).

---

## 9. Material Symbols icons (all names verified present in fonts.google.com/metadata/icons, family Material Symbols, 2026-09-25)
Style: **Rounded**, weight 400, fill 0, grade 0, optical size 24. Download the Android XML from
`https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<name>/materialsymbolsrounded/<name>_24px.xml` (pattern verified HTTP 200 for `format_bold`; the Outlined path is `…/materialsymbolsoutlined/<name>_24px.xml`, weight variants like `<name>_wght300_24px.xml`). Save as `res/drawable/ic_<name>.xml`. **Delete the `android:tint="?attr/colorControlNormal"` line** (that attr exists only with AppCompat/MDC; without them AAPT fails to link). Tint in Compose via `Icon(painterResource(R.drawable.ic_x), contentDescription, tint = colors.text)`.

| Where | Icons |
|---|---|
| Editor glyphs | `left_panel_open` (library, phones & pane hidden), `left_panel_close` (pane shown, ≥ 840 dp), `more_vert` (overflow), `arrow_back` (preview back; auto-mirrored), `share` |
| Overflow menu | `undo`, `redo`, `search`, `share`, `edit_square` (new note), `preview`, `center_focus_strong` (focus), `notes` (typewriter/word count rows, optional), `tune` (settings) |
| Formatting pill | `format_bold`, `format_italic`, `format_h1`, `link`, `code`, `format_quote`, `format_list_bulleted`, `more_horiz` |
| Pill › More | `format_strikethrough`, `ink_highlighter`, `format_list_numbered`, `checklist`, `code_blocks`, `format_clear`; heading level menu: `format_h1`…`format_h6`, `notes` (body text) |
| Drawer | `search`, `edit_square`, `close` (clear search), `phone_android` (on this device), `folder`, `folder_open` (current folder), `create_new_folder` ("Use a folder…" and new folder), `sort`, `check` (menu check), `description` (file row icon if ever needed), `chevron_right` (breadcrumb separator), `link_off` (stop using folder) |
| File context menu | `drive_file_rename_outline` (rename), `content_copy` (duplicate), `drive_folder_upload` (move), `share`, `delete` |
| Preview | `arrow_back`, `share`, `check_box_outline_blank`, `check_box` (task glyphs) |
| Settings | `dark_mode`, `light_mode`, `contrast` (theme), `text_fields` (typeface), `format_size` (text size), `center_focus_strong`, `info` (about), `open_in_new` (licence link) |
| Find bar (platform/editor tracks) | `search`, `find_replace`, `match_case`, `expand_less`, `expand_more`, `close` |

(`footnote` doesn't exist in Material Symbols. Don't reference it.)
Total ≈ 45 small XMLs (≈ 0.5–1 KB each).

---

## 10. Open questions / unverified
1. **[DECIDE]** Font/trade-dress stance (see TL;DR note): personal use with iA fonts (default), or IBM Plex Mono as default if the app will ever be published.
2. **[DECIDE]** App label: Margin / Longhand / Draftline.
3. **[DECIDE]** Dim emphasis/heading markers (`DIM_EMPHASIS_MARKERS`)? iA doesn't (evidence), but some users prefer it.
4. **[UNVERIFIED]** Negative `LeadingMarginSpan` trick for hanging `#` (§3.3). Prototype on device in the editor track. Fallback: no hanging.
5. **[UNVERIFIED]** iA's default line length (64 vs 72). We default to 64.
6. **[UNVERIFIED]** `LineHeightSpan.Standard` giving a full-height caret (§3.1). It follows from AOSP Editor.java, but check on device, including the last line and empty lines.
7. Stats visible while typing: iA does it; we default to visible at 60 % alpha (§4.8).
8. The system floating toolbar and our bottom pill are both visible during selection. If the user dislikes two surfaces, the alternative is to add formatting items to the system toolbar through `customSelectionActionModeCallback` (they'd end up in its overflow on phones). Not recommended.
9. iA reduces body weight (≈ 455 → 435) in dark mode with variable fonts. Not possible with static fonts. Future: bundle the V fonts and use `setFontVariationSettings("'wght' 440")`.

---

## 11. Sources
Primary / local:
- iA Writer for Android support pages (archived via web.archive.org, texts in `scratchpad/wb/`): editor (2023-03-22), document list (2023-03-22), core components (2024-05-30), settings-android (2026-04-12), focus-mode-android (2026-06-14), keyboard-bar-android (2024-02-27), stats-android (2026-06-11), navigation-android (2026-04-11), FAQ-android (2026-04-14), release notes (2023-01-30, 2026-04-12), export-android (2026-04-12), modify-preview-android (2026-04-11). URLs are recorded in each file's `SOURCE` line, e.g. https://web.archive.org/web/20260412021709/https://ia.net/writer/support/basics/settings/settings-android
- ia.net pages (scratchpad/live/): https://ia.net/topics/in-search-of-the-perfect-writing-font, https://ia.net/topics/a-typographic-christmas, https://ia.net/topics/turbocharged-ia-writer-for-android, https://ia.net/topics/starting-out-on-android, https://ia.net/writer/support/basics/settings/settings-iphone, …/settings-mac, …/focus-mode-iphone, …/stats-iphone, …/navigation-iphone, https://ia.net/writer/support/editor/syntax-highlight
- Screenshots: https://static.ia.net/writer/landing/iA-Writer-in-Focus-Mode-slide.png, …/iAW-alice-writing-formatting-desktop.png, …/writer-compare-iaw-mobile-c7720d.webp, https://static.ia.net/writer/ia-writer-ios-focus-mode-i2510p.webp, …/iaw-alice-focus-desktop-smaller-vuga7w.webp; Play Store listing for `net.ia.iawriter.x` (15 screenshots, play-lh.googleusercontent.com URLs in `scratchpad/play_shots.txt`, downloaded to `scratchpad/shots/play/`).
- Locally installed iA Writer 8.0.7 for Mac (`/Applications/iA Writer.app`): TypographyKit `Duo/Quattro/Mono.plist` (→ `scratchpad/typo_*.json`), `Kit.framework/Resources/mark.css` (highlight colours), `Templates/Duo.iatemplate/.../style.css` (preview rules), `Assets.car` colour dump (`scratchpad/ia_assets.json`). Only numeric values were read. Nothing from the bundle may be copied into the project.
- Fonts: https://github.com/iaolo/iA-Fonts (README, per-family LICENSE.md, Static TTFs; raw URLs HEAD-verified 2026-09-25).
- OFL FAQ: https://openfontlicense.org/ofl-faq/ (Q 1.4, 1.12, 1.20, 2.2, 2.6, 2.8, 5.3).
- Material Symbols metadata: https://fonts.google.com/metadata/icons?key=material_symbols (4 254 names; saved `scratchpad/icons/meta.json`); Android XML: https://github.com/google/material-design-icons/tree/master/symbols/android
- AOSP sources salvaged in scratchpad: `TextView.java` (onDraw clip at `compoundPaddingLeft`, ~line 9350), `Editor.java` (`BLINK = 500`, line 186; cursor bounds use `getLineBottom(line, includeLineSpacing=false)`, ~line 2440).
Secondary:
- mrowa44 Obsidian "iA Writer"-like theme (`scratchpad/themes/mrowa44.css`), rcvd theme (`scratchpad/themes/rcvd.css`), ia.net site CSS (`scratchpad/ia_main.css`: `::selection rgba(47,190,234,.25)`, accent `#00B2FF`).

---

## Appendix A — raw measurement notes (from the salvage pass)
- Caret colour sampled: `#00C3FF`–`#01C4FF` (Mac/iOS), `#00B2FF` (Android Play hi7.png: 9 px × 98 px at 2560 w; line pitch 100 px → caret = 0.98 × pitch).
- Android Play hi7.png dark: bg `#191919`, active text core `#D1D1D1`/max `#D7D7D7`, focus-dim `#727272`–`#767676`, `*never*` stars same as dim.
- Android Play hi4.png light: bg `#F9F9F9`, text/`#`/`*` core `#000000`, URL `#828282`–`#888888`, `![` `#6A6A6A`–`#717171`.
- iPhone: ~36 chars/line at 393 pt, margins ≈ 21 pt, font ≈ 15.5–16 pt, pitch ≈ 24.6 pt.
- Mac 2× shot: bg `#1A1A1A`, text/markers `#CCCCCC`, preview pane bg `#FCFCFC`, text `#1A1A1A`.
- Assets.car named colours (iA Writer Mac): P3 (0, 211, 254) → sRGB `#00D7FF`; P3 (0, 170, 255) → `#00ADFF`; grays 50/50/50, 20/20/20, 236/255.
- Contrast: `#1A1A1A`/`#F7F7F7` 16.25; `#868686`/`#F7F7F7` 3.40; `#00C3FF`/`#F7F7F7` 1.91; `#D1D1D1`/`#1A1A1A` 11.4; `#757575`/`#1A1A1A` 3.78; `#00B2FF`/`#1A1A1A` 7.3.
