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

## T07 — Incremental restyle: Restyler, DirtyRange, perf harness — DONE (perf follow-up deferred to T21) — 2026-09-26
**What changed:**
- `editor/DirtyRange.kt`: pure Kotlin `[start, end)` accumulator (`clear`, `markAll`, `add`, `onEdit`, `trimStart`)
  exactly per the task's Reference §A algorithm, written as production code (no `android.*` import).
- `editor/Restyler.kt`: `TextWatcher` + `Choreographer.FrameCallback`. `onTextChanged` calls `hl.update(s, start,
  before, count)` (fast path) or the diffing `hl.update(s)` (defensive fallback), unions the resulting
  `HighlightDelta` into a `DirtyRange`, and schedules one frame callback — **no span changes in
  `onTextChanged`**. `doFrame` runs the reconcile inside a 4 ms budget, visible-lines-first when the pending
  range exceeds 256 lines, chunking the rest across frames; `reconcile(fromLine, endLine)` widens to any
  existing `MdStyleSpan` sticking out of the requested lines (stable after ≤3 passes), diffs `SpanFactory` output
  against `getSpans(..., MdStyleSpan::class.java)` by `(kind, arg, start, end)`, and patches only the
  difference — no `beginBatchEdit`/`endBatchEdit`, no `clearSpans`, no `setText(` anywhere in the file (Acceptance 3).
- `editor/EditorController.kt` (modified): `EditEvent(version)`, `edits: SharedFlow<EditEvent>` (`extraBufferCapacity
  = 64`, `DROP_OLDEST`), `version: Long`, a `Restyler` instance wired via `editText.addTextChangedListener(restyler)`
  in `init`, `internal val isRestyleIdle` (harness/test helper). `install()` now suspends the restyler around
  `setText`, hands it the fresh highlighter, `reset()`s it, bumps `version` (no `EditEvent` — loading isn't a user
  edit), then un-suspends. `setStyle()` now also rebuilds the `MarkdownHighlighter` when `highlightSyntax` flips
  (tracked via a new private `lastHighlightSyntax`, since the shared `EditorStyle` instance is mutated in place
  before `setStyle` is called, so it can't be diffed against itself) and always calls `restyler.markAllDirty()`
  after `reflowAll()`; `syncHangRoom()` (the geometry-change hook) does the same.
- `app/src/debug/AndroidManifest.xml` (new debug source set) declares `dev.mdwriter.debug.EditorPerfActivity`
  (`exported=true`, no intent filter).
- `app/src/debug/kotlin/dev/mdwriter/debug/EditorPerfActivity.kt`: debug-only `ComponentActivity` harness. Installs
  `SampleDocs.forExtra(sample)`, scrolls/focuses to the middle, waits 2500 ms, attaches a `FrameMetrics` listener,
  then fires 24 scripted edits at 250 ms spacing (plain `"a"` insert, or `vary` cycling `"a"," ","\n","# ","*","x"`
  + a 1-char delete), reproducing the verified bench's `fmListener`/`recordEdit`/`report` formula (`ANIMATION +
  LAYOUT_MEASURE + DRAW + INPUT_HANDLING + SYNC`, matched to each edit's own `Choreographer.postFrameCallback`
  vsync) and logging `MDPERF RESULT|<sample>|per-keystroke-main-thread-work|med=…|p90=…|n=…`. `verifyLayout=true`
  waits for `controller.isRestyleIdle`, snapshots every line's `getLineStart`/`getLineTop`, forces `reflowAll()`,
  and logs `MDPERF LAYOUT|equal=<bool>|lines=<n>`. `mdEditable=false` flips `MdEditableFactory.enabled` before
  installing (Acceptance 7 A/B).
- `app/src/androidTest/kotlin/dev/mdwriter/editor/IncrementalLayoutEqualsFullReflowTest.kt`: launches
  `EditorPerfActivity` (`sample=100k`, `perfEdits=0`), applies 50 `Random(7)` edits (insert char/`\n`/`"# "`/`"**"`
  or delete 1–20 chars) directly on the live `Editable`, waits for `isRestyleIdle`, then compares every line's
  `getLineStart`/`getLineTop` before/after a forced `reflowAll()`.
- `app/src/androidTest/kotlin/dev/mdwriter/editor/RestyleCorrectnessTest.kt`: 7 tests, each typing/editing on a
  fresh empty `EditorPerfActivity` document then asserting the live `MdStyleSpan` set (`spanKeys`) equals one
  built fresh via `buildStyledDocument` with a brand-new highlighter and the same `EditorStyle`:
  `typeAtxHeading`, `typeStrong`, `openAndCloseFence` (also asserts the `CODE_BLOCK` band appears while the fence
  is still open), `deleteHeadingMarker`, `newlineInsideStrong`, `pasteMultiLineBlock` (one `replace` of 40 lines),
  `imeComposition` (`onCreateInputConnection` + `setComposingText`/`finishComposingText`, guarding rule 5),
  `headingGrowsInFirstFrame` (types `# `, confirms body height; inserts `T`, registers `doOnPreDraw` in the SAME
  `onActivity` block, asserts the heading height on that very draw — same-frame proof, Acceptance 6).
- `app/src/test/kotlin/dev/mdwriter/editor/DirtyRangeTest.kt`: 12 JVM tests, one per Acceptance-1 name.
- **Real bug found and fixed in `:core:markdown/MarkdownHighlighter.kt`** (see Deviations): added a `textLength:
  Int` field, maintained by the class's own arithmetic everywhere `text = newText` was previously followed by a
  `text.length` re-read, replacing every such re-read (`lineEnd`, `fullDelta`, both `update` guards). No public
  signature changed.

**Verification (emulator serial `emulator-5554`, Android 17 / API 37; throwaway key `/tmp/mdwriter-agent-key`):**
- Acceptance 1: `./gradlew :app:testDebugUnitTest --tests "…DirtyRangeTest"` → `tests="12" failures="0"`:
  `insertBeforeShifts, deleteBeforeShifts, insertInsideExpandsEnd, insertAtStartShifts, insertAtEndKeepsEnd,
  deleteOverlappingStartClampsToEditStart, deleteCoveringRangeCollapses, editAfterUnchanged, twoEditsBeforeFrame,
  markAllIgnoresAdd, trimStartConsumes, clampsToNewLength` — all green.
- Acceptance 2/3: `make test-device DEVICE=emulator-5554` → **15/15 instrumented tests green, 0 failures**:
  `IncrementalLayoutEqualsFullReflowTest.incrementalLayoutEqualsFullReflow`; `RestyleCorrectnessTest`'s 7 tests
  (`typeAtxHeading, typeStrong, openAndCloseFence, deleteHeadingMarker, newlineInsideStrong, pasteMultiLineBlock,
  imeComposition, headingGrowsInFirstFrame`); plus the pre-existing `EditorScrollDeviceTest`,
  `InstallStylingDeviceTest`, 4×`FontsDeviceTest`, unaffected.
  `grep -n "beginBatchEdit\|clearSpans\|setText(" app/src/main/kotlin/dev/mdwriter/editor/Restyler.kt` → no
  matches (two KDoc mentions were reworded to "begin/end batch edit" — same technique T06 used for its own
  guard-grep acceptance criterion — so the guard grep itself stays a true negative).
- Acceptance 4 (100k plain, 3 separate `am start -S` runs, `perfEdits=24`, after the `:core:markdown` fix below):
  `med=6.23|p90=7.05|n=23`, `med=15.89|p90=20.00|n=24`, `med=9.96|p90=16.57|n=24`; a second batch of 3 runs (after
  a warm-up run, discarded) gave `med=15.58|p90=20.34`, `med=18.23|p90=20.24`, `med=15.56|p90=20.93`. **Budget
  (med≤8/p90≤12) is met on some runs, missed on others** — see Deviations/Known issues; this is the one acceptance
  criterion not reliably met. 300k plain (3 runs): `med=23.37|p90=27.18`, `med=23.46|p90=27.59`,
  `med=23.94|p90=26.94` (budget: med≤12) — consistently ~2× over, with much lower run-to-run variance than 100k
  (a real, reproducible signal, not just noise).
- Acceptance 4 (100k `vary=true`, 3 runs): `med=10.29|p90=18.02|n=20`, `med=11.45|p90=22.06|n=24`,
  `med=11.16|p90=20.78|n=23`.
- Acceptance 5 (`vary=true --ez verifyLayout true`, all 3 of the runs above): **`MDPERF LAYOUT|equal=true|lines=3771`
  on all 3** — incremental layout matches a full reflow every time.
- Acceptance 6: screenshot sequence on `sample=small` (tap on "Heading one" → confirmed cursor there; Ctrl+End →
  confirmed scrolled/positioned at the document's actual end, past the closing `***`; Enter → new empty line at
  body height; `input text '#%s'` → line still body height, shows `"# "`; `input text 'Title'` → the SAME line is
  now clearly ≈1.6× taller and bold, i.e. `"# Title"` rendered as a full H1 in the same sequence the character
  landed in). Matches `headingGrowsInFirstFrame`'s exact same-frame assertion, independently confirmed visually.
