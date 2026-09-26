# 00 — Product: what we are building and why

## The one-liner
**mdwriter** is a private, offline Markdown editor for Android 16/17 that looks and feels like iA Writer: a calm page,
live-styled Markdown, a blue caret, and nothing else on screen while you write.

## The user's requirements (verbatim intent → where it is satisfied)

| # | Requirement | Satisfied by |
|---|---|---|
| R1 | Mimic iA Writer's UI aesthetics | `02-design-spec.md` (tokens, fonts, iA-isms), T02, T05–T09, T12–T17 |
| R2 | No useless menus; ~100 % writing area | One overflow menu, two fading glyphs, no app bar/FAB/keyboard bar (`02` §5, §9) — T13 |
| R3 | Full Markdown support (CommonMark + GFM) | Incremental highlighter: headings, emphasis, strike, code, fences, quotes, lists, tasks, links/images, refs, autolinks, footnotes, tables, HTML, front matter — T03, T06 |
| R4 | Live in-editor preview: `# Title` becomes **bigger** as you type | Heading size spans applied within the same frame as the keystroke — T06, T07 |
| R5 | Only the current 1–2 Android versions | minSdk 36 (Android 16), target/compile 37 (Android 17) — T01 |
| R6 | No logins, no accounts, "no nothing" | No INTERNET permission, no analytics/crash SDKs, local files only — T01, enforced by lint/manifest review in T20 |
| R7 | Helper toolbar only appears when text is selected | Selection pill replaces the system toolbar, hidden otherwise — T09 |
| R8 | Swipe gesture to open other files from a sidebar | Swipe anywhere in the editor → library drawer — T12, T13 |
| R9 | Modern best practice | Kotlin 2.4, AGP 9.3, Compose BOM 2026.09, UDF + StateFlow, edge-to-edge, predictive back, adaptive layouts, R8, baseline profiles — all tasks |
| R10 | `make install` gets it on my phone | Makefile (verified): builds a release, signs it with your own key, installs over USB or Wi-Fi, launches — T01, T22 |

## Scope of v1 (all in the plan)
- Editor with live Markdown styling, iA typography, Focus Mode, typewriter scrolling, stats (opt-in).
- Selection toolbar with formatting + clipboard; smart lists; undo/redo; hardware-keyboard shortcuts; find & replace.
- Library: internal notes + optional linked folder (e.g. a Syncthing folder); folders; search; rename/duplicate/move;
  undoable delete; auto-naming from the first line.
- Preview (rendered Markdown), share, export all notes as .zip, open `.md` files from other apps, share text into a new note.
- Light / dark / pure-black themes; Duo / Quattro / Mono; text sizes; line length on large screens.
- Tablet/foldable layout with a permanent library pane.

## Explicit non-goals (v1)
Cloud sync, accounts, collaboration, Play Store publishing, WYSIWYG (hiding syntax), style check / parts-of-speech
highlighting, templates, PDF/DOCX export (print-to-PDF from Preview is a possible later add), widgets, handoff,
variable fonts, PIN lock.

## Defaults chosen on the user's behalf (easy to change later)

| Decision | Default | Where to change |
|---|---|---|
| Where notes live | Inside the app ("On this device"); "Use a folder…" links any folder (e.g. `Documents/…`) so other apps/sync tools can see the files | Library drawer |
| Android Auto Backup of notes | **On** (Google's encrypted device backup; covers the uninstall/data-loss case). Set `android:allowBackup="false"` if you want zero cloud involvement | `AndroidManifest.xml` (T11) |
| Launcher label | "mdwriter" | `res/values/strings.xml` |
| `==highlight==` syntax | Off | Settings |
| Dim `#`/`*` markers | Off (iA keeps them in text colour; only URLs, backticks, pipes… are grey) | constant `DIM_EMPHASIS_MARKERS` |
| Fonts | iA Writer Duo/Quattro/Mono (OFL) — fine for personal sideloaded use. If the app is ever published, iA asks people not to clone their product: switch to IBM Plex and rethink the look | T02 |

## Glossary
- **Hanging markers** — iA puts `#` marks in the left margin so heading text aligns with body text (≥ 600 dp only).
- **Focus Mode** — dims everything except the current sentence/paragraph.
- **Typewriter scrolling** — keeps the line you are typing at a fixed height on screen.
- **Reconcile** — our restyle step: compare wanted spans with existing spans on dirty lines and change only the difference.
- **Scroll room** — empty padding above/below the text so the first/last lines can scroll to a comfortable height.
