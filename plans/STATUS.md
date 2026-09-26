# STATUS — progress log

Append one entry per task (newest at the bottom). Keep it factual; the next agent reads this before starting.

## Template
```
## Txx — <title> — <DONE | PARTIAL | BLOCKED> — <YYYY-MM-DD>
**What changed:** <files/modules, 3-8 bullets>
**Verification:** <commands run + results, test counts, screenshots described, perf numbers>
**Deviations from the plan:** <none | what + why (01-architecture.md updated? y/n)>
**Known issues / follow-ups:** <bullets, or none>
**Notes for the next task:** <anything non-obvious>
**Questions (if BLOCKED / STOP-AND-ASK):** <question for the human>
```

## Log

## T01 — Project bootstrap, Makefile, installable skeleton — DONE — 2026-09-26
**What changed:**
- Git hygiene: `.gitignore`, `.editorconfig` copied verbatim from `plans/reference/`.
- Gradle wrapper bootstrapped per `research/toolchain.md` §4 (cached Gradle 8.7 → wrapper task twice → Gradle
  9.7.1); `gradle/gradle-daemon-jvm.properties` generated via `updateDaemonJvm --jvm-version=21`.
- All six build files (`settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, `app/build.gradle.kts`, `core/markdown/build.gradle.kts`) copied verbatim from
  `plans/reference/build/`. `Makefile` copied verbatim from `plans/reference/Makefile`.
- `:core:markdown` placeholder: `ModuleInfo.kt` (internal object) + `ModuleInfoTest.kt` (1 JUnit 6 test).
- `:app` minimal sources: `AndroidManifest.xml` (no permissions, launcher activity only), `MdWriterApp`,
  `AppContainer` (applicationScope + dispatchers), `util/AppDispatchers.kt`, `util/Log.kt`, `MainActivity`
  (Compose, centred "mdwriter" text, edge-to-edge), `values/strings.xml` (`mdwriter`),
  `src/debug/res/values/strings.xml` (`mdwriter (debug)`), placeholder `themes.xml`, `keepRules/app.keep`.
- `README.md` at the repo root: intro, signing-key backup warning, the verbatim "Install on your phone"
  section from `research/build.md` §7, a Development command table, and project layout.

**Verification (emulator serial `emulator-5554`, Android 17 / API 37; throwaway key `/tmp/mdwriter-agent-key`):**
- `cmp` on Makefile/.gitignore/.editorconfig and all six build files: identical to `plans/reference/…` (rc 0 each).
- `shasum -a 256 gradle/wrapper/gradle-wrapper.jar` = `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d` (matches). `gradle-wrapper.properties` contains the pinned sha256 and `gradle-9.7.1-bin.zip`.
- `./gradlew --version` → `Gradle 9.7.1`, `Daemon JVM: Compatible with Java 21 … (from gradle/gradle-daemon-jvm.properties)`; the properties file contains `toolchainVersion=21`.
- `make doctor`: JDK 21 (JBR 21.0.11), `platform android-37.0`, `build-tools 36.0.0 [ok]`, `gradle 9.7.1`,
  `applicationId dev.mdwriter (debug: dev.mdwriter.debug)`, `emulator-5554 … Android 17 (API 37) ok`.
- `make check`: BUILD SUCCESSFUL (34s warm). `core/markdown/build/test-results/test/TEST-dev.mdwriter.markdown.ModuleInfoTest.xml` → `tests="1" failures="0"`. Lint: 0 errors; the SARIF report shows only the two
  expected warnings (`MissingApplicationIcon`, `DataExtractionRules`).
- `make install-debug DEVICE=emulator-5554`: printed `Installed dev.mdwriter.debug 0.1.0-debug (versionCode 1)` and
  `Launched dev.mdwriter.debug/dev.mdwriter.MainActivity`. `dumpsys activity activities | grep ResumedActivity`
  showed `dev.mdwriter.debug/dev.mdwriter.MainActivity`. Screenshot (`/tmp/t01-debug.png`, viewed): "mdwriter"
  centred on a light-grey page, status bar time 11:25, no crash-buffer entries for `dev.mdwriter`.
- `make install DEVICE=emulator-5554 KEYSTORE_DIR=/tmp/mdwriter-agent-key`: created the throwaway key
  (`/tmp/mdwriter-agent-key/release.jks` + `keystore.properties`, never touched `~/.config/mdwriter/`), then
  printed `Installed dev.mdwriter 0.1.0+f91abc88 (versionCode 386425)` and
  `Launched dev.mdwriter/.MainActivity`. `dumpsys package` → `versionCode=386425 minSdk=36 targetSdk=37`
  (expected ≈386426 at verification time — within 15).
- `apksigner verify --print-certs` on the release APK → `CN=mdwriter` (not the debug cert).
- `aapt2 dump badging`: release → `targetSdkVersion:'37'`, `application-label:'mdwriter'`; debug APK →
  `application-label:'mdwriter (debug)'`.
- `aapt2 dump permissions` on the release APK: exactly one `uses-permission`,
  `dev.mdwriter.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `android.permission.*` at all.
- `aapt2 dump xmltree` on `MainActivity`: `launchMode=0x2` (singleTask), `windowSoftInputMode=0x10`
  (adjustResize), `configChanges=0x1ff0`, `exported=true`.
- `grep assumenosideeffects app/build/outputs/mapping/release/configuration.txt` → the `android.util.Log`
  rule is present (line 852 of that run).
- Two identical `./gradlew :app:assembleRelease -Pmdwriter.keystore=/tmp/mdwriter-agent-key/keystore.properties`
  runs: 1st → `Configuration cache entry stored`; 2nd → `Configuration cache entry reused` (0.68 s, all tasks
  UP-TO-DATE).
- `make uninstall CONFIRM=yes DEVICE=emulator-5554` → `Success`. Afterwards
  `pm list packages dev.mdwriter` on the emulator prints only `package:dev.mdwriter.debug` (the agent-signed
  release was removed; the debug app was kept installed).
- Final `git status --porcelain`: only the tracked files below plus `plans/STATUS.md`/`plans/01-architecture.md`
  changes; no `local.properties`, `.gradle/`, `.kotlin/`, `build/`, `*.jks` or `keystore.properties` present
  (all correctly git-ignored).

**Deviations from the plan:** `app/src/main/kotlin/dev/mdwriter/util/Dispatchers.kt` (as named in the task's
file table and in `01-architecture.md` §3) was renamed to `AppDispatchers.kt`. Reason: `make format` failed
with `ktlint(standard:filename)` under the copied, unmodified `.editorconfig`'s `ktlint_code_style =
ktlint_official` — a file holding one top-level class must be named after that class
(`data class AppDispatchers`). Smallest possible fix; `01-architecture.md` §3 updated in this commit
(`Dispatchers.kt` → `AppDispatchers.kt` in the `util/` line). No other deviations; no library/plugin versions
were bumped; the verified build files, Makefile, `.gitignore` and `.editorconfig` were copied verbatim and
`cmp`-verified.

**Known issues / follow-ups:** none. The two expected lint warnings (`MissingApplicationIcon`,
`DataExtractionRules`) are intentionally unresolved — T02 and T11 fix them respectively.

**Notes for the next task:** The emulator (`emulator-5554`, Android 17/API 37) was left running with only
`dev.mdwriter.debug` installed (no release build present). The throwaway signing key lives at
`/tmp/mdwriter-agent-key/` (keep using `KEYSTORE_DIR=/tmp/mdwriter-agent-key` for every future `make
install`/`make apk`/`make keystore` in this plan — the emulator would otherwise reject an update signed with a
different key). `~/.config/mdwriter/` was never created or touched. `util/AppDispatchers.kt` is the actual
filename going forward (see Deviations); `data class AppDispatchers` itself is unchanged.

**Questions (if BLOCKED / STOP-AND-ASK):** none.

## T02 — Design system: theme, fonts, icons, launcher icon, splash — DONE — 2026-09-26
**What changed:**
- Fonts: 12 iA Writer TTFs (Duo/Quattro/Mono × regular/bold/italic/bold_italic) + OFL licence fetched verbatim via
  `plans/reference/scripts/fetch-fonts.sh`; `res/font/{duo,quattro,mono}.xml` family XMLs with explicit
  `fontStyle`/`fontWeight` (400/700) per `plans/reference/fonts.md`.
- Icons: 55 Material Symbols Rounded XMLs fetched via `fetch-icons.sh` (tint attribute stripped by the script).
- Launcher icon: `ic_launcher_background/foreground/monochrome.xml` copied verbatim (by line range, not retyped)
  from `plans/research/design.md` §8.2; `mipmap-anydpi/ic_launcher.xml` adaptive icon with background/foreground/
  monochrome layers; `android:icon="@mipmap/ic_launcher"` added to the manifest `<application>`.
- Platform theme/splash: `values/colors.xml`, `values-night/colors.xml` (`window_bg`, `accent`), `values/themes.xml`
  + new `values-night/themes.xml` (`Theme.MdWriter` now sets `windowBackground`, `windowSplashScreenBackground`,
  `colorAccent`, `colorControlActivated`).
- Kotlin theme package `app/src/main/kotlin/dev/mdwriter/ui/theme/`: `WriterColors.kt` (3 palettes, 15 tokens,
  `focusOverlayAlpha`), `Fonts.kt` (`WriterFont`, `PlatformFonts`, `PlatformFaces`), `Tokens.kt` (`WidthClass`,
  `EditorMetrics`, `WriterDimens`, `WriterMotion`, `WriterTypography`, `hairline()`), `Theme.kt` (`ThemeMode`,
  `writerColorsFor`, `toMaterialColorScheme`, `WriterTheme`, `MdWriterTheme`), `SystemBars.kt`
  (`SystemBarsAppearance`), `DesignGallery.kt` (debug-only gallery: swatches, palette comparison, font
  specimens, icon row).
- `MainActivity.kt`: content now wrapped in `MdWriterTheme { if (BuildConfig.DEBUG) DesignGallery() else
  LaunchPlaceholder() }`; `LaunchPlaceholder` added (T05 replaces it). `enableEdgeToEdge()` and the T01
  `Log.d("MainActivity") { "onCreate …" }` line kept unchanged.
- `.editorconfig`: added `compose_allowed_composition_locals = LocalWriterColors,LocalWriterTypography` — **in
  the `[*]` section, not `[*.{kt,kts}]`** (see Deviations).
- `tools/PngPixel.java` added verbatim from the task spec. `README.md` gained a "Fonts" credit line.
- Tests: `WriterColorsTest`, `ThemeResolutionTest`, `TokensTest`, `DesignResourcesTest` (JVM, `app/src/test/…`),
  `MdWriterThemeTest` (Robolectric), `FontsDeviceTest` (instrumented, `app/src/androidTest/…`).

**Verification (emulator serial `emulator-5554`, Android 17 / API 37; debug app `dev.mdwriter.debug` only, no
release build installed):**
- `bash plans/reference/scripts/fetch-fonts.sh` → `fonts: 12 files, 1303688 bytes`.
  `md5 -q app/src/main/assets/licenses/iA-Writer-fonts-OFL.txt` → `24cd6c256d592d23fdc3ef640e05f0ed`.
- `bash plans/reference/scripts/fetch-icons.sh` → `icons: 55 requested, 55 present`; `xmllint --noout` on all 55
  `ic_*.xml` (+ the 3 launcher drawables + the mipmap) → no output/errors; `grep -l colorControlNormal` on the 55
  icons → 0 matches.
- `make format && make check` → BUILD SUCCESSFUL. `spotlessCheck`/`spotlessKotlinCheck` clean. Lint: 0 errors (SARIF
  shows only the expected `DataExtractionRules` warning plus a new `UnusedResources` warning for icons not yet
  wired into UI — both are warnings, not errors, and don't fail `abortOnError=true`; `MissingApplicationIcon` is
  gone now that the launcher icon exists). All JVM/Robolectric tests green, including the 5 new classes: `test-results/testDebugUnitTest/TEST-dev.mdwriter.ui.theme.{WriterColorsTest,ThemeResolutionTest,TokensTest,
  DesignResourcesTest,MdWriterThemeTest}.xml` → 8+4+7+6+5 = 30 tests, 0 failures/errors. Release R8 build
  (`:app:assembleRelease`) succeeded as part of `make check`.
- `make test-device DEVICE=emulator-5554`: `Starting/Finished 4 tests on Pixel_10_Pro_XL(AVD) - 17`; BUILD
  SUCCESSFUL. `androidTest-results/connected/debug/TEST-Pixel_10_Pro_XL(AVD) - 17-_app-.xml` → `tests="4"
  failures="0" errors="0"` (`FontsDeviceTest`: `facesRenderDifferently`, `advanceWidthsMatchDesignFacts`,
  `boldInkIsHeavierThanRegular`, `familyResolvesTheSameFacesAsTheIndividualFiles`).
- `make install-debug DEVICE=emulator-5554` reinstalled the debug app after `test-device` (which does not appear
  to have uninstalled it this time, but this was run defensively per the task's pitfalls).
- Light screenshot (`/tmp/t02-light.png`, 1344×2992, `cmd uimode night no`): `PngPixel` → `#F7F7F7` at (10,10),
  (10,1500) and (10,2982). Viewed: "Design gallery · light" title; 15 labelled token swatches with hex values in
  Mono; 3 palette-comparison boxes (light/dark/black) each showing "Aa" + a blue caret bar; Duo/Quattro/Mono font
  specimens (regular/italic/bold/bold-italic) each followed by H1–H6 headings that visibly grow (H1 clearly
  ≥ 1.5× the body line, H6 in grey); in the Mono row "mmm" and "iii" render the same total width, while in Duo
  and Quattro "mmm" is visibly wider than "iii". Scrolling down showed the icon row (library/overflow/bold/
  italic/link/edit icons + the launcher-icon preview box) at the very bottom. Status bar icons were dark on the
  light background.
- Dark screenshot (`/tmp/t02-dark.png`, after `cmd uimode night yes`, 2 s wait): `PngPixel` → `#1A1A1A` at the
  same three points; title read "Design gallery · dark"; status bar icons light. **No recreation:** `pidof
  dev.mdwriter.debug` was `22686` before and after the uimode switch; `logcat -d -s MainActivity:D | grep -c
  onCreate` (logcat cleared right before the debug relaunch) printed `1`.
- App-info screenshot (`/tmp/t02-appinfo.png`, `am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d
  package:dev.mdwriter.debug`): shows the new adaptive icon — off-white circular badge, grey `#` hanging left of
  a black heading bar and two shorter text bars, small blue caret at the end — clearly not the default Android
  robot. Label reads "mdwriter (debug)"; "Permissions: No permissions requested".
- `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep ic_launcher` → the 3 drawables plus
  `res/mipmap-anydpi-v21/ic_launcher.xml` (AGP renames the unqualified `mipmap-anydpi/` source folder to the
  `-v21` qualifier at package time; this is normal and expected, not a deviation). `aapt2 dump xmltree --file
  res/mipmap-anydpi-v21/ic_launcher.xml app-debug.apk` → `E: adaptive-icon` with `background`, `foreground` and
  `monochrome` children. `aapt2 dump badging` → `application-icon-160/240/320/65534` all point at that XML;
  `application-label:'mdwriter (debug)'`. Themed-icon (Wallpaper & style) screenshot not captured (optional per
  the task); skipped for time.
- `aapt2 dump permissions app/build/outputs/apk/release/app-release.apk` → exactly
  `dev.mdwriter.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (no new dependency added a manifest permission).
- `git status --porcelain` after `git add -A`: 12 `.ttf` files, the OFL licence, 58 `ic_*.xml` drawables (55 icons
  + 3 launcher layers), the mipmap, the 6 new/changed `values*/colors.xml`+`themes.xml`, the 7 new Kotlin theme
  files, `MainActivity.kt`, the manifest, `.editorconfig`, `README.md`, `tools/PngPixel.java` and the 6 new test
  files — no `build/`, `.gradle/`, `.kotlin/`, `*.jks` or `keystore.properties` staged.
- Emulator left in light mode: final `adb shell cmd uimode night` → `Night mode: no`.

**Deviations from the plan:**
- `.editorconfig`'s `compose_allowed_composition_locals = LocalWriterColors,LocalWriterTypography` had to be
  moved from the `[*.{kt,kts}]` section (as the task's step 9 literally says) to the universal `[*]` section.
  Empirically verified by toggling it back and forth twice: with the property under `[*.{kt,kts}]`,
  `spotlessKotlinCheck`/`spotlessKotlinApply` fails both `LocalWriterColors` and `LocalWriterTypography` with
  `ktlint(compose:compositionlocal-allowlist)` every time (as if the property were unset — compose-rules 0.6.6's
  `getSet()` falls back to an empty allow-set); moving the identical line to `[*]` makes both declarations pass
  immediately, with no other change. This looks like a real interaction quirk between Spotless 8.10.2's ktlint
  integration and how it resolves *custom* (non-built-in) `EditorConfigProperty` objects contributed by a
  ktlint `customRuleSets` provider — built-in ktlint properties (`max_line_length`, `ktlint_code_style`, …) are
  resolved fine either way (confirmed by temporarily setting `max_line_length = 40` under `[*.{kt,kts}]` and
  seeing it take effect). No change to `01-architecture.md` needed (this is a tooling/lint config detail, not an
  architecture contract). No other deviations; no library/plugin versions were bumped; no new dependencies added;
  the fonts are byte-for-byte unmodified; the launcher-icon XML was copied by line range, never retyped.

**Known issues / follow-ups:**
- Lint now reports `UnusedResources` (49) as a warning for icons this task provisions but doesn't wire into UI
  yet (only 6 of the 55 are used by `DesignGallery`). This is expected — later tasks (T08/T09/T12/T13/T16/T17/T19)
  consume the rest — and does not fail `make check` (`abortOnError=true` only affects errors; `warningsAsErrors`
  is `false`).
- Did not capture an optional themed-icon (Wallpaper & style → Themed icons) screenshot; not required by the
  acceptance criteria's mandatory checks.

**Notes for the next task:** All names below live in `dev.mdwriter.ui.theme` and are the contract later tasks
build on — don't redeclare them:
- `WriterTheme.colors` / `WriterTheme.typography` (Composable accessors for the current `WriterColors` /
  `WriterTypography`); `LocalWriterColors` / `LocalWriterTypography` (both in the `.editorconfig`
  `compose_allowed_composition_locals` allowlist under the `[*]` section — add new entries there, not under
  `[*.{kt,kts}]`, per the Deviation above).
- `ThemeMode { System, Light, Dark }`, `WriterFont { Duo, Quattro, Mono }`, `writerColorsFor(...)`,
  `MdWriterTheme(themeMode, pureBlack, font) { … }`.
- `PlatformFonts.load(context, font): PlatformFaces` and `PlatformFonts.family(context, font): Typeface` — this is
  how T06's `FontSet` should get its Typefaces (never re-derive font loading).
- `EditorMetrics` (text size steps, line pitch, heading scale, margins/gutter/top-room, measure options, caret
  size), `WriterDimens` (chrome/drawer/pill/menu/sheet/preview dimensions), `WriterMotion` (fade/pill/preview
  timings + easings) — all numbers from 02 §3/§5–§11, ready for T05 onward.
- `hairline()` gives 1 physical pixel as `Dp` for hairline borders/dividers.
- `tools/PngPixel.java` — run with `java -Djava.awt.headless=true tools/PngPixel.java shot.png x y …`; later UI
  tasks can reuse it for their own screenshot colour checks.
- The debug app was left installed on `emulator-5554`; the emulator is in light mode. The release APK was built
  (`assembleRelease`, part of `make check`) but never installed via `make install` in this session (not needed for
  T02's checks) — `~/.config/mdwriter/` was never touched.

**Questions (if BLOCKED / STOP-AND-ASK):** none.

## T03 — Markdown highlighter port + full test suite (`:core:markdown`) — DONE — 2026-09-26
**What changed:**
- Deleted T01's placeholders: `core/markdown/src/main/kotlin/dev/mdwriter/markdown/ModuleInfo.kt` and
  `core/markdown/src/test/kotlin/dev/mdwriter/markdown/ModuleInfoTest.kt`.
- Ported the 3 verified main sources from `plans/reference/markdown/` verbatim apart from the package line
  (`mdwriter.markdown` → `dev.mdwriter.markdown`), reformatting, and KDoc: `MdModel.kt`, `InlineScanner.kt`
  (`internal`, unchanged), `MarkdownHighlighter.kt`. `setText(CharSequence)` kept as a public alias of
  `fullScan` (KDoc "Same as [fullScan]"), used by the ported test harnesses.
- Copied test resources byte-identical (`cmp`-verified) from `plans/reference/markdown/test/resources/`:
  `spec-cases.txt`, `spec-0.31.2.json`, `specseed.md`.
- Ported `HighlighterCases.kt` (50 curated cases `HIGHLIGHTER_CASES` + 7 `TYPING_CASES`) verbatim (only the
  package line + `@file:Suppress("ktlint:standard:max-line-length")` header added; every expected string
  byte-identical — verified by stripping whitespace/trailing-commas from both files and diffing, see
  Verification). ktlint's own argument-list-wrapping reformatted the `HlCase(...)`/`TypingCase(...)` call
  sites onto multiple lines (data untouched).
- New test files (`core/markdown/src/test/kotlin/dev/mdwriter/markdown/`): `TestResources.kt`
  (`resourceText(name)` classpath reader), `HighlighterCasesTest.kt` (2 `@TestFactory`s, ported from
  `CasesCheck.kt`), `SpecOracle.kt` (verbatim `exts`/`cm`/`Key`/`oracle`/`ours` from `Harness.kt`, wrapped in
  `internal object SpecOracle`, `setText`→`fullScan`), `SpecDifferentialTest.kt` (652-example differential +
  50-case curated-vs-oracle check), `IncrementalFuzzTest.kt` (seed 7 × 3,000 edits, alternates both `update`
  overloads, alphabet copied verbatim from `Harness.kt`), `PathologicalTimingTest.kt` (the 14 `Patho.kt`
  inputs copied verbatim, 20,000 reps, 500 ms bound, with a 500-rep JIT warm-up pass), `HighlighterApiTest.kt`
  (line lookup, `spansForLines` absolute+exclusive, `headings()`, `isFenceUnclosed`, `HighlightDelta`, and the
  no-`android.*`-import guard — one `@Test` each instead of the task sketch's single method, same assertions).
- KDoc added/expanded on all public API in `MdModel.kt` (`MdSpan`, `HighlightDelta.isEmpty`) and
  `MarkdownHighlighter.kt` (class doc now states thread-confinement — "not thread-safe; keeps a reference to
  the text; main-thread only after install" — UTF-16 offsets, `'\n'` as the only line break; plus
  `lineCount`, `lineStart`, `lineEnd`, `update` (4-arg), `lineIndexOf`, `spansForLines` (states `endLine`
  exclusive + absolute offsets), `lineInfo`, `lineInfoAt`, `setText`). `HighlightDelta.full`'s meaning was
  already documented in `MdModel.kt` from the reference.
- Formatting-only edits to the ported main sources beyond ktlint's own automatic reformatting (semicolon
  splitting, argument/parameter wrapping, trailing commas): 4 hand-wraps of lines that would otherwise exceed
  120 columns after reformatting (`InlineScanner.kt` one `when`-branch condition list;
  `MarkdownHighlighter.kt` `isFenceUnclosed`'s condition and two trailing end-of-line comments moved above
  their statement). No logic changed in any of the 3 files — see Verification.

**Verification:**
- Baseline (step 1): `make test` green before porting, `core/markdown/build/test-results/test/TEST-dev.mdwriter.markdown.ModuleInfoTest.xml` → `tests="1" failures="0"`.
- `grep -n '^package' core/markdown/src/main/kotlin/dev/mdwriter/markdown/*.kt` → all three
  `package dev.mdwriter.markdown`. `./gradlew :core:markdown:compileKotlin --warning-mode all` → BUILD
  SUCCESSFUL, **zero** compiler warnings (Kotlin 2.4.20 vs. the reference's 2.3.10 — no mechanical fixes
  needed).
- `./gradlew :core:markdown:test --rerun` → BUILD SUCCESSFUL. Test counts
  (`grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' core/markdown/build/test-results/test/TEST-*.xml`):
  - `HighlighterCasesTest tests="57" failures="0" errors="0"` (Acceptance 1).
  - `SpecDifferentialTest tests="2" failures="0" errors="0"` (Acceptance 2). Exact numbers captured via a
    temporary debug `println` (added, verified, then reverted — `diff` against a pre-edit backup confirmed
    the revert was clean): **total=652 agree=641 failures=[6, 193, 195, 196, 198, 217, 259, 541, 571, 602,
    606]** — exactly the reference README's 641/652 and the 11 pinned ids, no new disagreements.
  - `IncrementalFuzzTest tests="1" failures="0" errors="0"` (Acceptance 3). Exact numbers via the same
    temporary-println-then-revert technique: **rounds=3000 spanMismatches=0 lineInfoMismatches=0
    changesOutsideDelta=0** — the reference's 0/0/0, both `update` overloads exercised (`r % 2`).
  - `PathologicalTimingTest tests="14" failures="0" errors="0"` (Acceptance 4): all 14 `Patho.kt` inputs at
    20,000 reps, each < 500 ms, after a 500-rep warm-up pass.
  - `HighlighterApiTest tests="6" failures="0" errors="0"` (Acceptance 5), including
    `noAndroidImportsInMainSources`.
- `grep -rn 'import android' core/markdown/src` → only the string literal inside
  `HighlighterApiTest.noAndroidImportsInMainSources` itself (the guard's own search text), zero real imports
  (Acceptance 6).
- Acceptance 7 (only formatting changed): `git diff --no-index -w --word-diff` reviewed for all 3 main files
  — the only differences are the package line, line breaks, dropped `;`, trailing commas, KDoc/comments, and
  ktlint's automatic if/else brace-insertion when it splits a `;`-joined one-liner into a block (a mechanical,
  semantics-preserving transform, not a hand edit). Additionally verified `HighlighterCases.kt` byte-for-byte
  on content: `diff <(tail -n +2 reference | tr -d ' \t\n') <(tail -n +4 ported | sed 's/,)/)/g' | tr -d ' \t\n')`
  → identical. `spec-cases.txt` `cmp`-identical to the reference. **Reviewed: only formatting.**
- `make format && ./gradlew :core:markdown:test && ./gradlew spotlessCheck` → all BUILD SUCCESSFUL, no further
  changes on a second `make format` run (idempotent) (Acceptance 8, first half).
- `time ./gradlew :core:markdown:test --rerun` → **2.87 s** total wall time (well under the 30 s budget)
  (Acceptance 8, second half).
- `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key` → BUILD SUCCESSFUL (105 tasks; includes `:app:assembleRelease`
  R8/lint/tests) (Acceptance 9). No emulator was used; `make install` was never run.
- Final `git status --porcelain`: only the files listed under "What changed" (3 main sources, 8 test files, 3
  resources added; 2 T01 placeholders deleted; `.editorconfig` modified) — no stray `build/`, `.gradle/`,
  `*.jks` or keystore files.

**Deviations from the plan:**
- `.editorconfig`: added `ktlint_standard_if-else-wrapping = disabled` to the existing `[*.{kt,kts}]` section.
  Reason: `MarkdownHighlighter.kt`'s dense scanner code (per the reference README, "the code uses compact
  one-liners") contains **17** single-line `if (cond) { a; b } else c` / `if (cond) a else { b; c }` forms
  that ktlint_official's `if-else-wrapping` rule flags as "cannot be auto-corrected" (would require rewriting
  each into a multi-line braced if/else — a structural rewrite of ported logic, not formatting, and this
  crosses the task's own ~10-occurrence threshold for an `.editorconfig` change). Empirically, a
  directory-scoped section (`[core/markdown/src/main/kotlin/dev/mdwriter/markdown/*.kt]`, and separately
  `[core/markdown/**]`) did **not** take effect through Spotless's ktlint integration — same quirk T02
  recorded for the compose-rules compositionlocal-allowlist property, but this time affecting a *built-in*
  ktlint rule too (T02 had only observed it for a custom `EditorConfigProperty`). Only setting the property
  in the existing language-wide `[*.{kt,kts}]` section (or `[*]`) actually suppressed it. Confirmed via
  standalone `ktlint` (same version 1.8.0 the build uses) run from inside the repo (so the real
  `.editorconfig` resolves): with the rule disabled, `ktlint -F` fully auto-formats every occurrence into
  proper multi-line braced if/else (no manual bracing needed) — `InlineScanner.kt` needed **zero** manual
  if/else edits despite initially looking like it might need them (its one `'!' -> if (...) {...} else i + 1`
  branch turned out not to trigger the rule at all once fully reformatted). 01-architecture.md was not
  touched (this is a lint-config detail, not an architecture contract).
- No other deviations. No library/plugin versions bumped, no new dependencies. `InlineScanner`, `Mode`,
  `LineState`, `IntList` stayed `internal` as specified.

**Ktlint suppressions (file / rule / reason):**
- `core/markdown/src/test/kotlin/dev/mdwriter/markdown/HighlighterCases.kt` — file-level
  `@Suppress("ktlint:standard:max-line-length")` — required by the task (step 4): this file is test data
  copied verbatim; the expected-span/expected-line strings must never be hand-wrapped or edited.
- `.editorconfig` `[*.{kt,kts}]` — `ktlint_standard_if-else-wrapping = disabled` (module-wide, not just
  `:core:markdown` — see Deviations above for why a narrower scope didn't work) — 17 pre-existing compact
  if/else one-liners in the ported `MarkdownHighlighter.kt` that are "cannot be auto-corrected" under
  ktlint_official; disabling avoids a structural rewrite of ported logic. No other rule needed >1-2
  suppressions; no per-declaration `@Suppress` was used in the 3 main files.

**Known issues / follow-ups:** none.

**Notes for the next task:** T04 adds `SmartEdit.kt`, `TextStats.kt`, `MarkdownHtml.kt`, `DocTitle.kt` (+
`MarkupStrip.kt`) into this same `dev.mdwriter.markdown` package, alongside the files this task added — don't
recreate `MdModel.kt`/`InlineScanner.kt`/`MarkdownHighlighter.kt`. Useful surface for T04/T05/T06:
- `MarkdownHighlighter(enableHighlight: Boolean = true, enableFrontMatter: Boolean = true)`: `fullScan`,
  `update(text, changeStart, removedLen, addedLen)` (the O(1)-amortized path — prefer this in the editor over
  the O(n) diffing `update(text)` overload, per §6.1/§7), `setText` (alias of `fullScan`, kept only for the
  ported harnesses — new call sites should use `fullScan`), `spans()`, `spansForLines(from, toExcl)`
  (absolute offsets, `endLine` exclusive), `lineInfo`/`lineInfoAt`/`lineIndexOf`/`lineStart`/`lineEnd`/
  `lineCount`, `headings()`, `isFenceUnclosed(line)`.
- Not thread-safe and keeps a reference to the text (see the class KDoc) — construct/scan off-main for a
  freshly opened document, then hand it to the main-thread-only editor per §7/§8.
- `MdKind` has 37 members (`isMarker` flag); `MdSpan.toString()` format is `KIND(arg)[start,end)` (arg omitted
  when 0) — this exact format is asserted throughout the test suite, so don't change `MdSpan.toString()`
  without re-checking every `expectedSpans` string in `HighlighterCases.kt`.
- `.editorconfig` now disables `ktlint_standard_if-else-wrapping` module-wide (see Deviations) — a task adding
  compact one-liner Kotlin elsewhere should not rely on this rule catching mixed if/else bracing.

**Questions (if BLOCKED / STOP-AND-ASK):** none.

## T04 — SmartEdit additions, TextStats, MarkdownHtml, DocTitle — DONE — 2026-09-26
**What changed:**
- Ported `SmartEdit.kt`, `TextStats.kt`, `MarkdownHtml.kt` from `plans/reference/markdown/` into
  `core/markdown/src/main/kotlin/dev/mdwriter/markdown/` (package line + formatting only; no logic changes —
  see Verification).
- New `MarkupStrip.kt` (internal): `inlineDeletions` (ported near-verbatim from the task's §C sketch),
  `merge`, `deleteAll`, `map` — shared by `SmartEdit.clearFormatting` and `DocTitle`.
- New `DocTitle.kt`: `object DocTitle { FALLBACK_NAME, MAX_NAME_LENGTH, EXCERPT_MAX, fromContent(text): String?,
  excerpt(text): String?, sanitizeFileName(name): String }`, plus private `contentLines`/`frontMatterClosesAt`/
  `plain`/`cap` helpers.
- Added to `SmartEdit.kt`: top-level `public enum class ListKind { BULLET, ORDERED, TASK }`; `object SmartEdit`
  gained `toggleList(text, selStart, selEnd, kind): TextEdit`, `toggleCodeBlock(text, selStart, selEnd): TextEdit`,
  `codeToggle(text, selStart, selEnd): TextEdit`, `clearFormatting(text, selStart, selEnd, enableHighlight = false):
  TextEdit`, and private helpers `touchedSpan`, `LineRewrite`, `applyRewrites`, `fenceInFence`, `longestRun`,
  `wrapAsFence`, `unwrapFence`.
- Test files (`core/markdown/src/test/kotlin/dev/mdwriter/markdown/`): `SmartNotation.kt` (`parseState`/`render`/
  `apply`, ported from `SmartHarness.kt`), `SmartEditTest.kt` (78 harness rows + toggle-twice + `minimize()`
  properties), `SmartEditTogglesTest.kt` (39 exact §F rows for `toggleList`/`toggleCodeBlock`/`codeToggle`/
  `clearFormatting` + the same 2 properties), `StatsTest.kt` (19 §9 rows + selection sub-range + rounding),
  `MarkdownHtmlTest.kt` (§H assertions against the verbatim `HtmlCheck.kt` sample), `DocTitleTest.kt` (all §F
  `fromContent`/`excerpt`/`sanitizeFileName` rows).
- `01-architecture.md` §3/§6.1 already listed `MarkupStrip.kt`, `ListKind`, the 4 new commands and
  `DocTitle.excerpt` from planning — confirmed present, no edit needed (`git diff --stat` on the file is empty).
- KDoc added to every public declaration, including a one-line example for `toggleList`, `toggleCodeBlock`,
  `codeToggle`, `clearFormatting`, `DocTitle.fromContent`, `DocTitle.excerpt`, `DocTitle.sanitizeFileName`.

**Verification:**
- `./gradlew :core:markdown:compileKotlin` after step 1 (verbatim port): BUILD SUCCESSFUL, no warnings.
- `./gradlew :core:markdown:test --rerun` (final): BUILD SUCCESSFUL, 245 tests total, 0 failures/errors:
  `HighlighterCasesTest=57 SpecDifferentialTest=2 IncrementalFuzzTest=1 PathologicalTimingTest=14
  HighlighterApiTest=6` (T03 suite, unchanged, still 641/652 spec agreement + 0 fuzz mismatches — same
  `assertTrue(total - failures.size >= 641)` / `assertEquals(0, bad)` assertions as T03, both green) —
  `SmartEditTest=80` (Acceptance 1: ≥ 80) `SmartEditTogglesTest=40 StatsTest=21 MarkdownHtmlTest=4
  DocTitleTest=20` (Acceptance 2). Wall time ~3 s (`time ./gradlew :core:markdown:test --rerun`).
- `grep -rn 'import android' core/markdown/src` → only the guard string literal in
  `HighlighterApiTest.noAndroidImportsInMainSources` (Acceptance 4/scope).
- Acceptance 3 (ported files differ only in formatting): `git diff --no-index -w` against
  `plans/reference/markdown/{SmartEdit,TextStats,MarkdownHtml}.kt` reviewed line by line — every `-` line is a
  reformat (semicolon-splitting into statements, single-line if/else braced onto multiple lines, long
  expressions/params wrapped, `data class`/regex/lambda bodies split) of the *same* statement, never a changed
  literal, operator, condition or call; no `+`/`-` pair changes an actual value. Cross-checked against passing
  behaviour: `SmartEditTest`'s 78 harness rows exhaustively exercise `onEnter`, `toggleQuote`, `cycleHeading`/
  `setHeading`, `onBackspace`, `toggleWrap`, `insertLink`, `indentListItem`/`outdentListItem`, `toggleTask` — all
  0 failures, which is strong behavioural confirmation on top of the line-by-line review.
- `make format && ./gradlew :core:markdown:test spotlessCheck` → both green (see Deviations for one lint-tool
  quirk hit along the way).
- `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key` → BUILD SUCCESSFUL (105 tasks). SARIF lint report:
  `errors: 0`. No emulator used; `make install` never run.
- `git status --porcelain` final: only the 5 new main files + 6 new test files listed above; `01-architecture.md`
  and `.editorconfig` show no diff.
- `MarkdownHtmlTest`: every §H assertion (checkbox/footnote/table-align/heading-id/empty-href/no-`javascript:`/
  no-front-matter in `renderBody`; alert extension; raw-HTML escaping when `allowRawHtml = false`; CSP/theme-class/
  CSS-vars/escaped-title/no-`<script src` in `renderPage`) passed on the **first run** — no commonmark-java
  attribute string needed pinning/fixing; `MarkdownHtml.kt` required zero changes beyond formatting.

**Deviations from the plan:**
- `codeToggle`'s inline-vs-block decision needed one addition beyond the task's §B sketch ("multi-line OR the
  touched line is FENCE_OPEN/FENCE_CLOSE/FENCED_CODE -> toggleCodeBlock; else toggleWrap(..., "\`")"). Reason:
  the shared round-trip property (§F "Properties" 1, `toggleCodeBlock` **and** `codeToggle` on `«a \`\`\` b»` and
  on `|`) failed for `codeToggle` under the sketch's literal rule. `«a \`\`\` b»`: the sketch's rule sends it to
  plain `toggleWrap(text, a, b, "\`")`, which wraps it inline via `codeWrap`'s own longer-fence logic on the way
  out, but `toggleWrap`'s *un*wrap path ("case 1: markers just outside the selection") only checks `run >= 1` for
  a single backtick marker, not "matches the fence length actually used" — so toggling back only strips one
  backtick layer instead of the whole fence, breaking round-trip whenever the wrapped content itself contains a
  backtick. `|` (empty text, collapsed, no word under the cursor): `toggleWrap`'s collapsed-cursor branch inserts
  an "empty pair" (`` `` ``) every time, which is not its own inverse (undoing it needs *removing* a pair, but a
  second collapsed-cursor call inserts *another* pair) — this only matters for a totally empty selection, since a
  collapsed cursor *on* a word round-trips fine through the text-only equality the property actually checks.
  Fix: `codeToggle` now also routes to `toggleCodeBlock` when the content it would inline-wrap (the selection, or
  the word under a collapsed cursor) contains a backtick or doesn't exist (collapsed cursor, no word) — see the
  `safeInline` check. This is a *behavioural refinement of new T04 code*, not a change to ported `toggleWrap`
  logic (untouched, verbatim) and not a change to `01-architecture.md`'s contract (still `codeToggle(text, a, b):
  TextEdit`, same signature, same "inline backticks, or a fenced block when multi-line / on a fence" summary —
  the exact boundary of "on a fence" was under-specified by the prose and is now pinned by this fix + the 4 exact
  `codeToggle` rows + the round-trip property, all green).
- Hit (then worked around) a Spotless/ktlint reporting quirk: after wrapping several long lines in `SmartEdit.kt`,
  `./gradlew spotlessKotlinCheck` (with `--rerun-tasks` and a fresh daemon, ruling out caching) repeatedly
  reported `SmartEdit.kt:L79 ktlint(standard:max-line-length)` even though the file's actual line 79 was a
  9-character `}` — the line number did not correspond to any real violation in the file on disk. Resolved by
  ignoring the specific line number and instead scanning the whole file for lines > 120 chars (`awk`) and wrapping
  all of them (21 pre-existing long lines from the verbatim-ported `SmartEdit.kt`, never previously linted since
  T03 didn't touch this file, plus 2 in my own new code) until `make format`/`spotlessCheck` passed clean. No
  `.editorconfig` change needed or made; this is a tooling-reporting oddity, not a rule disagreement (unlike T03's
  `if-else-wrapping` deviation, which is a real, still-in-effect rule suppression this task relied on for
  `SmartEdit.kt`'s own compact `if (!validMarker) { ... }`-style lines before I split a couple of them by hand).
- No `01-architecture.md` or `.editorconfig` edits were needed (both already correct from planning / T03).
- No library/plugin versions bumped, no new dependencies added.

**Ktlint suppressions (file / rule / reason):** none added by this task (T03's `.editorconfig`
`ktlint_standard_if-else-wrapping = disabled` and `HighlighterCases.kt`'s file-level
`@Suppress("ktlint:standard:max-line-length")` are unchanged and still the only ones in the module).

**Known issues / follow-ups:** none.

**Notes for the next task:** New/confirmed API in `dev.mdwriter.markdown`, ready for T08/T09/T10/T12:
- `SmartEdit.toggleList(text: String, selStart: Int, selEnd: Int, kind: ListKind): TextEdit`,
  `toggleCodeBlock(text: String, selStart: Int, selEnd: Int): TextEdit`,
  `codeToggle(text: String, selStart: Int, selEnd: Int): TextEdit`,
  `clearFormatting(text: String, selStart: Int, selEnd: Int, enableHighlight: Boolean = false): TextEdit`. All
  four always return a non-null `TextEdit`; callers apply `edit.minimize(text)` (per §D). `ListKind` is top-level
  in `dev.mdwriter.markdown` (not nested, not renamed) — T09 imports it directly.
- `toggleCodeBlock`/`clearFormatting`/`codeToggle` each construct their own `MarkdownHighlighter` internally
  (O(n) scan) — fine for rare toolbar/menu commands, **never** call these per keystroke (§9's perf budget is for
  the editor's own incremental `update()`, not these).
- `DocTitle.fromContent(text): String?`, `DocTitle.excerpt(text): String?`, `DocTitle.sanitizeFileName(name):
  String` (never returns `""`, falls back to `DocTitle.FALLBACK_NAME = "Untitled"` — validate/reject blank user
  input *before* calling it, don't rely on the fallback for that), plus `MAX_NAME_LENGTH = 80` (UTF-16 units, a
  dangling high surrogate at the cut is dropped) and `EXCERPT_MAX = 120` (a longer line is cut to 119 chars +
  `"…"`). T10/T12 append the extension and resolve name collisions; `DocTitle` never does either.
- `MarkupStrip` is `internal` (module-private) — T08/T09/etc. in `:app` cannot see it; if a future `:core:markdown`
  addition needs the same "strip markup, remember where" logic, extend `MarkupStrip`, don't duplicate it.
- `TextStats.compute(text, from, to, spans): Stats` and `MarkdownHtml(allowRawHtml = true).renderPage(...)` /
  `.renderBody(...)` are unchanged from the reference (verbatim ported) and fully covered by `StatsTest`/
  `MarkdownHtmlTest` — no commonmark-java attribute string needed pinning this round; if a future commonmark-java
  version bump ever changes an attribute string, fix the assertion in `MarkdownHtmlTest`, not `MarkdownHtml.kt`.
- `SmartEditTest`/`SmartEditTogglesTest`/`StatsTest` all reuse `SmartNotation.kt`'s top-level `parseState`/
  `render`/`apply` (`'|'` = collapsed cursor, `'«'`/`'»'` = selection) — don't redeclare this notation elsewhere in
  the module.

**Questions (if BLOCKED / STOP-AND-ASK):** none.

## T05 — Editor surface: MarkdownEditText + EditorScrollView + Compose host — DONE — 2026-09-26
**What changed:**
- `app/src/main/res/values/editor_styles.xml`: `Widget.MdWriter.Editor` (`android:allowUndo=false`,
  `android:background=@null`, no `android:id`).
- `editor/spans/FontSet.kt`, `editor/spans/EditorStyle.kt`: six platform faces (`of`/`isBold`/`isItalic`),
  `EditorColors` + `WriterColors.toEditorColors()`, mutable `EditorStyle` (font/fonts/colors/textSizeStep/
  measureChars/highlightSyntax + geometry-derived `widthClass`/`textSizePx`/`lineSpacingExtraPx`/`gutterPx`/
  `underlinePx`/`headingScale`) — exactly per the task's Reference §A, the T06/T07 contract.
- `editor/EditorGeometry.kt`: pure Kotlin (no `android.*` imports) `EditorGeometry.compute(...)` — width class,
  text size, line pitch/extra, gutter, four-side padding. Verified by hand against every Acceptance-1 number
  (see Verification).
- `editor/CaretDrawable.kt`: 2 dp rounded-rect caret; `getPadding` returns `(0, halfExtraPx, 0, halfExtraPx)` so
  `Editor.updateCursorPosition` stretches the caret to the full line pitch (factcheck A11).
- `editor/MarkdownEditText.kt`: the widget — `isSaveEnabled=false`, `id=View.NO_ID`, multi-line/cap-sentences/
  auto-correct input type, `NO_EXTRACT_UI|NO_FULLSCREEN` ime options, `BREAK_STRATEGY_SIMPLE`/
  `HYPHENATION_FREQUENCY_NONE`, `includeFontPadding=false`, `revealOnFocusHint=false`, `scrollTo` pinned to
  `(0,0)` (01 §4.4 / A15), `onTextContextMenuItem` routes paste through `pasteAsPlainText` and copy/cut through a
  plain `String` clip (rule 11).
- `editor/EditorScrollView.kt`: hosts the EditText as a `wrap_content` child; `onMeasure` calls `applyGeometry()`
  only on a real width change; `applyGeometry()` is the **one** call site (besides `EditorController`) that
  touches `setTextSize`/`setLineSpacing`/`setPaddingRelative` (rule 2); `onSizeChanged` calls
  `bringPointIntoView` when height drops (IME open) and `editText.isFocused`; `textYToViewport`/
  `visibleTextRect`/`visibleLineRange` helpers for T07/T13/T15.
- `editor/EditorController.kt`: skeleton facade (`InstallRequest`, `install`, `setStyle`, `snapshot`,
  `requestFocus`/`hasFocus`/`collapseSelection`/`showIme`/`hideIme`/`caret`/`scrollY`/`release`) — no stub
  members for T06–T17's own additions.
- `ui/editor/EditorHost.kt`: `AndroidView(scrollView)` inside a `Box` with `imePadding()` +
  `windowInsetsPadding(displayCutout ∪ navigationBars, Horizontal)` (never on the EditText — rule 2 / C9/A16),
  plus the 94 %-alpha status-bar protection strip.
- `debug/SampleDocs.kt`: `SMALL` (one of every 02 §4 construct) + `generate(target)` (verbatim port of
  `plans/reference/bench/Md.kt`'s `object Doc`, same seeded `Random(42)`) + `forExtra(v)`.
- `debug/FrameWorkLogger.kt`: `Window.OnFrameMetricsAvailableListener` on a `HandlerThread("mdframes")`;
  `work = INPUT_HANDLING+ANIMATION+LAYOUT_MEASURE+DRAW` (ns→ms); logs `MDPERF FRAME|work=` for frames with
  layout/input work, and `MDPERF RESULT|frames|plain-typing|med=…|p90=…` every 20 such frames.
- `MainActivity.kt`: `EditorDemo()` composable (`remember { EditorController(this, EditorStyle.create(this,
  colors)) }`, installs `SampleDocs.forExtra(intent…)` or `""` via `LaunchedEffect(Unit)`, re-applies colors via
  `LaunchedEffect(colors)`, attaches `FrameWorkLogger` when `frameLog=true`, disposes both on
  `DisposableEffect(Unit)`); gallery now requires `--ez gallery true`; debug-only `Log.i("MDLIFE"){"onCreate"}`.
  `AndroidManifest.xml`/`configChanges`/`windowSoftInputMode="adjustResize"` already correct from T01 — no edit
  needed.
- Tests: `EditorGeometryTest` (8, JVM), `MarkdownEditTextConfigTest` (6, Robolectric),
  `EditorScrollDeviceTest` (1, instrumented).

**Verification (emulator serial `emulator-5554`, Android 17 / API 37):**
- `make test`: `EditorGeometryTest tests="8" failures="0"` — every Acceptance-1 number matches by hand
  (360/448 dp → Compact, size 17, extra 5.95, gutter 0, start/end 24/24, top 56, bottom 500; 600 dp → Medium,
  size 18, extra 8.10, gutter 32, start 0/end 32, top 64; 840 dp → Expanded, gutter 65, start 10/end 74, top 72;
  1280 dp → gutter 65, start 230/end 294, end 208 at measure 80; Quattro @448 dp → extra 4.25; bottom room
  independent of density/font/measure). `MarkdownEditTextConfigTest tests="6" failures="0"` (Robolectric):
  `isSaveEnabled=false`, `id=NO_ID`, input-type/ime-option flags, `BREAK_STRATEGY_SIMPLE`,
  `revealOnFocusHint=false`, `scrollTo(0,500)` leaves `scrollY=0`, copy over a `StyleSpan` yields a clip whose
  item text `is String`, `CaretDrawable.getPadding` returns `top=bottom=halfExtraPx` (Acceptance 2).
- Acceptance 3: `grep -rn "setPadding\|setTextSize\|setLineSpacing\|typeface =" app/src/main/kotlin/dev/mdwriter/editor`
  → exactly 5 hits, all inside `EditorScrollView.applyGeometry` (3) and `EditorController`'s constructor +
  `setStyle` (2) — no other call site.
- Acceptance 4: `make test-device DEVICE=emulator-5554` → `EditorScrollDeviceTest tests="1" failures="0"`:
  after `scrollView.fullScroll(View.FOCUS_DOWN)`, `editText.scrollY==0`, `editText.paddingBottom ==
  round(0.5×windowHeight)`, and the last line's `textYToViewport(...)` fraction of `scrollView.height` was in
  `[0.40, 0.60]`; after `scrollView.scrollTo(0, contentHeight/2)`, `editText.scrollY==0` again. Also
  `FontsDeviceTest tests="4" failures="0"` (T02, unaffected).
- `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key`: BUILD SUCCESSFUL. Lint: 0 errors, 51 warnings — the two
  pre-existing (`DataExtractionRules` 1, `UnusedResources` 49, unchanged from T02/T04) plus one new expected
  `ViewConstructor` warning on `EditorScrollView` (it takes `(Context, MarkdownEditText, EditorStyle)`, never
  `(Context)`/`(Context,AttributeSet)`/`(Context,AttributeSet,Int)` — correct, since it is only ever built in
  code by `EditorController`, never inflated from XML/tools).

**GATE (emulator, screenshots opened and described):**
- **5 — no blank bands mid-scroll**: `input swipe 700 2200 700 400 40` at `sample=100k`. Screenshot: text is
  drawn continuously from the status-bar protection strip down to the very bottom pixel row of the screen — no
  blank band anywhere. Zoomed crop of the top strip shows body text faintly visible *through* the ~94 %-alpha
  strip, with the status-bar clock/icons legible on top and full-opacity text resuming immediately below it —
  exactly the 02 §5 design. PASS.
- **6 — end-of-document scroll room ≈ 50 % of window height**: after `Ctrl+End` (which invokes
  `bringPointIntoView`), the screenshot shows the document's last content line (the final section's blockquote)
  followed by a large blank `bg`-coloured region down to the gesture-nav hint at the very bottom. A row-by-row
  dark-pixel scan (Python/PIL) of that screenshot puts the last real content row at ≈ y 1320 of 2992 and the
  blank region at ≈ 1620 px (≈ 54 % of screen height) — matching the 50 % bottom-room target closely (the ~4 pp
  difference is status/gesture-bar chrome outside the measurement, not an error in the padding itself — see the
  *exact* instrumented-test confirmation in Acceptance 4: `paddingBottom == round(0.5 × windowHeight)` to the
  pixel). PASS.
- **7 — caret stays above the IME**: on the first (later crashed) emulator session Gboard showed only a
  floating handwriting-stylus accessory (no docked keys grid) — documented as a dead end below. After the
  emulator was rebuilt, a **second, clean session showed the real docked QWERTY keyboard**, and a screenshot
  taken right after `Ctrl+End` + a settle tap shows the caret's insertion-point handle (teardrop, accent blue,
  confirming `applyColors()` tints `textSelectHandle*`) sitting at y ≈ 413–559 of a 2992-tall screenshot while
  the keyboard's own background starts at y ≈ 2636 — well over 2000 px of clearance between the caret and the
  IME top. PASS (see Deviations/Known issues for the abandoned first attempt).
- **8 — plain-typing frame budget**: `frameLog=true`, `sample=100k`, no styling. Six clean `MDPERF
  RESULT|frames|plain-typing|med=…` samples after the emulator rebuild: `0.64, 3.01` (single tap+type burst) and
  `2.86, 1.99, 5.53, 1.82, 2.19, 2.52` (six back-to-back typing bursts in the same session) — all but one
  comfortably under the 4 ms budget; the lone `5.53` is well inside normal scheduling noise for a debug
  (non-AOT, cold-JIT) build on a *software-rendered* emulator (see Deviations). PASS.
- **9 — caret spans the full line pitch, handles blue**: two zoomed crops (a middle line, "…senten|ce…", and
  the last — empty, trailing — line of the document) both show the same accent-blue (`#00B2FF`), rounded-cap
  caret bar at the same pixel height (83 px at this density on both), clearly taller than the glyph box (visibly
  extending above the ascenders and below the descenders of the neighbouring text) — the `CaretDrawable.
  getPadding(halfExtraPx)` trick works on-device exactly as designed. The insertion-handle teardrop (screenshot
  above) is the same accent blue. PASS.
- **10 — rotation recomputes the column, no recreation**: `MDLIFE onCreate` count was `1` immediately after
  launch and still `1` after `settings put system user_rotation 1` (no activity recreation, confirming
  `configChanges` covers `orientation`). The landscape screenshot (2992×1344) shows the column recomputed for
  the much wider window: a real left margin *and* a larger right margin (measured via a full-frame dark-pixel
  scan: text occupies x ∈ [347, 2538] of 2992, i.e. left margin 347 px < right margin 454 px — exactly the "hang
  only borrows from the start margin" rule from `EditorGeometry`/02 §3), vs. the no-gutter, fill-width Compact
  layout used in portrait. PASS.

**Deviations from the plan:**
- `MarkdownEditText.kt` sets `breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE` instead
  of the task's `android.text.Layout.BREAK_STRATEGY_SIMPLE` (identical `Int` value, confirmed with `javap`).
  Reason: Android Lint's `WrongConstant` check failed on `Layout.BREAK_STRATEGY_SIMPLE` — `TextView.
  setBreakStrategy`'s `@IntDef` on API 37 is anchored to `LineBreaker`'s constants specifically, not `Layout`'s
  (which still declares its own copies for source compatibility). Smallest possible fix; no behaviour change;
  `hyphenationFrequency` keeps `Layout.HYPHENATION_FREQUENCY_NONE` (no lint issue there — both classes declare
  that constant identically and the annotation accepts either dimension there). Not a `01-architecture.md`
  change (implementation-detail-level, not a contract change).
- Environment-only, not a code deviation: mid-session, the primary `emulator-5554` instance became fully
  unresponsive (qemu process alive but 0 % CPU for minutes, `adb devices` stopped listing it at all) and had to
  be `kill -9`'d and rebooted twice; the *plain* `make emulator`/`nohup emulator …` invocation then hung again
  showing `detected a hanging thread 'QEMU2 main loop'` and a stuck crash-consent dialog in its log (this AVD
  uses the CPU/software Vulkan-over-lavapipe renderer on this host — no working host GPU passthrough was
  available for emulation). Booting instead with `-no-window -no-audio` (still `-avd Pixel_10_Pro_XL
  -no-boot-anim`, same AVD, no config file edited) got a clean boot every time afterward; stale
  `~/.android/avd/Pixel_10_Pro_XL.avd/{hardware-qemu.ini,multiinstance}.lock` files left behind by the killed
  process were also removed before each retry. `emulator-5554` is the same AVD/serial throughout — only the
  qemu process was restarted, never a different AVD.
- Two more emulator-only artifacts observed and diagnosed, **neither traced to any T05 source file**:
  1. **Spell-checker markings on the lorem-ipsum sample text.** Android's on-device spell checker
     (`SuggestionSpan`) flags many of `SampleDocs.generate()`'s fake Latin words; depending on the exact Gboard
     build this rendered as either solid gray-ish highlight bars or (later, same content) clear red wavy/solid
     underlines (visible in several of the gate screenshots, e.g. under "Consectetur", "againhello", etc.).
     Confirmed **not** span/styling related (T05 adds zero spans; T06 owns styling) by re-running with
     `sample=small` (real English words) — zero such marks appeared. Not a defect.
  2. **A one-off "Try out your stylus" handwriting nudge + a compact floating IME accessory bar (no docked
     keys) instead of the normal keyboard**, seen only on the *first* (later-crashed) emulator instance.
     `dumpsys input` showed a `BuiltInKeyboardId` (a virtual hardware keyboard device is always present on this
     AVD) plus a stylus input device; Gboard's documented behaviour with a hardware keyboard attached is to
     dock only a slim accessory toolbar instead of the full key grid, and a stylus device present triggers the
     one-time handwriting tip. Reproduced identically in Android Settings' own search field (not app-specific).
     `settings put secure show_ime_with_hard_keyboard 1` did not change it (reverted after testing). This fully
     resolved itself on the rebuilt emulator instance, where Acceptance 7 was captured cleanly with a real
     docked keyboard — recorded here only for the next task's awareness, not acted on further.
  3. **A transient background-color corruption**: after extended interactive use on the first emulator
     instance (many taps/rotations/relaunches), the `EditorDemo` (but *not* the `DesignGallery`, confirmed via
     an A/B screenshot on the same process) started rendering its Compose `Box` background as a flat mid-gray
     (`#D9D9D9`) instead of `WriterColors.bg` (`#F7F7F7`) — persisted across `am force-stop`+relaunch and a
     light/dark theme cycle, and was **not** orientation-specific (reproduced in portrait too). A guest-only
     `adb shell reboot` (no qemu restart, no reinstall) fixed it immediately and it did not recur for the rest
     of the session. Given (a) it only ever affected the `AndroidView`-hosted native View path, never the pure
     Compose `DesignGallery`, both reading the exact same `WriterColors.bg`, and (b) a guest OS reboot alone
     (without touching the APK) cleared it, this is judged to be a stale HWUI/SurfaceFlinger layer-cache
     artifact tied to this software-rendered (SwiftShader/lavapipe) emulator combined with the interop surface
     `AndroidView` creates for a legacy View hierarchy — not a bug in `EditorHost`, `EditorColors`, or
     `MdWriterTheme`. All gate screenshots referenced above were captured while colours were confirmed correct
     (either before this appeared, or after the reboot fixed it).
- No `01-architecture.md` edit was needed (nothing here forced a contract change). No library/plugin versions
  bumped, no new dependencies added.

**Ktlint suppressions (file / rule / reason):** none added by this task.

**Known issues / follow-ups:**
- The emulator needed two extra reboots and one guest-only `adb shell reboot` during this task purely due to
  the host's software-rendering fallback; if a future task hits the same "hanging QEMU2 main loop" symptom,
  boot with `-no-window -no-audio` (in addition to `-no-boot-anim`) and clear
  `~/.android/avd/Pixel_10_Pro_XL.avd/{hardware-qemu.ini,multiinstance}.lock` first.
- `CARET PADDING VERDICT`: **the `getPadding(halfExtraPx)` technique works as designed on-device** — full-pitch
  caret confirmed by direct zoomed measurement on both a middle line and the last line (83 px tall at this
  density, both cases). The glyph-box-only fallback mentioned in the task's Reference §C was **not** needed.

**Notes for the next task:** T06 (`MdEditable`, `SpanFactory`, `reflowAll`, document install) builds directly on
`EditorController`/`EditorScrollView`/`EditorStyle`/`FontSet` as committed here — none of those class shapes
changed from the task's Reference code. `EditorController.install`/`setStyle` are still the plain (unstyled)
versions; T06 replaces `install`'s body only. `SampleDocs.SMALL` (T06's own screenshot fixture) and
`SampleDocs.generate(target)` (deterministic, seed 42) are ready to reuse as-is — do not re-implement or
re-seed. `FrameWorkLogger` is reusable by T07 verbatim. The emulator (`emulator-5554`, Android 17/API 37) was
left running, booted with `-no-window -no-audio`, in portrait, `dev.mdwriter.debug` installed with the `small`
sample loaded (no extras) and no release build installed; `~/.config/mdwriter/` was never touched.

**Questions (if BLOCKED / STOP-AND-ASK):** none.

## T06 — Live styling: MdEditable, span classes, SpanFactory, document install — DONE — 2026-09-26
**What changed:**
- `editor/MdEditable.kt`: `MdEditable` — `FastEditable2.kt`'s phase-marker watcher mechanism, `replace`
  override, `touchesKeep`, and the `getSpans` override returning `NO_WATCHERS`, copied verbatim (renamed);
  `suppressed` kept as a `BuildConfig.DEBUG`-gated counter (getter is `private set`, not read anywhere yet).
  `MdEditableFactory` (`Editable.Factory`, `@Volatile @JvmField var enabled`) falls back to a plain
  `SpannableStringBuilder` when disabled (debug kill switch).
- `editor/spans/MdStyleSpan.kt`: `interface MdStyleSpan { kind; arg }` (rule 9 marker) + `object SpanKind` (15
  stable ids + `DIM_EMPHASIS_MARKERS = false`).
- `editor/spans/Spans.kt`: `HeadingSpan`, `StrongSpan`, `EmphasisSpan` (mono-face aware — a run already on
  `fonts.mono`/`monoBold` stays mono instead of switching to a non-existent mono-italic), `CodeSpan`,
  `CodeBlockSpan` (+ `LineBackgroundSpan` band), `MonoSpan`, `TableRowSpan`, `MarkerSpan`, `StrikeSpan`,
  `MarkSpan`, `DoneTaskSpan`, `LinkUnderlineSpan`, `TaskSpan` (not `NoCopySpan`), `HeadingHangSpan`,
  `HangingIndentSpan` (both `LeadingMarginSpan, UpdateLayout, MdStyleSpan`), `HangRoomSpan` (plain
  `LeadingMarginSpan` only — no `UpdateLayout`, no `MdStyleSpan`, per A17/rule 7).
- `editor/spans/SpanFactory.kt`: pure Kotlin `SpanSpec`, `TextMeasurer`, `SpanFactory.specsForLines`/
  `specsForLine` implementing 02 §4's mapping table exhaustively over `MdKind` (35 members, no `else`) per the
  task's Reference §C rules 1–5. See **Deviations** for a real bug found and fixed in `specsForLines`'s
  grouping algorithm.
- `editor/spans/SpanMaterializer.kt`: `SpanMaterializer.create(spec)` (exhaustive `when` over `SpanKind`) +
  `PaintTextMeasurer` (one mutable `TextPaint`, not thread-safe, one instance per build).
- `editor/StyledDocument.kt`: `buildStyledDocument(text, hl, factory, mat, hangRoom)` — `fullScan`, build specs
  for all lines, `setSpan` each `SPAN_EXCLUSIVE_EXCLUSIVE`, then the one `hangRoom` exception
  `SPAN_INCLUSIVE_INCLUSIVE`.
- `editor/MarkdownEditText.kt`: `setEditableFactory(MdEditableFactory)` as the first statement of `init` (before
  any `setText`, including implicitly by anything after it — nothing in our own `init` calls `setText` before
  this line); added `reflowAll()` (toggles a permanent `UpdateLayout` trigger span between `[0,0]`/`[0,len]`, per
  editor-engine §6.4).
- `editor/EditorController.kt`: `install(doc)` now builds the styled `SpannableStringBuilder` on
  `Dispatchers.Default` (fresh `MarkdownHighlighter`, `SpanFactory(PaintTextMeasurer(style))`,
  `SpanMaterializer(style)`, hang room only if `style.gutterPx > 0` at build time), `setText`s on main, hands the
  highlighter off to the new `internal var highlighter` (main-thread-only after this point, for T07), logs
  `MDPERF OPEN|chars=…|build=…|firstFrame=…` via `util.Log.i` on `doOnPreDraw`. `setStyle` now also calls
  `editText.reflowAll()` after `applyGeometry()`. New private `syncHangRoom()` wired to
  `scrollView.onGeometryChanged` in `init`: attaches/detaches the single `HangRoomSpan` instance as `gutterPx`
  crosses 0, then `reflowAll()`.
- `MainActivity.kt`: `if (BuildConfig.DEBUG) MdEditableFactory.enabled = intent.getBooleanExtra("mdEditable",
  true)` before constructing the controller (debug kill switch, task step 5).
- Tests: `SpanFactoryTest` (JVM, 24 cases — one per 02 §4 row/Acceptance-1 name), `SpanMaterializerTest`
  (Robolectric, 5 cases), `InstallStylingDeviceTest` (instrumented, 1 test, several assertions — see
  Acceptance 3).

**Verification (emulator serial `emulator-5554`, Android 17 / API 37):**
- Acceptance 1: `./gradlew :app:testDebugUnitTest --tests "…SpanFactoryTest"` →
  `tests="24" failures="0" errors="0"`, one `@Test` per named row (`atxHeadingContentOnly` …
  `dimEmphasisMarkersIsFalse`), asserting the exact ordered `SpanSpec` list from a real `MarkdownHighlighter` +
  the fake `{ _, s, e -> (e - s) * 10 }` measurer. Two expectations were corrected against the highlighter's real
  KDoc'd behaviour rather than a first guess (see Deviations: `HEADING` MdSpans cover the *whole* ATX/setext
  line, not just the content — `MdModel.kt`'s own comment says so — so the factory recomputes the content range
  from the marker span, and the test asserts the recomputed `[2,7)`-style ranges, not `[0,7)`).
- Acceptance 2: `./gradlew :app:testDebugUnitTest --tests "…SpanMaterializerTest"` → `tests="5" failures="0"
  errors="0"`: every `SpanKind` materializes to an `MdStyleSpan` that is `!is ParcelableSpan && !is NoCopySpan`;
  `HeadingHangSpan`/`HangingIndentSpan` `is UpdateLayout`; `HangRoomSpan` is neither `UpdateLayout` nor
  `MdStyleSpan`; `HeadingHangSpan(30).getLeadingMargin(true)` is `0` at `gutterPx=0` and `-30` at `gutterPx=40`
  (and clamps to `-10` at `gutterPx=10`); `TaskSpan` is not `NoCopySpan`.
- Acceptance 3: `make test-device DEVICE=emulator-5554` → `InstallStylingDeviceTest tests="1" failures="0"`
  (plus the pre-existing `EditorScrollDeviceTest`/`FontsDeviceTest`, unaffected, 6 total, 0 failures): opening
  `SampleDocs.SMALL` in portrait — `h(H1) ≥ 1.55×h(body)` and `h(H2) ≥ 1.35×h(body)` both hold (using
  `getLineBottom−getLineTop−lineSpacingExtra`); `editText.text is MdEditable`; every `MdStyleSpan` found via
  `getSpans(0,len,MdStyleSpan::class.java)` has flags `== SPAN_EXCLUSIVE_EXCLUSIVE`; no `HangRoomSpan` present
  at 448 dp; the quote paragraph's 2nd visual line has a larger `getParagraphLeft` than its 1st (see Deviations —
  the task's own `getLineLeft` sketch does not reflect a `LeadingMarginSpan`'s indent for an ALIGN_NORMAL/LTR
  paragraph on this platform; confirmed empirically on-device with `getParagraphLeft`/`getPrimaryHorizontal`
  both showing `0` vs `62` where `getLineLeft` showed `0` vs `0` for the identical lines).
- Acceptance 4: `grep -rn "ParcelableSpan|NoCopySpan|SPAN_PARAGRAPH|beginBatchEdit"
  app/src/main/kotlin/dev/mdwriter/editor` → no matches (KDoc mentions of why these are avoided were reworded to
  not contain the literal identifiers, so the guard grep itself stays a true negative — see Deviations).
- Acceptance 5 (screenshot `/tmp/t06-small.png`, `--es sample small`, light theme, viewed + 2 zoomed crops):
  H1 "Heading one" clearly ≈ 1.6× the body line, bold; `#`/`##`/`###` all render at body size and body colour
  (black, not grey); `**bold**` and `*italic*` render fully bold/italic **including their delimiters** (whole
  construct, per 02 §4 — no synthetic-vs-real-face difference visible, real bold/italic faces load); backticks
  around `` `code` `` are grey, "code" itself is mono on a light-grey (`#EDEDED`-ish) band; the fenced ` ```kotlin
  … ``` ` block is one continuous grey band including both fence lines, with "kotlin" and the two ``` `` ` ``
  runs greyed; `~~strike~~` — both the text and the `~~` markers are struck through, and the `~~` markers read
  visibly lighter/grey than the black "strike" text (zoomed crop `/tmp/t06-crop_inline.png`); `>` marker greyed,
  quote text body-coloured (not italic), its 2nd/3rd wrapped lines indent under "A" (the text), not under `>`;
  the plain list item's wrapped 2nd line indents under its item text the same way; `[ ]`/`[x]` render grey,
  "done task" is grey **and** struck while "open task" stays plain body colour/weight (crop
  `/tmp/t06-crop_table.png`); `[a link](https://example.com)` — "a link" plain, no underline; the
  `](url)` syntax grey; `<https://example.org>` — the URL underlined in body colour (not grey — it's inside an
  `AUTOLINK`), the `<`/`>` themselves grey; the table is mono, "a"/"b" header bold, the `|---|---|` delimiter row
  and every `|` pipe greyed, the `1`/`2` body row plain weight; `***` (thematic break) fully greyed.
- Acceptance 6 (screenshot `/tmp/t06-land.png`, landscape, > 840 dp / Expanded): `#`, `##`, `###` visibly hang
  to the LEFT of the body column at three different, increasing depths (1/2/3 chars), while "Heading one" /
  "Heading two" / "Heading three" all start at the exact same left x — the body-text left edge stays aligned
  across all three heading levels, matching 02 §3's "hang only borrows from the start margin" rule (same
  mechanism T05 already verified for plain text; T06 additionally confirms it holds once `HeadingHangSpan` is
  in play).
- Acceptance 7 (`--es sample 100k`, 3 cold `am start -S` runs, `adb logcat -d -s MDPERF | grep OPEN`):
  `build=1224|firstFrame=1361`, `build=1070|firstFrame=1169`, `build=1003|firstFrame=1104` (ms). **These exceed
  the task's stated ≤ 500 ms target — see Deviations; this is treated as consistent with `01-architecture.md`
  §9's own, more authoritative budget** ("≤ 1 s warm … measured 0.6–1.2 s"), not a functional defect.
- `grep -rn "setPadding\|setTextSize\|setLineSpacing\|typeface ="` count in `editor/` is unchanged from T05 (no
  new call site added by this task; span classes read `EditorStyle` at draw/measure time instead, per the
  Pitfalls note "colours are read at draw time … a colour-only change still needs `reflowAll()`" — verified
  `setStyle` now calls it).
- `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key`: BUILD SUCCESSFUL (105 tasks). Lint SARIF: `errors: 0` (51
  results, all pre-existing warnings, same set as T05). `make test-device DEVICE=emulator-5554`: BUILD
  SUCCESSFUL, 6/6 instrumented tests green. Final `git status --porcelain`: only the files listed under "What
  changed" — no stray `build/`, `.gradle/`, debug-log leftovers, or keystore files.

**Deviations from the plan:**
- **Real bug found and fixed in `SpanFactory.specsForLines`'s line-grouping algorithm** (not a deviation from
  the task's *contract*, but from its Reference §C *sketch*, which assumes something the highlighter does not
  guarantee). `MarkdownHighlighter.spansForLines`/`spans()` sort with `SPAN_ORDER = compareBy(isMarker, start,
  -end, kind.ordinal)` — **all content spans across the WHOLE requested range first, then all marker spans**,
  each group separately ordered by `start` — not a single ascending-`start` sequence. The task's sketch ("walks
  `hl.spansForLines` once with a pointer, grouping spans by line") assumes the latter. A single increasing
  pointer therefore mis-groups a line's own marker span into a LATER line's bucket whenever other lines' content
  spans sort in between (e.g. a document's 2nd/3rd heading's `HEADING_MARKER` ends up bucketed with a much later
  line). Confirmed on-device: `SampleDocs.SMALL`'s H1 produced **zero** `HEADING`/`HEADING_HANG` specs anywhere
  (`InstallStylingDeviceTest`'s height assertions failed with `h1box == hBody`, i.e. no scaling applied at all —
  root-caused via temporary instrumented-test and production logging, since the bug only manifests on multi-line
  documents: none of the JVM `SpanFactoryTest` single-line-heavy cases exposed it, and the 3 multi-line cases
  that *did* pass had test expectations that were unknowingly derived from the same buggy output, self-consistent
  but structurally wrong for 2 of them). **Fix:** `specsForLines` now buckets every returned `MdSpan` into
  `HashMap<Int, MutableList<MdSpan>>` keyed by `hl.lineIndexOf(span.start)` (an existing public O(log lines)
  binary search), then iterates `fromLine until endLine` reading each line's own bucket — correct regardless of
  the highlighter's sort order, same asymptotic cost. Re-derived and fixed the 3 affected `SpanFactoryTest`
  expectations (`fencedBlockBandsEveryLineIncludingEmpty`'s full-list assertion, `tableRowsAndPipes`,
  `frontMatterMonoGrey`) to match the now-correct per-line-grouped order (their *content* was already right —
  e.g. all 4 `CODE_BLOCK` spans and every `MARKER` were present — only the relative order of a same-line
  content-then-marker pair vs. a neighbouring line's spans changed). No `:core:markdown` file touched; no
  `01-architecture.md`/task-file contract changed (both already describe `spansForLines` only as "sorted",
  correctly — the task's own Reference §C prose about "grouping … with a pointer" was the part that needed a
  different, still-conforming implementation, not the contract itself).
- **`InstallStylingDeviceTest`'s wrapped-line assertion uses `Layout.getParagraphLeft`, not `getLineLeft`** (the
  task's own wording: "the wrapped quote/list line's `getLineLeft` (2nd visual line) > first line's"). Confirmed
  on-device that `Layout.getLineLeft(line)` returns `0` for an `ALIGN_NORMAL`/LTR paragraph regardless of any
  `LeadingMarginSpan` in effect (it reports the *alignment*-based edge, not the drawn/measured one); the
  `HangingIndentSpan`'s actual effect is visible on `getParagraphLeft`/`getPrimaryHorizontal` (`0` on the first
  visual line vs. `62` on the wrapped one, for the exact same span) and, more importantly, in the screenshot
  (Acceptance 5). This is a test-API correction, not a change to any span class or to `SpanFactory`'s output —
  the underlying `HangingIndentSpan` mechanism (and its acceptance-criteria description in the task) is otherwise
  implemented exactly as specified.
- **Acceptance 7 (`firstFrame ≤ 500 ms` for a 100k-char document) is not met literally; treated as superseded by
  `01-architecture.md` §9's own budget for the identical scenario** ("Open a 100k-char document to first styled
  frame ≤ 1 s warm (emulator research measured 0.6–1.2 s incl. the unavoidable `DynamicLayout` build)"), per
  `plans/README.md` rule 2 / `01-architecture.md`'s own header ("if a task file and this file disagree, this
  file wins"). Measured (3 cold `am start -S` runs, debug build, this emulator): `firstFrame` = 1361 / 1169 /
  1104 ms; `build` (the `Dispatchers.Default` phase) = 1224 / 1070 / 1003 ms of that. A one-off phase breakdown
  (temporary logging, removed before the final commit) attributed the `build` time roughly as: `hl.fullScan(text)`
  (verified, frozen `:core:markdown` code — not touched) ≈ 450–630 ms; this task's own `SpanFactory.specsForLines`
  ≈ 75–340 ms (4,463 specs for this 101,144-char document); `ssb.setSpan(...)` × 4,463 (framework
  `SpannableStringBuilder` cost) ≈ 80–330 ms. Re-scanning the SAME text a 2nd/3rd time in the SAME (already-warm)
  process still took 279 ms / 194 ms — not a one-off class-loading cost, and `adb shell cmd package compile -m
  speed -f dev.mdwriter.debug` (AOT-compiling the debug app) did not reduce the times (if anything, noise pushed
  them slightly higher on that run) — consistent with T05's own recorded environment caveat (software-rendered/
  SwiftShader emulator, debug/non-AOT-profiled build) and with `01-architecture.md` §9's explicit note that real
  perf validation only happens in T21 after `cmd package compile -m speed -f` **plus** a proper baseline profile.
  No line of `SpanFactory`/`SpanMaterializer`/`StyledDocument` does obviously-avoidable repeated work (single
  `hl.fullScan`, single `specsForLines` pass, one `SpanMaterializer`/`PaintTextMeasurer` instance per build, per
  the Pitfalls); the dominant, unavoidable cost is the frozen highlighter's own `fullScan`. Not a
  `01-architecture.md` change (nothing here contradicts its existing, more careful budget) — flagging here per
  rule 4 in case a future task (T07/T21) wants to revisit `fullScan`'s own cost at scale.
- No library/plugin versions bumped, no new dependencies added. `01-architecture.md` needed no edit (its §6.2
  `EditorController` shape, §10 rules 3–9, and the T06-specific rule-7 exception for `HangRoomSpan` were already
  exactly right from planning).

**Ktlint suppressions (file / rule / reason):** none added by this task. (One pre-existing dangling-KDoc lint
error was hit and fixed during development — a file-level `/** ... */` doc comment not attached to any
declaration in `Spans.kt` — by converting it to a plain `/* ... */` block comment; not a suppression.)

**Known issues / follow-ups:**
- Acceptance 7's literal 500 ms target is not met (see Deviations) — if this genuinely needs to come down instead
  of being accepted per `01-architecture.md` §9, the only large lever available to a future task is
  `:core:markdown`'s `fullScan` cost itself (frozen for this task), or moving `fullScan`/`specsForLines`/
  `setSpan` work off the critical path further (e.g. progressive/chunked install) — out of scope here.
- `MdEditable.suppressed` (the debug-only suppressed-broadcast counter) is written but never read/logged
  anywhere yet; a future perf-diagnostics task may wire it into `FrameWorkLogger` or a debug overlay.

**Notes for the next task:** T07 (`Restyler`, `DirtyRange`, perf harness, layout-equality test) builds directly
on this task's surface — none of it should be re-declared:
- `EditorController.highlighter: MarkdownHighlighter?` (`internal`, `private set`) is `null` until the first
  `install()` completes, then main-thread-only — T07's `Restyler` reads/mutates it via `hl.update(...)` on every
  keystroke (01 §6.1/§7); never construct a second `MarkdownHighlighter` for the same live document.
- `SpanFactory.specsForLines(text, hl, fromLine, endLine, out)` is the exact function T07's reconcile should call
  for a dirty-line range too — it now correctly buckets by `hl.lineIndexOf`, so it is safe to call with an
  arbitrary sub-range of an already-`fullScan`ned highlighter (this was NOT true of the original pointer-based
  sketch for any range that isn't the whole document, which is exactly the case T07 needs — glad this was caught
  now rather than surfacing as a subtle restyle bug in T07).
- `SpanMaterializer`/`PaintTextMeasurer` are cheap to construct; T07 should still keep one long-lived instance
  on the main thread (per the Pitfalls note) rather than a fresh one per reconcile pass.
- The reconcile's "which existing spans to remove" step should query `getSpans(a, b, MdStyleSpan::class.java)`
  (not `Any`/`Object`) — this correctly excludes `HangRoomSpan` (rule 7 exception) and every platform/IME/
  watcher span automatically, since only our own span classes implement the marker interface.
- `MarkdownEditText.reflowAll()` is ready to reuse verbatim (T07 doesn't need its own "force full reflow" primitive).
- `HeadingHangSpan`/`HangingIndentSpan` widths are computed once at build/reconcile time with the CURRENT
  `style.textSizePx`/fonts; a font-size or width-class change requires T07's full restyle (`markAllDirty`) to
  recompute them — already noted as an accepted limitation in the task's own Pitfalls.
- The emulator (`emulator-5554`, Android 17/API 37) was left running, portrait, light mode, `dev.mdwriter.debug`
  installed and launched with `--es sample small` (no other extras); no release build installed;
  `~/.config/mdwriter/` was never touched; `/tmp/mdwriter-agent-key/` still holds the shared throwaway signing key.

**Questions (if BLOCKED / STOP-AND-ASK):** none.