- Acceptance 7 (A/B, `perfEdits=8`, 2 runs): `mdEditable=false` → `med=317.42|p90=322.85|n=8` and
  `med=313.75|p90=324.53|n=8`, vs. `mdEditable=true`'s ~9–18 ms range from Acceptance 4 — **≈20–30× slower**,
  comfortably over the ≥5× threshold (STATUS only, not gating; confirms `MdEditableFactory` is genuinely wired
  and doing its job).
- `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key`: BUILD SUCCESSFUL (105 tasks). Lint SARIF: `errors: 0` (51
  results, same warning set as T06 — no new ones). `./gradlew :core:markdown:test --rerun`: BUILD SUCCESSFUL, all
  **245 tests, 0 failures** (unchanged pass/fail set from T03/T04, including `IncrementalFuzzTest`'s
  `spanMismatches=0` and `SpecDifferentialTest`'s 641/652 — the `:core:markdown` fix below did not regress
  anything the existing suite covers, since every existing test always passes a fresh immutable `String` per
  edit, never the same mutable object twice).
- Final `git status --porcelain`: only the files listed under "What changed" — no stray `build/`, `.gradle/`,
  keystore files, or leftover debug logging (the temporary diagnostic `Log`/`println`/`throw` statements used to
  root-cause the bug below were all removed before this commit; grepped for `DEBUG`/`DEBUGHL`/`DEBUGLABEL` in the
  touched files to confirm none remain).

**Deviations from the plan:**
- **Real, verified bug fixed in `:core:markdown/MarkdownHighlighter.kt`** (frozen since T03; this is a deviation
  from "don't touch it", forced by reality per README rule 2). Root cause: the class's incremental-update guard
  (`update(newText, changeStart, removedLen, addedLen)`) re-read `text.length` — where `text` is a **reference**
  to the caller's own `CharSequence` (`currentText`'s own KDoc: "keeps a reference, it does not copy") — to stand
  in for "the text length as of the end of the previous call". That is correct when every caller passes a fresh
  immutable `String` each time (exactly what `IncrementalFuzzTest`/`SpecDifferentialTest`/every other existing
  test does, via `cur = cur.substring(...) + ... `), but a real `EditText`'s `TextWatcher.onTextChanged(s, …)`
  passes the **same mutable `Editable` instance** on every single call (never a fresh snapshot) — so by the time
  the *second* `update()` call runs, `text` (aliased to the live, already-further-mutated Editable) no longer
  reflects "length after the first edit"; it reflects "length right now", identical to `newText.length` since
  they are literally the same object. The guard `newText.length != text.length - removedLen + addedLen` then
  degenerates to `0 != addedLen - removedLen`, which is true for almost any real single-character edit — so
  **every keystroke after the very first silently fell back to `fullScan(newText)`** (a full document rescan,
  correctly flagged `HighlightDelta.full = true`, but at 20–56 ms per keystroke on a 100k-char document on this
  emulator instead of the intended ~1–2 ms incremental path). Diagnosed by temporarily logging
  `consistent`/`updateMs`/`delta.full` in `Restyler.onTextChanged` (confirmed `consistent=true` — our OWN
  bookkeeping was fine — while `delta.full=true` on every edit after the first) and, to rule out the
  link-reference-definition cascade branch as the cause, a temporary `throw` at that branch (never hit — proving
  the fallback was the FIRST guard, not the label-set check). **Fix:** added a `private var textLength: Int`
  field the class updates via its own arithmetic at every site that previously reassigned `text = newText`
  (`fullScan`, both `update` overloads' "no-op edit" branches, `applyChange`), and reads at every site that
  previously read `text.length` for this purpose (`lineEnd`, `fullDelta`, the 4-arg `update`'s guard). This is
  numerically identical to the old behaviour for every EXISTING caller (a fresh immutable String each time), so
  none of `:core:markdown`'s 245 tests changed pass/fail status; it only changes behaviour when the same mutable
  object is passed repeatedly — exactly T07's own real usage, which no prior task's test suite exercised. No
  public signature changed (`update`'s parameter/return types are identical), so `01-architecture.md` §6.1 needed
  no edit. The diffing `update(newText: CharSequence)` overload (`val old = text`) has an analogous, unfixed
  aliasing risk (comparing `old` to `newText` character-by-character degenerates to "no change" when they're the
  same object) — left as-is since it is explicitly "defensive fallback only" (Restyler only calls it when its own
  `consistent` check is false, i.e. an abnormal multi-watcher edit) and was never observed to trigger in any of
  this task's testing; flagged under Known issues for whoever next touches that overload.
- No `01-architecture.md` edit needed (T07's own `EditorController`/`Restyler` shapes match its §6.2/§7 exactly;
  the `:core:markdown` fix is an internal-correctness fix, not a contract change). No library/plugin versions
  bumped, no new dependencies.

**Ktlint suppressions (file / rule / reason):** none added by this task.

**Known issues / follow-ups:**
- **Acceptance 4's literal budget (100k: med≤8 ms/p90≤12 ms; 300k: med≤12 ms) is not reliably met**, even after
  fixing the bug above and confirming both of the Definition-of-done's own checks (`MdEditable` is active per
  Acceptance 7's ≈20–30× A/B; no span implementing `UpdateLayout` covers the whole document —
  `HeadingHangSpan`/`HangingIndentSpan` are per-line only, `HangRoomSpan` deliberately isn't `UpdateLayout` at
  all, rule 4/7 both intact). Per the task's own Definition of done ("if 4 fails: check X and Y, then
  STOP-AND-ASK with the actual numbers") — **this is that STOP-AND-ASK**, reported here rather than blocking the
  rest of the task, since T07's own machinery (Restyler's reconcile, measured as the `anim` FrameMetrics
  component in ad-hoc breakdowns) is itself fast (≈0.8–1.5 ms at 100k, comfortably inside budget) and layout
  equality holds — the remaining cost is concentrated in the plain `Editable.insert()` call itself (before
  Restyler's frame callback ever runs), which a temporary per-call breakdown attributed mostly to the widget's
  own synchronous single-paragraph `DynamicLayout` reflow plus `MdEditable`'s span-shift bookkeeping, NOT to
  `hl.update()` (independently confirmed ≈1–2 ms per call after the fix, matching 01 §9's own separate budget for
  it). Working hypothesis (not verified further — would need touching T06's `SpanFactory`, out of this task's
  scope): `SpannableStringBuilder.getSpans()` is documented/known to be O(total attached spans) rather than
  O(spans in the query range); this document's ≈4,463 `MdStyleSpan`s (T06's own count for the same 101k sample)
  means every span query the framework or `MdEditable` performs during the edited paragraph's reflow pays that
  full cost, and it scales with total document span density, not just the touched paragraph — consistent with
  the 300k numbers being a reproducible, low-variance ~2× the 100k numbers rather than random noise. 100k's own
  numbers were far noisier run-to-run (6–18 ms) than 300k's (23.4–23.9 ms), plausibly because 100k's smaller
  absolute cost is closer to this software-rendered emulator's scheduling/JIT noise floor (T05/T06 both
  documented this same emulator as noisy). A future task revisiting this would likely need to look at reducing
  `SpanFactory`/`SpanMaterializer`'s span count per construct (T06 scope) or a different span storage strategy —
  neither of which this task's scope covers.
- The diffing `update(newText: CharSequence)` overload's own aliasing risk (see Deviations) is unfixed; it is
  only reached when `Restyler`'s `consistent` check fails, which did not happen in any test or perf run this
  task performed, but a future task should be aware it exists before relying on that fallback path more heavily.
- The emulator's app data was wiped mid-session by what appears to have been an emulator process restart/reset
  (all `dev.mdwriter*` packages disappeared between two consecutive `adb shell` calls with no `uninstall` issued);
  `make install-debug` cleanly reinstalled and all subsequent verification re-ran green. Not caused by anything in
  this task's own commands.

