# mdwriter — implementation plan

A native Android Markdown editor in the spirit of iA Writer. This folder is the complete plan: product spec, architecture
contract, design spec, 22 executable tasks, verified reference code, and the research that backs every decision.

```
plans/
├─ README.md            ← you are here: how to run the plan, rules for agents, task index
├─ STATUS.md            ← progress log; every task appends to it
├─ 00-product.md        ← what & why (requirements → tasks), defaults chosen, non-goals
├─ 01-architecture.md   ← THE CONTRACT: stack, modules, packages, interfaces, threading, hard rules
├─ 02-design-spec.md    ← tokens, typography, per-Markdown-construct styling, toolbar, drawer, settings
├─ tasks/T01…T22        ← one self-contained task per file
├─ reference/           ← VERIFIED code to copy (build files, Makefile, markdown engine + tests, scripts, bench)
└─ research/            ← background reports (evidence, measurements, sources) + fact-check
```

## How to run this plan (for the human)

1. Tasks are meant to be done **in order**, one per Sonnet session. Each takes roughly 1–3 hours of agent time.
2. Start a fresh session in this repo and paste:

   > You are implementing **task T05** of the mdwriter plan. Read, in this order: `plans/README.md` (especially "Rules
   > for agents"), `plans/STATUS.md`, `plans/01-architecture.md`, `plans/02-design-spec.md`, then
   > `plans/tasks/T05-*.md`. Implement exactly that task, meet every acceptance criterion, append your entry to
   > `plans/STATUS.md`, and commit. Do not start the next task.

3. Review the STATUS entry and the commit, then move to the next task. Milestone checkpoints below tell you when
   there is something worth trying on the emulator or your phone.
4. When a task says **STOP-AND-ASK**, the agent will stop and report instead of guessing — answer it and re-run.

Tasks that can run in parallel sessions (different files, no ordering dependency): T02 ∥ T03/T04, and T10 ∥ T05–T09.
When in doubt, run sequentially.

### Milestones
| After | You can… |
|---|---|
| T01 | `make install` a hello-world app on the emulator/phone (validates the whole toolchain + signing) |
| T07 | type Markdown in a live-styled editor (headings grow as you type) — try `make install-debug` on the emulator |
| T09 | select text and format it from the pill |
| T13 | keep real notes: library, autosave, swipe to open files → **first version worth installing on your phone** |
| T19 | full feature set (folders, focus, preview, settings, find, share/export) |
| T22 | final QA'd release on your phone |

### Installing on your phone (once T01 is done)
See the README section "Install on your phone" that T01 creates: enable Developer options → USB debugging (or Wireless
debugging + `make pair`), then `make install`. The first run creates your signing key in `~/.config/mdwriter/` —
**back it up** (`make keystore-info`); every future update needs it.

## Rules for agents (read before every task)

**Scope**
1. Do exactly one task. Read its "Depends on" tasks' STATUS entries first. Don't refactor unrelated code; don't
   pre-build future tasks (small forward-compatible seams mentioned in the task are fine).
2. `plans/01-architecture.md` is the contract (packages, class names, interfaces, hard rules §10). If reality forces a
   deviation, make the smallest one, record it in STATUS.md under "Deviations", and update `01-architecture.md` in the
   same commit. (Many task files say "update 01 §X in this commit": those contract updates were already applied during
   planning — just confirm they are there and only edit 01 if your implementation had to differ.)
3. **Never bump library/plugin versions** or add new dependencies unless the task says so. If you truly need one,
   STOP-AND-ASK.
4. **STOP-AND-ASK** (write the question in STATUS.md and end the session) when: a verification gate in the task
   fails and the task gives no fallback; a hard rule would have to be broken; the task is ambiguous in a way that
   changes user-visible behaviour.

**Devices, keys, data — safety**
5. Only ever install onto the **emulator**. Start it with `make emulator`, find its serial with `make devices`, and
   always pass it explicitly: `make install-debug DEVICE=emulator-5554`. Never install to, uninstall from, or run
   commands against a physical phone.
6. Never create or touch the user's real signing key. Never run `make keystore`, `make install` or `make apk` without
   `KEYSTORE_DIR=` pointing at the shared throwaway dir `KEYSTORE_DIR=/tmp/mdwriter-agent-key` (the SAME dir in every task,
   or the emulator rejects the update with INSTALL_FAILED_UPDATE_INCOMPATIBLE). Only T21/T22 may remove that
   throwaway-signed release app, from the emulator only, with `make uninstall CONFIRM=yes`. Never read or write
   `~/.config/mdwriter/`. Prefer `make install-debug` (debug key, separate `dev.mdwriter.debug` app) for testing.
7. Never commit secrets, keystores, `local.properties`, fonts you didn't download via the script, or personal notes.

**Quality bar**
8. Before declaring done: `make check` passes (spotless + lint + all JVM tests + release build). If the task has
   instrumented tests: `make test-device DEVICE=<emulator>` passes.
9. UI tasks: install on the emulator and **look at it**: `adb -s <serial> exec-out screencap -p > /tmp/shot.png`,
   then open the PNG. Compare against `02-design-spec.md`. Put screenshots you rely on in the STATUS entry
   (describe them; don't commit them unless the task asks).
10. Write tests as the task specifies. Prefer fakes over mocks. JVM tests for anything that doesn't need Android.
11. Kotlin style: ktlint_official (Spotless). `make format` fixes most things. Public API in `:core:markdown` needs
    explicit visibility (explicitApi).
12. Adb is not on PATH: the Makefile finds it (`~/Library/Android/sdk/platform-tools/adb`). For raw Gradle calls use
    `make` targets, or `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` first (never Homebrew's JDK 27).

**Finishing**
13. Append a STATUS.md entry (template inside that file): what was done, verification evidence (commands + results),
    deviations, follow-ups/known issues, and anything the next task must know.
14. Commit once at the end (or a few logical commits) with message `Txx: <short summary>`. Do not push unless asked.

## Task index

| # | Task | Depends on | Size |
|---|---|---|---|
| T01 | [Project bootstrap, Makefile, installable skeleton](tasks/T01-bootstrap.md) | — | M |
| T02 | [Design system: theme, fonts, icons, launcher icon, splash](tasks/T02-design-system.md) | T01 | M |
| T03 | [Markdown highlighter port + full test suite (`:core:markdown`)](tasks/T03-markdown-highlighter.md) | T01 | M |
| T04 | [SmartEdit additions, TextStats, MarkdownHtml, DocTitle](tasks/T04-markdown-commands.md) | T03 | M |
| T05 | [Editor surface: MarkdownEditText + EditorScrollView + Compose host (with verification gate)](tasks/T05-editor-surface.md) | T02 | L |
| T06 | [Live styling: MdEditable, span classes, SpanFactory, document install](tasks/T06-styling-spans.md) | T03, T05 | L |
| T07 | [Incremental restyle: Restyler, DirtyRange, perf harness, layout-equality test](tasks/T07-incremental-restyle.md) | T06 | L |
| T08 | [Editing behaviours: undo/redo, smart Enter/Backspace/Tab, shortcuts, task toggle](tasks/T08-editing-behaviours.md) | T04, T07 | L |
| T09 | [Selection toolbar pill](tasks/T09-selection-toolbar.md) | T08 | M |
| T10 | [Storage layer: InternalStore, AtomicWriter, TextCodec, RecoveryStore](tasks/T10-storage.md) | T01 | M |
| T11 | [Document session: repository, autosave, EditorViewModel, restore, welcome note](tasks/T11-document-session.md) | T07, T10 | L |
| T12 | [Library drawer UI + file operations](tasks/T12-library-drawer.md) | T11 | L |
| T13 | [Swipe navigation, editor chrome, overflow menu, back handling, wide-screen pane](tasks/T13-swipe-and-chrome.md) | T12 | L |
| T14 | [Linked folders (Storage Access Framework)](tasks/T14-saf-folders.md) | T13 | L |
| T15 | [Focus Mode, typewriter scrolling, stats line](tasks/T15-focus-typewriter-stats.md) | T13 | M |
| T16 | [Preview (WebView) + swipe to preview](tasks/T16-preview.md) | T13 | M |
| T17 | [Find & replace](tasks/T17-find-replace.md) | T13 | M |
| T18 | [Open from other apps, share in/out, export all notes](tasks/T18-intents-share-export.md) | T14 | M |
| T19 | [Settings sheet, runtime style changes, About & licences](tasks/T19-settings.md) | T15, T16, T18 | M |
| T20 | [Hardening: accessibility, large screens, config changes, huge files, StrictMode](tasks/T20-hardening.md) | T17, T19 | L |
| T21 | [Performance validation + baseline profile](tasks/T21-performance.md) | T20 | M |
| T22 | [Final QA and install on the phone](tasks/T22-final-qa.md) | T21 | M |

## Task file format
Every task file has the same sections: **Goal · Depends on · Read first · Scope (in / out) · Files to create/modify ·
Steps · Reference code · Acceptance criteria · Verification commands · Pitfalls · Definition of done**. Acceptance
criteria are checkable (a command, a test, a screenshot with a specific expected property).
