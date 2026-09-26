# Research reports (background evidence)

These reports were produced on 2026-09-24/25 by research agents that verified facts against primary sources and ran
real experiments on this Mac (builds, an Android 17 emulator benchmark app, parser benchmarks, font/URL checks).
They are **background**: the plan files (`00`–`02`, `tasks/`) are authoritative. File paths inside the reports that
point to a `scratchpad/` directory refer to a temporary folder that no longer exists — the important artifacts were
copied to `plans/reference/`.

| Report | What's in it |
|---|---|
| `toolchain.md` | AGP/Gradle/Kotlin/Compose versions, compatibility tables, wrapper bootstrap commands, verified build files, test setup pitfalls |
| `platform.md` | Storage (internal vs SAF), intents, swipe-gesture analysis (why not edge swipes / M3 drawer drag), predictive back, insets/IME, large screens, Android 16/17 behaviour changes, architecture |
| `editor-engine.md` | Compose vs EditText benchmarks, the MdEditable trick, reconcile restyle, span classes, selection toolbar suppression, undo, focus overlay, code sketches (with platform source line citations) |
| `markdown.md` | Parser comparison, token model, incremental algorithm, 57 curated test cases, SmartEdit behaviour table, stats rules, Preview/WebView security |
| `design.md` | iA Writer analysis: fonts & licence, sampled colours, typography, Android interaction model, tokens, wireframes, icon geometry |
| `build.md` | Signing policy, versionCode strategy, the Makefile, README install text, error-code reference |
| `factcheck.md` | Adversarial check of 44 claims: **read this before trusting any report detail** |

## Known errors in the reports (already corrected in the plan)
- **editor-engine.md §5.8/§6.6 and design.md §3.1**: scroll room / typewriter room via large TextView padding is
  broken — TextView clips its padding bands while self-scrolled (factcheck A15). The plan hosts the EditText in a
  `ScrollView` instead (01-architecture §4.4).
- **editor-engine.md §5.2 (`HangRoomSpan : UpdateLayout`)**: a document-wide span must not implement `UpdateLayout`
  (two full reflows per keystroke; factcheck A17).
- **platform.md §6 sketch**: `clipToPadding` does not exist on EditText (compile error), and padding must not change
  on IME toggles (factcheck A16, C9).
- **Package names** differ between reports (`app.mdwriter`, `mdwriter.markdown`): the plan uses `dev.mdwriter` everywhere.
- **Tokenizer API**: editor-engine.md assumes `applyEdit/rescan`; the real API is `MarkdownHighlighter.update(...)`
  returning `HighlightDelta` (factcheck C2).
- **Marker colours**: editor-engine.md dims `#`, `*`, `>`, `-`; the design (iA-faithful) keeps prose markers in the
  text colour (factcheck C4) — follow `02-design-spec.md` §4.
- **Selection toolbar**: design.md keeps the system toolbar + a bottom pill; the plan uses one custom pill anchored at
  the selection (factcheck C8).
- **Gradle 9.8.0** went final on 2026-09-24; the plan deliberately stays on 9.7.1 (factcheck T2).
- **toolchain.md §8.8 .gitignore**: an inline `# comment` after `.kotlin/` breaks the pattern (build.md §6.1);
  use `plans/reference/gitignore`.