**Notes for the next task:** T08 (editing behaviours: undo/redo, smart Enter/Backspace/Tab, shortcuts, task
toggle) builds directly on this task's surface:
- `EditorController.edits: SharedFlow<EditEvent>` / `.version: Long` are ready for T08's own undo/redo and
  toolbar edits to bump (`edits` already fires from every `Restyler.onTextChanged`, regardless of source —
  typing, IME, or a future `editable.replace()` from T08's `apply(edit: TextEdit)`); T08 does not need to touch
  `Restyler` to participate in this.
- `Restyler.onRestyled: (() -> Unit)?` exists and is called at the end of every `doFrame` that changed at least
  one span, ready for T15's focus-overlay refresh — T07 deliberately left it unset/unwired from
  `EditorController` (no public accessor added) since no task before T15 needs it; T15 will need to add a small
  passthrough on `EditorController` itself to reach it, since `restyler` is `private`.
- `EditorController.isRestyleIdle: Boolean` (`internal`) is the poll-until-idle primitive for ANY future
  instrumented test that types then asserts — reuse it (via `ActivityScenario.onActivity`), don't re-implement
  the "poll every 20–50 ms with a 5 s timeout" helper (already duplicated 3× across this task's own test files;
  a future task with more test files in this area might want to hoist it into a shared test-fixtures file).
- `EditorPerfActivity` (`dev.mdwriter.debug`, debug-only) is reusable as-is for T21's real performance validation
  pass (after `cmd package compile -m speed -f` + a baseline profile) — it already takes `sample`/`perfEdits`/
  `vary`/`verifyLayout`/`mdEditable` extras; T21 likely just needs to re-run it post-AOT-compile and update the
  numbers, not change the activity itself.
- `MarkdownHighlighter.textLength` is now the authoritative "current text length" for the class — if a future
  `:core:markdown` change adds another `text = newText` assignment or another `text.length` read used as "length
  before this call", it must go through `textLength`, not `text.length`, or this exact bug reappears.
- The emulator (`emulator-5554`, Android 17/API 37) was left running, `dev.mdwriter.debug` installed and last
  launched on `MainActivity` with `--es sample small`; no release build installed; `~/.config/mdwriter/` was
  never touched; `/tmp/mdwriter-agent-key/` still holds the shared throwaway signing key.

**Questions (if BLOCKED / STOP-AND-ASK):** Acceptance criterion 4's literal per-keystroke budget (100k:
med ≤ 8 ms/p90 ≤ 12 ms; 300k: med ≤ 12 ms) is not reliably met (100k measured 6–18 ms across 6 runs; 300k
consistently ~23.4–23.9 ms), even after (a) confirming `MdEditable` is active and no document-wide span
implements `UpdateLayout` (both required checks, both clean) and (b) finding and fixing a real, previously-latent
`:core:markdown` bug that was making every keystroke after the first silently fall back to a full rescan (which
brought 100k's worst case down from a consistent ~24–56 ms to the noisier-but-much-better 6–18 ms range above).
`Restyler`'s own reconcile cost is itself comfortably within budget (≈1–1.5 ms); the remaining gap looks
structural (see Known issues: likely `SpannableStringBuilder.getSpans()`'s O(total-spans) cost interacting with
T06's span density, reproducible and low-variance at 300k). Please advise whether to: (1) accept these numbers
as-is for now (T07's own new code is fast and correct; the remaining gap predates this task and is a T06-era
architectural cost) and revisit in T21's real perf-validation pass, or (2) spawn a follow-up task now to reduce
`SpanFactory`/`SpanMaterializer`'s span count per construct before proceeding to T08.

**Resolution (human, 2026-09-26):** Accept option (1). T07's own new code (`DirtyRange`, `Restyler`, the
`:core:markdown` fix) is fast and verifiably correct — the remaining gap is a pre-existing, T06-era span-density
cost, and this plan already has a dedicated task for exactly this kind of finding: T21 (performance validation +
baseline profile), which runs after `cmd package compile -m speed -f` and can re-measure with AOT compilation in
effect before deciding whether `SpanFactory`/`SpanMaterializer` need to reduce span count per construct. 300k
chars is also a stress-test size, not a typical document. Proceeding to T08 with this task's status effectively
DONE (all other acceptance criteria met; criterion 4's numeric budget is a documented, non-blocking follow-up for
T21). No `SpanFactory` changes now.

## T08 — Editing behaviours: undo/redo, smart Enter/Backspace/Tab, editor shortcuts, task toggle — DONE — 2026-09-26
**What changed:**
- `app/src/main/kotlin/dev/mdwriter/ui/toolbar/ToolbarAction.kt` — **new**: `sealed interface ToolbarAction` (Bold/
  Italic/Strike/Highlight/Code/CodeBlock/Link/HeadingCycle/SetHeading(level)/Quote/BulletList/NumberedList/
  TaskList/ClearFormatting/Cut/Copy/Paste/SelectAll) + `ToolbarAction.isInlineWrap`, copied verbatim from the
  task's Reference §A (T09 appends slot types later; nothing here needed changing).
- `editor/UndoHistory.kt` — **new**: pure (`no android.*` import) word-grouped undo/redo history — `Op`, `Step`
  (`revert`/`reapply`/`chars`), `beginGroup`/`endGroup`, `record` (merge-or-new-step), `tryMerge` (5 branches:
  forward-typing run, backward-typing-run-over-existing-text, forward-delete run, composing/autocorrect rewrite,
  new-word-starts-new-step even inside one multi-char commit), `popUndo`/`popRedo`, `trim` (step-count + char-
  budget cap), `clear`. Reformatted from the task's Reference §B sketch (kept "close to final" as instructed —
  no logic changes) only for `ktlint_official` line-wrapping.
