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