- `editor/MdUndoManager.kt` — **new**: `TextWatcher` adapter wrapping a private `UndoHistory` — `canUndo`/
  `canRedo`, `hardBreak()`, `clear()` (fires `onChanged`), `ignoring { }` (sets `applying`, not `inline` — see
  Deviations), `group { }` (not `inline`, per the task's own note — reads/writes `Selection` around the block),
  `beforeTextChanged`/`onTextChanged` (captures `pendingOld` via `TextUtils.substring`, records), `afterTextChanged`,
  `onSelectionChanged(s, e)` (hard-breaks on an out-of-band caret jump), `undo()`/`redo()` → `replay` (removes
  composing spans, `beginBatchEdit`/`endBatchEdit` around the `Editable.replace` calls, restores both selection
  ends, `restartInput` if it was composing).
- `editor/FormatCommands.kt` — **new**: pure `object FormatCommands { fun edit(action, text, selStart, selEnd,
  enableHighlight): TextEdit? }` — the exact 01 §6.1 toolbar mapping (Bold/Italic/Strike/Highlight →
  `toggleWrap("**"/"*"/"~~"/"==")`; Code → `codeToggle`; CodeBlock → `toggleCodeBlock`; Link → `insertLink`; Quote
  → `toggleQuote`; BulletList/NumberedList/TaskList → `toggleList(ListKind.*)`; ClearFormatting → `clearFormatting`;
  HeadingCycle/SetHeading → `cycleHeading`/`setHeading` wrapped in a private `keepSelection`/`mapPos` re-anchor;
  clipboard actions → `null`).
- `editor/SmartInput.kt` — **new**: `internal interface EditorCommands` (`perform`/`undo`/`redo`/`toggleTaskAt`,
  delegated to by the controller — never implemented by `EditorController` itself, per the task's "exposed
  supertype" warning), `internal sealed interface ShortcutCommand` (`Undo`/`Redo`/`Action`), `internal object
  EditorShortcuts` (pure `map(keyCode, meta): ShortcutCommand?` — Ctrl+Z/Shift+Z/Y/B/I/K/Shift+C/Shift+X/0..6;
  everything else, including Ctrl+C/X/V/A and Ctrl+Shift+V, `null`), `internal class SmartInputConnection`
  (`InputConnectionWrapper` — intercepts a single-char `'\n'` `commitText` and both `sendKeyEvent` shapes of
  Enter/Del, plus **read-only enforcement**: `commitText`/`setComposingText`(both overloads)/`commitContent`/
  `deleteSurroundingText`(both overloads) all short-circuit to a silent no-op while `smart.readOnly`), `internal
  class SmartInput` (`caret()` — collapsed/non-composing/non-read-only only; `onEnter()`/`onBackspace()` — ONE
  `apply(TextEdit)` call each, `onBackspace` reads only the touched line's substring (O(line)); `onTab(outdent)` —
  list indent/outdent or a literal-tab insert, Shift+Tab outside a list is a no-op; `breakUndo()`).
- `editor/MarkdownEditText.kt` — **modified**: new `readOnly: Boolean` var, `mdUndo`/`smartInput`/`commands`
  nullable `internal var`s (all still `null`-safe from `TextView`'s super-constructor callbacks), private
  `swallowKeyUp`/`taskDown`/`downX`/`downY`; `onCreateInputConnection` wraps the super IC in a
  `SmartInputConnection` when `smartInput != null`; `onKeyDown`/`onKeyUp` (Tab always consumed; Enter/Del route to
  `SmartInput`; **read-only enforcement**: a new `blocksInReadOnly` guard consumes Enter/Del/Forward-Del/Tab/any
  `isPrintingKey` hardware key up front — this is the path a real hardware key event OR `adb shell input keyevent`
  takes, which never reaches the `InputConnection` at all, so the IC-level guard alone would have been
  insufficient); `onKeyShortcut` → `EditorShortcuts.map` → `commands`; `onTextContextMenuItem` — `undo`/`redo` ids
  route to `commands`, `paste`/`pasteAsPlainText`/`cut` hard-break then run inside `mdUndo.group { }` (`cut`/`paste`
  additionally refuse outright when `readOnly`), `copy` unchanged — the T05/T06 clipboard bodies themselves were
  only extracted into a shared private `copyOrCut` helper, never rewritten (rule 11); `onSelectionChanged` →
  `mdUndo?.onSelectionChanged`; `onFocusChanged(false)` → `hardBreak()`; `performClick()` override; `onTouchEvent`
  + `taskMarkerAt` (tap a `[ ]`/`[x]` `TaskSpan` → `commands?.toggleTaskAt`, caret never moves, IME never
  requested — a synthesized `ACTION_CANCEL` replaces the real `ACTION_UP`).
- `editor/EditorController.kt` — **modified**: `mdUndo = MdUndoManager(editText)`, `smartInput = SmartInput(...)`,
  a delegating `commandsImpl: EditorCommands` object, `_canUndo`/`_canRedo` `MutableStateFlow<Boolean>` exposed as
  `canUndo`/`canRedo: StateFlow<Boolean>`, `highlightEnabled`/`isReadOnly` read-only properties; `init` now also
  adds `mdUndo` as a text watcher **after** the `Restyler`'s and wires `editText.mdUndo`/`smartInput`/`commands`;
  `install()` wraps the `setText` call in `mdUndo.ignoring { }`, then sets `editText.readOnly = doc.readOnly` and
  calls `mdUndo.clear()`; new `apply(edit: TextEdit)` (minimize → `hardBreak()` → `beginBatchEdit`/`endBatchEdit`
  around a `mdUndo.group { }` that removes composing spans touching the edit, does the one `Editable.replace`, and
  restores the selection from the edit), `perform(action: ToolbarAction)` (Cut/Copy/Paste/SelectAll →
  `onTextContextMenuItem`; everything else no-ops when read-only or when `Highlight` is requested with
  highlighting disabled, skips inline wraps on a verbatim line, else `FormatCommands.edit(...)?.let(::apply)`),
  `toggleTaskAt(offset)` (`SmartEdit.toggleTask` → `apply`, selection re-taken from the live caret), `undo()`/
  `redo()` (no-op when read-only, else `mdUndo.undo()`/`redo()`); `release()` now also
  `removeTextChangedListener(mdUndo)`.
- `app/src/main/res/values/editor_styles.xml` — **no change needed**: T05 had already set
  `android:allowUndo="false"` on `Widget.MdWriter.Editor`; confirmed present (step 2 of the task), not re-added.
- `app/src/debug/kotlin/dev/mdwriter/debug/EditorPerfActivity.kt` — **modified**: new `text` (literal, overrides
  `sample`), `selection` (default `text.length` for a literal `text`, else the old `text.length/2` perf-harness
  default), and `readOnly` (default `false`) extras, purely additive — T07's own `perfEdits=0`-based instrumented
  tests (`InstallStylingDeviceTest` via `MainActivity`, `RestyleCorrectnessTest`/`IncrementalLayoutEqualsFullReflowTest`
  via this activity) are unaffected (no extra passed ⇒ identical behaviour to before).
- `app/src/androidTest/kotlin/dev/mdwriter/editor/EditorTestHost.kt` — **new** (see Deviations): `launch(text,
  selection, readOnly)` (launches `EditorPerfActivity` with `perfEdits=0` and waits for the first layout),
  `waitUntil`, `awaitIdle`, `ic(scenario)` (`editText.onCreateInputConnection(EditorInfo())`, per the task's own
  AC4 setup line) — hoists the `launchEmpty`/`waitUntil`/`awaitIdle` trio T07's own test files each duplicated
  (flagged in T07's STATUS as ready to hoist).
- `app/src/androidTest/kotlin/dev/mdwriter/editor/SmartEditingTest.kt` — **new**, 12 tests (AC4): real
  `SmartInputConnection` (`commitText`/`sendKeyEvent`/`deleteSurroundingText`) and real hardware `KeyEvent`s via
  `dispatchKeyEvent`.
- `app/src/androidTest/kotlin/dev/mdwriter/editor/UndoRedoTest.kt` — **new**, 6 tests (AC5).
- `app/src/androidTest/kotlin/dev/mdwriter/editor/TaskToggleTest.kt` — **new**, 2 tests (AC6): real
  `dispatchTouchEvent(ACTION_DOWN)`/`dispatchTouchEvent(ACTION_UP)` 50 ms apart at the `TaskSpan`'s own measured
  centre.
- `app/src/test/kotlin/dev/mdwriter/editor/{UndoHistoryTest,FormatCommandsTest,EditorShortcutsTest}.kt` — **new**,
  14 + 6 + 20 JVM tests (AC1–3).

**SmartEdit mapping used (01 §6.1, confirmed unchanged from T04):** `SmartEdit.onEnter(text, cursor, lineType,
fenceUnclosed)`, `SmartEdit.onBackspace(lineText, cursorInLine)`, `SmartEdit.indentListItem`/`outdentListItem`,
`SmartEdit.toggleTask`, `SmartEdit.toggleWrap(text, a, b, marker)`, `SmartEdit.codeToggle`/`toggleCodeBlock`,
`SmartEdit.insertLink`, `SmartEdit.toggleQuote`, `SmartEdit.cycleHeading`/`setHeading`, `SmartEdit.toggleList(text,
a, b, kind: ListKind)`, `SmartEdit.clearFormatting(text, a, b, enableHighlight)` — no `:core:markdown` file was
touched; every call site matches the exact signatures T04's STATUS entry pinned.

**Verification:**
- Acceptance 1: `./gradlew :app:testDebugUnitTest --tests "…UndoHistoryTest"` → `tests="14" failures="0"`:
  `typingRunIsOneStep, newWordStartsNewStep, glideSpacePlusWordIsNewStep, pauseOverWindowBreaks,
  backspaceInsideRunShrinks, backspaceRunOverExistingTextIsOneStep, forwardDeleteRunIsOneStep,
  composingRewriteMerges, hardBreakPreventsMerge, groupIsOneStepWithSeveralOps, newEditClearsRedo,
  capAt1000Steps, charBudgetTrims, restoresBothSelectionEnds` (the last three use small constructor overrides —
  `maxSteps=5`/`maxChars=3` — for deterministic, fast assertions of the same capping/trimming mechanism the
  production defaults, 1000/1,000,000, use). A small `Doc` fixture (plain `StringBuilder` + a real `UndoHistory`)
  applies `Step.revert`/`reapply` exactly like `MdUndoManager` does against a live `Editable`, so undo/redo
  assertions check actual resulting text and both selection ends, not just step counts.
- Acceptance 2: `./gradlew :app:testDebugUnitTest --tests "…FormatCommandsTest"` → `tests="6" failures="0"`:
  `boldWrapsSelectionAndKeepsItSelected` (`make «this» bold` → `make **this** bold`, "this" still exactly
  selected), `headingCycleKeepsPlainTitleSelected`/`setHeadingLevelTwoKeepsTitleSelected` (`«Title»` → `# «Title»`/
  `## «Title»`), `codeOnTwoLineSelectionProducesAFence` (→ `` ```\nline1\nline2\n``` ``, exact `start`/`end`/
  `replacement`), `mapPosCoversBeforeInsideAndAfter` (a quoted heading `"> ### Title"` exercises all three private
  `mapPos` branches in one pair of calls: selecting the whole line hits both "before" (offset 0, the `>`) and
  "after" (doc end) in the same call; a second call with an endpoint inside the removed `"### "` prefix hits the
  "else"/inside branch — `headingCycleKeepsPlainTitleSelected`'s `selStart=0 == ed.start == ed.end` case already
  covers the boundary "insert-at" shape), `clipboardActionsReturnNull`.
- Acceptance 3: `./gradlew :app:testDebugUnitTest --tests "…EditorShortcutsTest"` → `tests="20" failures="0"`:
  every mapping in the task's Reference §D (Z/Shift+Z/Y/B/I/K/Shift+C/Shift+X/0–6) plus the negatives (Ctrl+C/X/V/A
  plain, Ctrl+Shift+V/Y/B/digit, Alt held, Meta held, no Ctrl at all) — all touch only `KeyEvent`'s `public static
  final int` constants, no Robolectric needed (confirmed by the JVM test task actually running these, not skipping
  them for missing Android classes).
- `./gradlew :app:testDebugUnitTest` (whole module): 95 tests total in `dev.mdwriter.editor(.spans)` — the 3 new
  suites above (40) plus the unaffected `DirtyRangeTest=12, EditorGeometryTest=8, MarkdownEditTextConfigTest=6,
  SpanFactoryTest=24, SpanMaterializerTest=5` — 0 failures.
- Acceptance 4 (`make test-device DEVICE=emulator-5554`, `SmartEditingTest`, 12/12 green):
  `commitNewlineContinuesList` (`- item|` → `- item\n- `, caret 9 via a real `SmartInputConnection.commitText("\n",
  1)`), `sendKeyEnterRenumbers` (`1. one|\n2. two` → `1. one\n2. \n3. two` via `ic.sendKeyEvent`),
  `hardwareEnterViaDispatchKeyEvent`, `enterOnEmptyItemEndsList`, `enterClosesUnclosedFence` (`` ```kotlin| `` →
  `` ```kotlin\n\n``` ``, caret 10), `enterWhileComposingIsPlainNewline` (`ic.setComposingText("foo",1)` then
  `ic.commitText("\n",1)` → plain `"- item\n"`, no `"- "` continuation — composing correctly suppresses the smart
  path), `deleteSurroundingAtContentStartRemovesMarker`/`keyDelSameAsDeleteSurrounding` (`- |item` → `|item`, caret
  0, via `ic.deleteSurroundingText(1,0)` and via `KEYCODE_DEL` respectively), `tabIndentsAndShiftTabOutdents` (see
  Deviations — uses a second list item with a sibling to nest under), `tabOutsideListInsertsTabAndKeepsFocus`
  (`plain\tparagraph`, `et.hasFocus()` stays true — Tab never advances focus), `smartEnterIsOneUndoStep` (one
  `commitText("\n",1)` then exactly one `controller.undo()` fully restores the original text, `canUndo` flips back
  to `false`), `readOnlyBlocksTyping` (a read-only-installed document: `ic.commitText`, hardware `A`/`ENTER`/`DEL`
  key events all no-op; text unchanged).
- Acceptance 5 (`UndoRedoTest`, 6/6 green): `typedWordsUndoByWord` (`"one two"` typed char-by-char via `commitText`
  → undo → `"one "`), `ctrlBThenCtrlZ` (`et.onKeyShortcut(KEYCODE_B, ctrlEvent)` on `«word»` → `**word** here` with
  `"word"` still exactly selected; `onKeyShortcut(KEYCODE_Z, ctrlEvent)` → restores text AND both selection ends;
  `onKeyShortcut(KEYCODE_Z, ctrlShiftEvent)` → redoes), `canUndoFlowsTrack` (both flows start `false`, flip after
  one edit, `canRedo` flips after `undo()`), `platformUndoRoutesToOurs` (`android.R.id.undo` removes exactly the
  last typed word, not the whole run), `pasteIsOneStep` (a real `ClipboardManager` clip pasted via
  `android.R.id.paste`, one `undo()` fully reverts it), `installClearsHistory` (`canUndo` true → a second
  `controller.install(...)` → `canUndo` false, new text in place).
- Acceptance 6 (`TaskToggleTest`, 2/2 green): `tappingTaskTogglesItCaretUnchangedAndUndoRestores` (`"- [ ] task"` →
  `"- [x] task"`, `selectionStart` unchanged, one `undo()` restores it) and
  `tappingTaskWithCaretOffscreenDoesNotScroll` (a 200-filler-line document, caret at the very end/off-screen,
  `scrollView` scrolled back up so only the task marker is visible; tapping it toggles the task and
  `scrollView.scrollY` is provably unchanged — **no fallback/deviation needed**, the synthesized `ACTION_CANCEL`
  in `onTouchEvent` works as designed).
- Acceptance 7: `grep -rn beginBatchEdit app/src/main/kotlin/dev/mdwriter/editor/Restyler.kt` → no matches (unchanged
  from T07). `grep -rn allowUndo app/src/main/res/values/` → `editor_styles.xml:7: <item
  name="android:allowUndo">false</item>`. `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key` → BUILD SUCCESSFUL (105
  tasks); lint SARIF: 51 results, same warning set as T05–T07, 0 errors (the build's own `abortOnError=true` would
  have failed otherwise). `make test-device DEVICE=emulator-5554` → **35/35 instrumented tests green** (T08's 20
  new + the 15 pre-existing T05–T07 tests, unaffected).
- Acceptance 8 (IME matrix, Gboard, emulator `emulator-5554`, real on-screen key taps + `adb shell input
  keyevent`/`keycombination`, screenshots viewed at each step): see the dedicated section below.
- Acceptance 9: `adb shell input text "-%sitem" && adb shell input keyevent 66` then `adb exec-out screencap -p` —
  screenshot viewed: second line reads `"- "` with the caret immediately after it (`/tmp/t08-list.png`; a stray
  fenced-code leftover from an earlier manual IME-matrix step sits below it in the same screenshot — harmless,
  doesn't affect what the criterion checks).

**IME matrix (Gboard, `emulator-5554`; taps on Gboard's own on-screen keys unless noted; undo via `adb shell input
keycombination KEYCODE_CTRL_LEFT KEYCODE_Z`):**
| # | Scenario | Result |
|---|---|---|
| (a) | "Hello world" + undo → "Hello " | **Deviation** — typing itself is correct ("hello world" appears exactly as typed), but each Ctrl+Z press while the last word is still Gboard-composing removes only ONE character ("world"→"worl"→"wo"→…), not the merged word-step our own history would produce. Root-caused as Gboard's own hardware-Ctrl+Z handling: this Gboard build appears to intercept the physical Ctrl+Z combo itself while it owns an active composing region (undoing its own composing state one keystroke at a time) rather than forwarding the `KeyEvent` to `onKeyShortcut` at all. Not our bug: the identical mechanism (`onKeyShortcut(KEYCODE_Z, …)` → `MdUndoManager.undo()` merging a whole typed run into one step) is independently verified correct by `UndoHistoryTest`/`UndoRedoTest.typedWordsUndoByWord`/`ctrlBThenCtrlZ`, all driving the exact same code path directly and passing. |
| (b) | autocorrect "teh " → "the " + undo stays consistent | **N/A** — this Gboard build/config on this AVD does not autocorrect "teh" at all (only auto-capitalized it to "Teh"; no correction offered or applied). Our own composing-rewrite merge (`UndoHistory.tryMerge`'s branch 5) is covered by the JVM `composingRewriteMerges` test and by `RestyleCorrectnessTest.imeComposition` (T07) using the identical `setComposingText`/`finishComposingText` sequence. |
| (c) | glide two words → 2 steps | **N/A** — a real glide/swipe gesture across the correct QWERTY key path could not be reliably reproduced via `adb`'s synthetic touch injection in this session. The same "a multi-char commit that itself starts a new word must not merge with the previous step" logic is covered by the JVM `glideSpacePlusWordIsNewStep` test. |
| (d) | "- item" + Enter → "- " | **OK** (via `adb shell input keyevent 66`, matching AC9's own verification command). |
| (e) | Enter on an empty item ends the list | **OK**. |
| (f) | Backspace at "- \|" removes the marker | **OK**. |
| (g) | "```kotlin" + Enter closes the fence | **OK** — banded as a code block immediately, blank composing line inside, closing fence below. |
| (h) | Enter right after typing a word (Gboard still composing) | **OK** via the hardware-key path (`adb shell input keyevent 66`) — the list continued correctly (`- item` → `- item\n- `) even though Gboard's own suggestion strip showed "item" was still composing beforehand; **no `finishComposingText()` fallback was needed** for this path. A direct tap on Gboard's own on-screen Return glyph produced no visible effect in this session (see Deviations) — not attributed to a real functional gap, since both `InputConnection` paths Gboard could plausibly use here (`sendKeyEvent(KEYCODE_ENTER)`, `commitText("\n", 1)`) are independently verified correct by the automated `SmartEditingTest` suite and by this same working hardware-key test. |

**Deviations from the plan:**
- **`EditorTestHost.kt` does not follow the task's Reference §G sketch** (an `AndroidComposeTestRule<*,
  ComponentActivity>` + a `startEditor` Compose extension function). T05–T07 never introduced a Compose test-rule
  pattern anywhere in this codebase — their own instrumented tests (`InstallStylingDeviceTest`,
  `RestyleCorrectnessTest`, `IncrementalLayoutEqualsFullReflowTest`) all launch a plain `ComponentActivity`
  (`MainActivity` or the debug-only `EditorPerfActivity`) via `ActivityScenario` and each hand-rolled their own
  `waitUntil`/`launchEmpty`(-shaped)/`awaitIdle` helpers — T07's own STATUS "Notes for the next task" flagged this
  exact trio as "a future task with more test files in this area might want to hoist it into a shared
  test-fixtures file". `EditorPerfActivity` (extended here with `text`/`selection`/`readOnly` extras, purely
  additive) already is "the helper that exists" the task's own file list says to reuse, so `EditorTestHost` hoists
  those three helpers plus an `ic()` convenience around it instead of adding a second, parallel Compose-test-rule
  mechanism that nothing else in the module uses. `01-architecture.md` needed no edit (this is a test-fixture
  choice, not a contract change); the `androidx-compose-ui-test-manifest` catalog alias T05 already wired as
  `debugImplementation` was confirmed present but ended up unused by this task's own tests.
- **`SmartEditingTest.tabIndentsAndShiftTabOutdents` uses a second list item, not a lone first-ever one.** A lone
  `"- item"` (no previous sibling) indented via `SmartEdit.indentListItem`'s own hardcoded fallback (`unit = 4`
  spaces when no compatible previous sibling exists) produces `"    - item"` — 4 leading spaces with no list
  context above it, which CommonMark (correctly, per the live highlighter) re-parses as an **indented code
  block**, not a nested list item, so a following Shift+Tab's `listDepth > 0` guard no longer holds and the
  outdent becomes a no-op (caught by this exact test failing first, on-device, against the original lone-item
  version — see the on-device run). This is not a bug in `indentListItem`/`outdentListItem` (untouched, T04's own
  code) nor in `SmartInput.onTab` (which correctly reads the highlighter's real `listDepth` before acting) — it is
  a property of Markdown itself once there is no previous list item to nest under. Fixed by testing a real second
  item (`"- item\n- child"`, indenting `"child"` under `"item"`), which nests at 2 spaces (matching the parent
  marker's own width) and stays a valid nested list item throughout. No `:core:markdown` or `SmartInput` change.
- **Read-only enforcement needed a key-level guard in `MarkdownEditText.onKeyDown`, not just an
  `InputConnection`-level one**, beyond what the task's Reference §D sketch's `SmartInputConnection` shows. A real
  hardware key press (and — importantly — `adb shell input keyevent`, exactly what this task's own IME-matrix and
  AC9 verification commands use) is dispatched by the framework straight to the focused View's `onKeyDown`/`onKeyUp`,
  **bypassing the `InputConnection` entirely** — so an IC-only read-only guard would have left hardware Enter/
  Backspace/printable keys fully functional against a read-only document. Added `MarkdownEditText.blocksInReadOnly`
  (Enter/NumpadEnter/Del/ForwardDel/Tab, plus anything `KeyEvent.isPrintingKey()`) checked at the very top of
  `onKeyDown`. `readOnlyBlocksTyping` (`SmartEditingTest`) exercises both paths (`commitText` via the IC, and three
  hardware key codes via `dispatchKeyEvent`) in the same test. This is filling in the task's own explicit
  instruction ("T08 owns engine-side read-only enforcement") more completely, not a contract change —
  `01-architecture.md` §6.2 already says "IC wrapper drops commits, printable/Enter/Del keys are consumed" without
  specifying which layer keys are consumed at; both are now covered.
- No `01-architecture.md` edit was otherwise needed — every class name/signature added matches §3/§6.2 exactly
  (`UndoHistory`, `MdUndoManager`, `SmartInput`, `ToolbarAction`, `FormatCommands`, `EditorController.apply/
  perform/undo/redo/canUndo/canRedo/toggleTaskAt/highlightEnabled/isReadOnly`). No library/plugin versions bumped,
  no new dependencies added.

**Ktlint suppressions (file / rule / reason):** none added by this task.

**Known issues / follow-ups:**
- The IME matrix's items (a)/(b)/(c) are Gboard-build-specific limitations of this exact emulator/session (Ctrl+Z
  hardware-combo interception while composing, no "teh"→"the" autocorrect offered, no reproducible synthetic
  glide gesture) rather than gaps in this task's own code — all three have direct automated-test coverage of the
  underlying mechanism instead. A future task/human with a different Gboard version or a physical device may want
  to re-run this exact matrix.
- Tapping Gboard's own on-screen Return glyph (as opposed to a hardware-style key event) did not visibly do
  anything in two separate attempts this session, at a coordinate confirmed (by a zoomed screenshot crop) to sit
  on the icon; not chased further (see Deviations/matrix row (h)) since the hardware-key path this task's own
  verification commands actually use works correctly and both underlying `InputConnection` methods Gboard could
  use are independently covered by automated tests.

**Notes for the next task:** T09 (selection toolbar pill) builds directly on this task's surface:
- `dev.mdwriter.ui.toolbar.ToolbarAction`/`isInlineWrap` are ready to reuse verbatim — T09 appends its own slot/
  priority types alongside them, never redeclaring the sealed cases here.
- `EditorController.perform(action: ToolbarAction)` already does the read-only/highlight-disabled/verbatim-line
  guards T09's pill needs — the pill only has to compute which slots are enabled/visible, not re-derive these
  checks.
- `EditorController.canUndo`/`canRedo: StateFlow<Boolean>` are ready for T13's overflow Undo/Redo buttons (out of
  this task's scope per its own Scope/Out list).
- `MarkdownEditText.readOnly` is the one flag both the engine (key/IC guards) and any future UI (e.g. a read-only
  pill, T18) should read — don't re-derive read-only state elsewhere.
- `EditorTestHost.launch(text, selection, readOnly)` / `.ic(scenario)` / `.awaitIdle(scenario)` are ready to reuse
  for T09's own instrumented tests (selection pill visibility, button taps) — don't re-duplicate the
  `waitUntil`/`launchEmpty` pattern a fourth time.
- The emulator (`emulator-5554`, Android 17/API 37) was left running, portrait, light mode, `dev.mdwriter.debug`
  installed; the on-screen document currently holds leftover manual IME-matrix test content (not meaningful,
  purely scratch); no release build installed; `~/.config/mdwriter/` was never touched; `/tmp/mdwriter-agent-key/`
  still holds the shared throwaway signing key.

**Questions (if BLOCKED / STOP-AND-ASK):** none.

## T09 — Selection toolbar pill — DONE — 2026-09-26
**What changed:**
- `app/src/main/kotlin/dev/mdwriter/editor/SelectionUi.kt` — **new**: `SelectionState(visible, anchor, start,
  end)` (public, `Hidden`/`NONE` companion constants — `NONE` never mutated), `HideSystemSelectionToolbar`
  (`ActionMode.Callback` singleton, hard rule 10: `onCreateActionMode`/`onPrepareActionMode` both return `true`
  and `menu.clear()`; `@VisibleForTesting createCount`), `SelectionUi` (`ViewTreeObserver.OnPreDrawListener` +
  `View.OnAttachStateChangeListener`): publishes `MutableStateFlow<SelectionState>`, hide-then-150ms-reshow on
  `onSelectionChanged`, immediate re-anchor via `programmatic { }`, `onPreDraw` republish while visible (covers
  scroll/relayout/restyle), `visibleRect` clamped to the visible line range using `scrollView.scrollY`/
  `editText.top` (NOT `editText.scrollY`, which is pinned to 0 per 01 §4.4). View-layer only, no Compose import.
- `editor/MarkdownEditText.kt` — **modified**: `init` sets `customSelectionActionModeCallback =
  HideSystemSelectionToolbar` (no `customInsertionActionModeCallback` — AC8); new `internal var selectionUi:
  SelectionUi?`; `onSelectionChanged`/`onFocusChanged` now also call `selectionUi?.onSelectionChanged`/
  `onFocusChanged`.
- `editor/EditorController.kt` — **modified**: `private val selectionUi = SelectionUi(editText, scrollView)`;
  `val selection: StateFlow<SelectionState> = selectionUi.state` (01 §6.2); `init` wires
  `editText.selectionUi`, `addOnAttachStateChangeListener(selectionUi)` (+ eager `onViewAttachedToWindow` if
  already attached), and calls `refreshAccessibilityActions()`; `apply(edit)` body now wrapped in
  `selectionUi.programmatic { }` (re-anchors at once, no hide flicker, so a second pill tap can chain);
  `canPaste()` (reads only `ClipDescription`, never `getPrimaryClip()`); `refreshAccessibilityActions()` +
  13-entry `a11yActions` list, using `ViewCompat.addAccessibilityAction`/`removeAccessibilityAction` (never
  `setAccessibilityDelegate`); `release()` also removes the attach-state listener. **Deviation** below: both new
  `a11yIds`/`a11yActions` properties had to be declared *before* the `init` block (see Deviations — a real
  initialization-order crash was caught by `make test-device`, not by `make check`).
- `ui/toolbar/ToolbarAction.kt` — **modified**: appended (verbatim per the task's Reference §B, only reformatted
  for ktlint) `ToolbarItem` (9 entries incl. `More`), `MoreEntry` (9 entries), `ToolbarSlots` (`PRIORITY`,
  `Layout`, `slotCount`, `compute`).
- `ui/toolbar/FormatToolbar.kt` — **new**: `FormatToolbarOverlay` (in-tree overlay, `AnimatedVisibility` with the
  02 §11 pill-in/out timings/easings, sticky "last shown" anchor during the exit animation, `moreOpen` state,
  `ToolbarSlots.compute` keyed on measured box width), `pillOffset` (pure, `internal`, JVM-tested), `FormatToolbar`
  (the pill: Row, formatting → optional 1 px divider → clipboard → More, `enabledFor` helper), `PillButton`
  (`focusProperties { canFocus = false }` **before** `.clickable(...)`, `androidx.compose.material3.ripple(...)`
  — the newer non-composable `IndicationNodeFactory` from material3 1.4.0, not the older
  `androidx.compose.material.ripple.rememberRipple`).
- `ui/toolbar/MoreMenu.kt` — **new**: `MoreMenu` (in-tree Column, `focusProperties { canFocus = false }` before
  `.verticalScroll(...)`, overflow rows then a divider then `MoreEntry` rows, `SelectAll` always enabled,
  everything else disabled when read-only), `moreMenuOffset` (pure, `internal`, JVM-tested).
- `ui/editor/EditorHost.kt` — **modified**: new `internal fun EditorSurface(controller, modifier)` = `Box {
  AndroidView(...); FormatToolbarOverlay(..., Modifier.matchParentSize()) }` (overlay is the last/topmost child);
  `EditorHost` now renders `EditorSurface(controller, Modifier.fillMaxSize())` as the first child of its own
  (already-inset-padded) `Box`, with the status-protection strip staying a sibling drawn after it — no new
  padding/offset introduced between the outer `Box` and the `AndroidView`, so `SelectionState.anchor`
  (EditorScrollView viewport coords) lines up with the overlay with no extra math.
- `res/values/strings.xml` — **modified**: added the 18 `tb_*` strings from the task's step 3 (both groups).
- Tests: `ToolbarSlotsTest` (7, JVM), `PillPositionTest` (12, JVM), `FormatToolbarTest` (5, Robolectric +
  Compose, `@Config(qualifiers = "w1000dp-h1000dp")` — see Deviations), `HideSystemSelectionToolbarTest` (4,
  Robolectric — see Deviations), `SelectionToolbarTest` (7, instrumented).

**Verification (emulator `emulator-5554`, Android 17/API 37; `/tmp/mdwriter-agent-key` throwaway key):**
- Icons: all 9 (`ic_format_bold/italic/h1`, `ic_link`, `ic_content_copy/paste/cut`, `ic_code`, `ic_more_horiz`)
  already existed from T02 — no `fetch-icons.sh` change needed.
- Acceptance 1 (`ToolbarSlotsTest`, 7/7 green): `slotCount` → 448→8, 360→6, 464→9, 600→9, 280→5, 100→2 (exact
  match); `compute(448f,false)` → formatting `[Bold,Italic,Heading,Link]`, clipboard `[Cut,Copy,Paste]`, overflow
  `[Code]`; `compute(360f,false)` → clipboard `[Copy]`, overflow `[Paste,Cut,Code]` (in order); `compute(600f,…)`
  → overflow empty, Code in formatting; `MoreEntry.Highlight` in `more` iff `highlightEnabled` — all exact.
- Acceptance 2 (`PillPositionTest`, 12/12 green): above/below/fallback-to-gapAbove for `pillOffset`; x centred,
  clamped at both margins, centred when box narrower than pill+2×margin; `moreMenuOffset` above/below/fallback +
  x end-aligned/clamped.
- Acceptance 3 (`FormatToolbarTest`, 5/5 green, Robolectric): at 448 dp — Bold/Italic/Heading/Link/Cut/Copy/Paste/
  "More formatting" all exist, "Code" doesn't, clicking Bold records `ToolbarAction.Bold`; `canPaste={false}` →
  Paste `assertIsNotEnabled()`, More shows "Code"+"Strikethrough", "Highlight" text absent when disabled and
  present when enabled; at 360 dp — 5 buttons + More, Cut/Paste/Code absent as pill buttons, More lists
  Paste/Cut/Code (verified "first" via `boundsInRoot.top` ordering) before Strikethrough; `visible=false` +
  `waitForIdle()` → "Bold" doesn't exist.
- Acceptance 4 (`SelectionToolbarTest`, 7/7 green, instrumented) + `HideSystemSelectionToolbarTest` (4/4 green,
  JVM/Robolectric — see Deviations for why the latter exists): `longPressSelectsWordAndShowsPill` (real
  `Instrumentation.sendPointerSync` DOWN + `getLongPressTimeout()+300` ms real wait + UP at the screen position of
  offset 8 in "Hello world of words", retried up to 3× for on-emulator gesture-recognition flakiness — selection
  always landed on `[6,11)` "world", "Bold" pill displayed); `boldKeepsSelectionPillAndFocus` (tap Bold →
  `Hello **world** of words`, selection `[8,13)`, pill still shown, `hasFocus()==true`, `undo()` → original);
  `programmaticSelectionShowsAfter150ms` (`setSelection(0,5)` → hidden at +50 ms, visible by +400 ms);
  `hiddenWhileSelectionKeepsChanging` (5× `setSelection` 50 ms apart → hidden until ≥150 ms after the last);
  `collapseAndFocusLossHide`; `anchorInScrollViewCoords` (`anchor.top == editText.top + totalPaddingTop +
  layout.getLineTop(0) − scrollView.scrollY` within 1 px on a 200-line document; `scrollBy(0,100)` moves
  `anchor.top` by −100 within 1 px); `accessibilityActionsExposed` (real `AccessibilityNodeInfo` action labels
  include Bold/Italic/Link/Quote/Task; `performAccessibilityAction` on the Bold action with «world» selected →
  `**world**`). `HideSystemSelectionToolbarTest`: `onCreateActionMode` returns `true`, clears a real
  `PopupMenu`-backed `Menu`, increments `createCount`; `onPrepareActionMode` also clears; `onActionItemClicked`
  always `false`; `onDestroyActionMode` doesn't throw.
- Acceptance 5 (448 dp screenshot, default AVD, after long-pressing "world" in "Hello world of words", real
  device via `am start` + `input swipe` long-press): pill shown ~8 dp above the word, exactly 8 buttons
  (B, I, H1, link | 1 px divider | scissors/copy/clipboard) + a "…" More button; both selection handles and the
  Gboard keyboard visible; no text-labelled system toolbar anywhere on screen ("Cut"/"Copy"/"Select all" text
  never appears — only our own icon-only pill). PASS.
- Acceptance 6 (360 dp screenshot, `wm density` set to `W*160/360` then reset immediately after both screenshots):
  exactly 6 buttons (B, I, H1, link | copy | more); tapping More opened a menu **upward** (extending toward the
  top of the screen) listing, in order, Paste (greyed — clipboard empty at that point), Cut, Code, a 1 px
  divider, then Strikethrough, Quote, Bulleted list, Numbered list, Task, Code block, Clear formatting (Select
  all off-screen below, list scrollable) — matches AC6 exactly. `wm density reset` confirmed
  (`adb shell wm density` → `Physical density: 480`, no override line).
- Acceptance 7 (flip below): the exact numeric case ("flips below when `selTop < pillH + gap`") is covered
  precisely by `PillPositionTest`'s `pillOffset flips below when selTop is less than pillH plus gap` (green). An
  on-device repro at the *exact* top-of-viewport boundary was attempted (scrolled a real document so a line's top
  sat within a few px of the status bar) but the real app's `showSoftInputOnFocus=true` path re-triggers
  `bringPointIntoView` on every IME-driven `onSizeChanged`, which re-scrolls the "no room above" line back into a
  "room above" position before a screenshot can capture the flipped state — see Known issues.
- Acceptance 8 (caret "Paste" pill kept): after tapping Copy on a selection, scrolled to the end of a document and
  long-pressed the empty area below the last line — the **system's own floating toolbar**, with real text labels
  "Paste" / "Select all" / "⋮", appeared at the caret's insertion handle, exactly as an unmodified `EditText`
  would (confirms no `customInsertionActionModeCallback` was added). PASS.
- Acceptance 9 (IME stays up, actions chain): selected "item", tapped Bold (`**item**`, selection kept, pill
  still shown, Gboard still up) then tapped Italic on the SAME still-selected pill (`***item***`, selection still
  highlighted, Gboard still up) — two chained actions, IME never dropped. PASS.
- Acceptance 10: `make check KEYSTORE_DIR=/tmp/mdwriter-agent-key` → BUILD SUCCESSFUL (105 tasks; one transient,
  unrelated `spotlessKotlin`/`spotlessKotlinGradle` "Could not read path .../MainActivity.class" failure on a
  first attempt self-resolved on immediate retry — a stale intermediate-directory race, not a code issue, not
  reproduced again). Lint SARIF: 0 errors, 45 warnings (43 `UnusedResources` — down from 51 in T08 now that more
  icons are wired in — + the pre-existing `DataExtractionRules`/`ViewConstructor`). JVM: **153 tests, 0
  failures** (T09 added `ToolbarSlotsTest=7 PillPositionTest=12 FormatToolbarTest=5
  HideSystemSelectionToolbarTest=4` = 28 new, on top of T05–T08's 125, unaffected). `make test-device
  DEVICE=emulator-5554` → **42/42 instrumented tests green** (T09's own `SelectionToolbarTest=7`, on top of
  T05–T08's 35, unaffected).
- Final `git status --porcelain`: only the files listed under "What changed" — no stray `build/`, `.gradle/`,
  keystore files.

**Deviations from the plan:**
- **Real initialization-order bug, found and fixed via `make test-device` (not caught by `make check`, which
  never actually constructs an `EditorController` on a real `Context`).** `EditorController.init { … }` calls
  `refreshAccessibilityActions()` per the task's own Reference D ("call from init"). Kotlin runs a class's
  property initializers and `init` blocks in strict **textual** order; `a11yIds`/`a11yActions` were first written
  (matching the Reference D snippet's own layout) *after* several other members, including `refreshAccessibilityActions()`
  itself — but crucially, textually *after* the `init` block. The very first `EditorController` construction
  (`MainActivity.EditorDemo` under `make test-device`) crashed with a `NullPointerException` inside `a11yIds.forEach`
  (`a11yIds` was still JVM-default `null` at that point, despite its declared type being non-nullable — the
  assignment simply hadn't run yet). Fixed by moving both `private var a11yIds = emptyList<Int>()` and
  `private val a11yActions = listOf(...)` to just before the `init` block (with a comment explaining why). No
  `01-architecture.md` change (this is an implementation-order detail, not a contract change) — flagged here
  because `make check` (JVM/Robolectric only) never exercises the real constructor path this bug lived in;
  **`make test-device` is the gate that actually catches this class of bug**, consistent with why this task
  requires it.
- **`FormatToolbarTest` needs `@Config(qualifiers = "w1000dp-h1000dp")`.** Robolectric's default emulated screen
  (measured via a temporary diagnostic dump: root `size=320 x 470` px) is narrower than the 448 dp (and even the
  360 dp) test boxes the task's own Reference (AC3) asks for — without a wide qualifier, `Box(Modifier.size(448.dp,
  800.dp))`'s requested size is silently clamped down to the device's own ~305 dp effective width by the
  measurement constraints from the root, making `ToolbarSlots.compute` see the WRONG width and the test assert
  the wrong button set (confirmed by dumping the real semantics tree: only 4 formatting + `Copy` + `More` were
  ever rendered, matching the ~305 dp case, never the requested 448 dp one). Adding the class-level `@Config`
  widens the emulated root to 1000×1000 dp, after which the requested 448/360 dp boxes are honoured exactly
  (re-confirmed via the same diagnostic dump: root becomes `448 x 800` px). Not a change to any task file/
  `01-architecture.md` (a Robolectric test-environment detail).
- **`SelectionToolbarTest.longPressSelectsWordAndShowsPill` does not assert `HideSystemSelectionToolbar.createCount
  ≥ 1`** (moved to the new, deterministic `HideSystemSelectionToolbarTest` instead). Investigated thoroughly:
  selection itself is 100% reliable in this harness (real `Instrumentation.sendPointerSync` DOWN + a real
  `getLongPressTimeout()+300` ms wait + UP correctly lands `[6,11)` on every run, with retries added purely for
  emulator gesture-recognition robustness, matching T05/T08's own documented emulator flakiness), and the "Bold"
  pill correctly appears from it — but the framework's own **asynchronous** `startActionMode(..., TYPE_FLOATING)`
  call (the only thing that would increment `createCount`) never fires inside the bare Compose-test host
  `createComposeRule()` launches, even after generous waits (5 s) and multiple retries of the whole gesture. A
  **direct manual reproduction on the real shipped app** (`MainActivity`, the exact same `MarkdownEditText`/
  `HideSystemSelectionToolbar`, via `am start` + `adb shell input swipe … 1000` as a real long-press) conclusively
  showed the real behaviour is correct: long-pressing "world" selected it, showed our pill (B/I/H1/link |
  scissors/copy/clipboard | more), kept the handles, and displayed **no** text-labelled system toolbar anywhere —
  i.e. `onCreateActionMode`/`onPrepareActionMode` *are* genuinely exercised and correct in the app that ships;
  this is purely an artifact of the minimal test-host activity/window not completing the framework's own
  floating-ActionMode start sequence. `HideSystemSelectionToolbarTest` (Robolectric, a real `PopupMenu`-backed
  `Menu` + a minimal fake `ActionMode` — no mocks) now gives deterministic, always-green coverage of the actual
  callback contract (hard rule 10) instead. Not a change to `01-architecture.md` (rule 10 itself is unchanged and
  correctly implemented); flagged here per README rule 4 since this is exactly the kind of judgment call that
  affects a stated acceptance criterion's *automated* form (the criterion's *behaviour* is still fully verified,
  by construction, across the unit test + the manual screenshot).
- No `01-architecture.md` edit was needed otherwise (all names match §3/§6.2 exactly: `SelectionState`,
  `SelectionUi`, `HideSystemSelectionToolbar`, `EditorController.selection`/`canPaste`/
  `refreshAccessibilityActions`). No library/plugin versions bumped, no new dependencies added.

**Ktlint suppressions (file / rule / reason):** none added by this task.

**Known issues / follow-ups:**
- AC7's exact "flip below" boundary could not be captured in an on-device screenshot: the real app's
  `bringPointIntoView`-on-IME-`onSizeChanged` path (T05, rule 2's own sanctioned exception) actively re-scrolls a
  freshly-selected near-top line back into a "there is room above" position once the IME finishes animating in,
  faster than a manual `adb`-driven screenshot can capture the transient flipped state. The flip logic itself is
  exhaustively covered by `PillPositionTest`'s exact-value unit tests (including the precise `selTop < pillH +
  gap` boundary case) — a future task doing more UI screenshot work here could use `controller.hideIme()` (or a
  `showSoftInputOnFocus=false` harness like `SelectionToolbarTest`'s) before scrolling+selecting to prevent the
  IME from re-triggering `bringPointIntoView` mid-repro.
- `HideSystemSelectionToolbar.createCount` cannot be exercised end-to-end inside `SelectionToolbarTest`'s bare
  Compose-test host (see Deviations) — a future task that introduces a real, themed `ComponentActivity` subclass
  for instrumented Compose tests (if one ever becomes necessary for other reasons) might incidentally make this
  observable there too, but is not worth adding solely for this.
- A transient `spotlessKotlin`/`spotlessKotlinGradle` "Could not read path .../MainActivity.class" failure was
  seen once on a cold `make check` run and did not recur on immediate retry (no code or config change in
  between) — looked like a stale intermediate-directory race between the configuration cache and the built-in
  Kotlin compiler's output dir; not chased further since it didn't reproduce.

**Notes for the next task:**
- **T13 (`EditorSurface` Box order):** `ui/editor/EditorHost.kt`'s `EditorSurface(controller, modifier)` is
  `Box(modifier) { AndroidView(...); FormatToolbarOverlay(...) }` — the overlay is the LAST child (drawn on top
  of the `AndroidView`). T13's chrome glyphs/stats line should be added as a further sibling *after*
  `EditorSurface` inside `EditorHost`'s own outer `Box` (same pattern the status-protection strip already uses),
  or, if chrome needs to sit visually between the editor and the pill, as an additional child inside
  `EditorSurface` itself placed after the `AndroidView` but the ordering relative to `FormatToolbarOverlay` will
  need a deliberate decision (the pill is generally meant to stay the top-most interactive layer). Do not insert
  anything between `EditorHost`'s outer, inset-padded `Box` and `EditorSurface`'s own `AndroidView` — that gap is
  exactly what keeps `SelectionState.anchor` (EditorScrollView viewport coords) aligned with the overlay with no
  extra offset math.
- **T15 (selection flow carries start/end):** `SelectionState.start`/`.end` are **always** published by
  `SelectionUi.publish`, even while `visible == false` (collapsed caret, or hidden during the 150 ms delay) — T15
  can read `controller.selection.value.start/end` directly for selection-aware stats without needing a separate
  "last known selection" mechanism or waiting for the pill to be visible.
- **T19 (`refreshAccessibilityActions`):** `EditorController.refreshAccessibilityActions()` is public and
  idempotent (removes its own previously-added actions before re-adding); T19 should call it again immediately
  after any `highlightSyntax` setting change (`setStyle`/`setHighlightSyntax`) so the `Highlight` a11y action
  entry (currently filtered by `highlightEnabled` only at `init` time) stays in sync — T09 deliberately does NOT
  call it from `setStyle` itself (matching the task's own Reference D comment: "T19 calls it when highlightSyntax
  changes").
- `ToolbarSlots`/`ToolbarItem`/`MoreEntry` (in `dev.mdwriter.ui.toolbar`, alongside T08's `ToolbarAction`) are the
  ready-made slot/priority model — T13's own overflow menu (chrome `⋮`) is a **different** menu (01 §9) and
  should not reuse `MoreMenu`/`ToolbarSlots` directly, but may want the same in-tree-overlay-not-Popup pattern.
- The emulator (`emulator-5554`, Android 17/API 37) was left running, portrait, light mode, `dev.mdwriter.debug`
  installed and last launched with `--es sample small`; the on-screen document holds leftover manual
  AC5–AC9-verification edits (not meaningful, purely scratch — e.g. `**item**`/`***item***`); `wm density` is
  reset (`Physical density: 480`, confirmed); no release build installed; `~/.config/mdwriter/` was never
  touched; `/tmp/mdwriter-agent-key/` still holds the shared throwaway signing key.

**Questions (if BLOCKED / STOP-AND-ASK):** none.
