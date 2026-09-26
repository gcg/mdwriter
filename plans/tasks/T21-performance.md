# T21 — Performance validation (+ optional baseline profile)

**Goal** Every budget in 01 §9 is measured on the emulator. Numbers are recorded in `plans/perf-results.md` and
STATUS.md, and failures are fixed or escalated. The user gets a written protocol to repeat the measurements on their
own phone later. A baseline-profile module is added **only if** cold start misses 800 ms. Normal builds and
`make install` must keep working either way.

**Depends on** T20: `PerfLog` markers (`open.request`, `open.loadingShown`, `open.installed`, `open.firstFrame`),
`scripts/qa/gen-doc.sh` and `push-doc.sh`, StrictMode clean, TalkBack off. T07: the per-keystroke perf harness. Its
class, invocation and output format are in T07's STATUS entry. T15: focus mode. T09: the pill. T13: the wide layout.

**Read first**
- `plans/01-architecture.md` §4.1–4.3, §7, §9 (budgets), §10 rules 3, 4, 13, §12.
- `plans/research/editor-engine.md` §2.1 (reference numbers), §7 items 4, 5, 7 (`sed -n '87,107p;1193,1210p'`).
- `plans/research/markdown.md` §6, "Budget rules" (`sed -n '334,389p'`).
- `plans/research/factcheck.md` §7 items 11, 12. README "Rules for agents" 3, 5, 6.
- The T06 span classes: `grep -n 'class .*LeadingMarginSpan\|UpdateLayout' app/src/main/kotlin/dev/mdwriter/editor/spans/Spans.kt`
  (find the global hang-room span).

## Scope — In / Out
**In:** measurements on the emulator; the debug timing of `highlighter.update`; `ReportDrawnWhen` for cold start;
small local fixes when a budget fails; the optional `:baselineprofile` module (exactly the dependencies authorised
below); `plans/perf-results.md`, including the phone protocol.
**Out:**
- Running anything on a physical phone (HARD RULE 13). The protocol is written for the user.
- Architectural changes. STOP-AND-ASK instead (list in Pitfalls).
- Changing budgets in 01 §9. If one cannot be met, STOP-AND-ASK with the numbers.
- New QA scenarios or README work (T22). Accessibility or StrictMode work (done in T20).
- Any dependency other than: `androidx.baselineprofile` Gradle plugin 1.5.0,
  `androidx.benchmark:benchmark-macro-junit4:1.5.0`, and `androidx.test.uiautomator:uiautomator:2.4.0`.

## Files to create / modify
- `app/src/main/kotlin/dev/mdwriter/util/PerfStats.kt`: new. A pure ring buffer with percentiles, JVM-tested.
- `app/src/test/kotlin/dev/mdwriter/util/PerfStatsTest.kt`: new JVM test.
- `app/src/main/kotlin/dev/mdwriter/editor/Restyler.kt`: modify. Times `highlighter.update` in debug and logs
  percentiles.
- `app/src/main/kotlin/dev/mdwriter/ui/root/MdWriterRoot.kt`: modify. Adds
  `ReportDrawnWhen { !ui.loading && ui.doc != null }` (androidx.activity.compose).
- The T07 harness files (path from T07 STATUS): modify. Add the `focus`, `selection` and `label` parameters if missing.
- `plans/perf-results.md`: new. All tables, the method, and the phone protocol.
- `plans/STATUS.md`: append the entry.
- **Only if cold start > 800 ms:**
  - `settings.gradle.kts`: `include(":baselineprofile")`.
  - `gradle/libs.versions.toml`: the three authorised dependencies plus the `com.android.test` plugin alias (AGP
    version ref).
  - `build.gradle.kts` (root): the plugins, `apply false`.
  - `app/build.gradle.kts`: plugin, `baselineProfile(project(":baselineprofile"))`, and the config block.
  - `baselineprofile/build.gradle.kts`.
  - `baselineprofile/src/main/kotlin/dev/mdwriter/baselineprofile/BaselineProfileGenerator.kt`.
  - `app/src/release/generated/baselineProfiles/baseline-prof.txt`: generated, committed.

## Steps
1. **Emulator hygiene.**
   - `make emulator`, `make devices`.
   - TalkBack off: `settings delete secure enabled_accessibility_services`.
   - `font_scale 1.0`, `wm density reset`, animations at their defaults.
   - Cold boot if the emulator has been up for more than a day.
   - Record the AVD, host CPU and the emulator's `ro.build.fingerprint` in perf-results.md.
2. **Debug app.** Run `make install-debug DEVICE=emulator-5554`, then
   `adb shell cmd package compile -m speed -f dev.mdwriter.debug`. That mirrors the research AOT methodology
   (editor-engine §2.1), but keep one JIT run per size for comparison. Debuggable builds are pessimistic: judge the
   budgets on the speed-compiled run, and write both in the table.
3. **`highlighter.update` timing** (Reference A). Add `PerfStats` plus the Restyler hook (debug only, zero cost when
   `PerfLog.enabled == false`). Run the harness at 100k. The logcat line `MdPerf hl.update … p95=…` must show
   p95 < 1 ms. If the reference `lastRescannedLines` is public, log a warning when it is > 2000 (markdown §6 rule 1).
4. **Per-keystroke runs** (Reference B). Use the T07 harness at 20k, 100k and 300k chars with 3 runs each (24+ edits
   per run, `vary=true` if the harness supports it). Record the median and p90 of the per-keystroke main-thread time.
   Scenarios:
   - **S1** Phone baseline (448 dp): focus Off, no selection.
   - **S2** Focus Sentence (T15 overlay).
   - **S3** Selection pill visible. Each step extends the selection by 1 char with `setSelection(start, end+1)`,
     which measures anchor recomputation + pill recomposition. Typing is not the action here.
   - **S4** Medium width: `adb shell wm density 320` (672 dp, gutter 4 chars, hang spans active).
   - **S5** Expanded: `wm density 240` (896 dp, gutter 6 chars + permanent pane).

   Budgets:

   | Size | Median | p90 |
   |---|---|---|
   | 100k | ≤ 8 ms | ≤ 12 ms |
   | 300k | ≤ 12 ms | none |

   20k is informational. Reset `wm density` afterwards.
5. **Global hang-room span cost.** Compare S1 with S4 at 300k. The global `LeadingMarginSpan` is correctly *not*
   `UpdateLayout` (HARD RULE 4). Because it covers the selection, every keystroke's span-change broadcast still
   reaches TextView and invalidates the text display lists. Quantify it:
   - Run S4 once with the span removed through a debug-only switch (for example a `EditorStyle` debug flag in the
     harness that sets the gutter to 0 but keeps the width). Report the delta in ms.
   - If S4 is within budget, just record the number.
   - If S4 is over budget, STOP-AND-ASK. The fixes (per-line spans, a different gutter mechanism) are architectural.
6. **Open time** (Reference C). Push `big-100k.md` and `big-300k.md` with `push-doc.sh`, then force-stop and relaunch.
   Open them alternately from the drawer 5 times each; tap coordinates come from `uiautomator dump`. Read
   `adb logcat -s MdPerf | grep 'open.request->open.firstFrame'`. Budget: **100k ≤ 1 s warm** (median of 5; 01 §9).
   Record 300k too (no budget; target ≤ 1.2 s) and `open.installed` (the setText cost).
7. **Cold start (release)** (Reference D).
   - Add `ReportDrawnWhen`.
   - Run `make install KEYSTORE_DIR=/tmp/mdwriter-agent-key DEVICE=emulator-5554`. This creates a throwaway key and
     runs installRelease, which installs the APK and the `.dm`.
   - Verify the profile with `adb shell pm art dump dev.mdwriter`. Expect `speed-profile` with reason `install-dm`
     (fallback: `adb shell dumpsys package dexopt | grep -A3 dev.mdwriter`).
   - Take 10 launches: `am force-stop`, then `am start -W`. Record `TotalTime` and the logcat
     `Fully drawn dev.mdwriter/.MainActivity: +Nms`. Drop the first run and take the median.
   - Budget: **Fully drawn ≤ 800 ms**. The last document is the welcome note plus a typed paragraph.
8. **Jank while scrolling 300k** (debug app, speed-compiled). Run `dumpsys gfxinfo dev.mdwriter.debug reset`, then
   10 flings (`input swipe 700 2400 700 500 120`), then `dumpsys gfxinfo dev.mdwriter.debug`. Record "Janky frames"
   % and the 50th/90th/99th percentiles. T21 target (not in 01 §9): janky < 10 %, p90 ≤ 20 ms. Missing it is a
   follow-up, not a STOP.
9. **If an 01 §9 budget fails** (Reference E):
   - Profile. Section timers first, then a Perfetto trace saved to `/tmp` as evidence.
   - Apply local fixes. Examples: avoid a second measure pass on install, make sure spans are built before `setText`
     (not after), remove per-keystroke allocations in reconcile, debounce pill anchor updates to one per frame.
   - Re-measure, and record before/after.
   - If the fix is architectural → STOP-AND-ASK.
10. **Baseline profile: only if step 7's median > 800 ms** (Reference F).
    - Add the module and generate with `:app:generateReleaseBaselineProfile` against the emulator.
    - Re-run step 7 and record before/after.
    - Verify `make install KEYSTORE_DIR=/tmp/mdwriter-agent-key DEVICE=emulator-5554` still works, `pm art dump`
      still shows `install-dm`, and `make check` is green.
    - If the plugin 1.5.0 is incompatible with AGP 9.3.3 (sync error), revert the module and STOP-AND-ASK. Do not
      change versions.
    - If the budget is met, skip this step and write "baseline profile: not needed (median N ms)".
11. **Clean up.** `make uninstall CONFIRM=yes DEVICE=emulator-5554` (the throwaway-signed release app, emulator only),
    `rm -rf /tmp/mdwriter-agent-key`, `wm density reset`.
12. **Write `plans/perf-results.md`** (Reference G) and the STATUS entry (headline numbers, pass/fail per budget).
    Then `make check`, `make test-device DEVICE=emulator-5554`, and commit.

## Reference code
**A. PerfStats + Restyler hook** (write it as shown; it is small)
```kotlin
class PerfStats(private val capacity: Int = 256) {
    private val buf = LongArray(capacity); private var n = 0
    /** Returns true when the buffer just filled (caller logs, then it resets). */
    fun add(nanos: Long): Boolean { buf[n++] = nanos; return n == capacity }
    fun percentileMs(p: Double): Double { val s = buf.copyOf(n).also { it.sort() }
        return s[((n - 1) * p).toInt().coerceIn(0, n - 1)] / 1e6 }
    fun reset() { n = 0 }
    val count: Int get() = n
}
// Restyler, around the existing call (keep the call itself unchanged):
val t0 = if (PerfLog.enabled) System.nanoTime() else 0L
val delta = highlighter.update(s, start, removed, added)
if (PerfLog.enabled && updateStats.add(System.nanoTime() - t0)) {
    Log.i(PerfLog.TAG, String.format(Locale.ROOT, "hl.update n=%d p50=%.3f p95=%.3f max=%.3f",
        updateStats.count, updateStats.percentileMs(0.5), updateStats.percentileMs(0.95), updateStats.percentileMs(1.0)))
    updateStats.reset()
}
```
`PerfStatsTest` covers: 1..100 ms inputs give p50 ≈ 50, p95 ≈ 95, max 100; `add` returns true exactly at capacity;
`reset` empties the buffer.

**B. Harness invocation.** Use T07's recorded command. The expected shapes are:
- **(a) Debug-only activity**, like the bench `MainActivity`:
  `adb shell am start -S -W -n dev.mdwriter.debug/dev.mdwriter.debug.EditorPerfActivity --es sample 100k --ei perfEdits 24 --ez vary true --es focus sentence --ez selection false --es label S2` (T07's extra names),
  then `adb logcat -d | grep 'MDPERF RESULT'`.
- **(b) Instrumented test:**
  `make test-device DEVICE=emulator-5554 GRADLE_FLAGS='-Pandroid.testInstrumentationRunnerArguments.class=<fqcn> -Pandroid.testInstrumentationRunnerArguments.size=100000 -Pandroid.testInstrumentationRunnerArguments.focus=sentence'`.

T07 already ships the harness: `dev.mdwriter.debug/dev.mdwriter.debug.EditorPerfActivity` (extras `sample`, `perfEdits`,
`vary`, `verifyLayout`) printing `MDPERF RESULT|<sample>|per-keystroke-main-thread-work|med=|p90=|n=` and
`MDPERF LAYOUT|equal=|lines=`. Reuse it; add `focus` (`off|sentence|paragraph`), `selection` (bool; S3 behaviour) and
`label` extras if missing, and append `|label=<S1..S5>` to T07's line — do not invent a second format. It measures per-keystroke main-thread time
the same way as the research bench (edit → next frame end via FrameMetrics / Choreographer), so the numbers compare
with editor-engine §2.1 (fast2r: 2.1 / 5.4 / 6.8 ms median).

**C. Open-time loop** (sketch)
```sh
for i in 1 2 3 4 5; do
  $ADB -s $S shell input tap $LIB_X $LIB_Y; sleep 1          # "Open library" glyph (from uiautomator dump)
  $ADB -s $S shell input tap $ROW100K_X $ROW100K_Y; sleep 3  # row "big-100k"
  $ADB -s $S shell input tap $LIB_X $LIB_Y; sleep 1
  $ADB -s $S shell input tap $ROW300K_X $ROW300K_Y; sleep 4
done; $ADB -s $S logcat -d -s MdPerf | grep -E 'open.request->open.(installed|firstFrame)'
```

**D. Cold start** (sketch)
```kotlin
// MdWriterRoot (inside the ComponentActivity's composition):
ReportDrawnWhen { !ui.loading && ui.doc != null }   // androidx.activity.compose.ReportDrawnWhen
```
```sh
for i in $(seq 1 10); do $ADB -s $S shell am force-stop dev.mdwriter; sleep 1
  $ADB -s $S shell am start -W -n dev.mdwriter/.MainActivity | grep TotalTime; sleep 3; done
$ADB -s $S logcat -d | grep 'Fully drawn dev.mdwriter'
```

**E. Profiling** (sketch)
- Section timers: temporary `PerfStats` instances around the TextWatcher, the reconcile frame callback,
  `onMeasure`/`onDraw` of `MarkdownEditText`, and the pill anchor computation. Log them like A, and remove or keep
  them debug-gated.
- `android.os.Trace.beginSection("md:reconcile")`/`endSection()` around the same regions, then:
  `adb shell perfetto -o /data/misc/perfetto-traces/md.pftrace -t 15s -b 64mb sched freq gfx view input am wm dalvik`
  and `adb pull /data/misc/perfetto-traces/md.pftrace /tmp/`. The trace is evidence for the human (ui.perfetto.dev).
  Base decisions on the section timers; the agent has no trace viewer.

**F. Baseline profile module** (sketch, UNVERIFIED on AGP 9.3.3; built-in Kotlin applies to `com.android.test`, and
do not apply `org.jetbrains.kotlin.android`, HARD RULE 1)
```kotlin
// baselineprofile/build.gradle.kts
plugins { alias(libs.plugins.android.test); alias(libs.plugins.androidx.baselineprofile) }
android {
    namespace = "dev.mdwriter.baselineprofile"; compileSdk = 37
    defaultConfig { minSdk = 36; targetSdk = 37; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    targetProjectPath = ":app"
}
baselineProfile { useConnectedDevices = true }
dependencies { implementation(libs.androidx.benchmark.macro.junit4); implementation(libs.androidx.uiautomator) }
// app/build.gradle.kts additions
plugins { alias(libs.plugins.androidx.baselineprofile) }
baselineProfile { automaticGenerationDuringBuild = false; saveInSrc = true }   // normal builds never need a device
dependencies { baselineProfile(project(":baselineprofile")) }
```
```kotlin
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    @Test fun journey() = rule.collect(packageName = "dev.mdwriter", includeInStartupProfile = true) {
        pressHome(); startActivityAndWait()
        device.findObject(By.clazz("android.widget.EditText"))?.click()
        device.executeShellCommand("input text Hello_profile"); device.waitForIdle()
        device.findObject(By.desc("Open library"))?.click(); device.waitForIdle()
        device.pressBack(); device.waitForIdle()
    }
}
```
Generate with `export JAVA_HOME=$(/usr/libexec/java_home -v 21); ANDROID_SERIAL=emulator-5554 ./gradlew
:app:generateReleaseBaselineProfile` plus the same signing properties/env the Makefile passes for `KEYSTORE_DIR`
(read the `keystore`/`GRADLE_INSTALL` recipes in `Makefile`). Use only classes available transitively
(AndroidJUnit4, the runner); if one is missing, STOP-AND-ASK.

**G. `plans/perf-results.md` outline**
1. Environment: AVD, image fingerprint, host, date, build type/compile mode.
2. Per-keystroke table: rows S1–S5, columns 20k/100k/300k, cells `median / p90`, then a budget verdict column.
3. `hl.update` p50/p95/max at 100k.
4. Hang-span delta.
5. Open 100k/300k (installed / firstFrame).
6. Cold start: TotalTime, Fully drawn, .dm status, and whether a baseline profile was added.
7. Jank.
8. Fixes applied, before → after.
9. "Run it on your phone". Steps for the user only; the agent must not run them:
   - `make devices`, then `make install-debug DEVICE=<phone-serial>` and
     `adb -s <phone-serial> shell cmd package compile -m speed -f dev.mdwriter.debug`.
   - Push the docs with `scripts/qa/push-doc.sh <phone-serial> /tmp/big-100k.md`, then run the §B commands with
     `-s <phone-serial>`.
   - For cold start use `make install DEVICE=<phone-serial>`, which uses the real key in `~/.config/mdwriter`.
     Do not uninstall afterwards: that deletes notes.
   - Paste the RESULT lines into a new "Phone" column.
   - Uninstall the debug app on the phone with `make uninstall-debug DEVICE=<phone-serial>`. It is a separate app,
     so the real notes are safe.

## Acceptance criteria
1. `PerfStatsTest` passes. `adb logcat -s MdPerf` shows `hl.update` lines, and p95 at 100k is < 1 ms.
2. perf-results.md has all S1–S5 × 20k/100k/300k cells filled (3 runs each, median of medians). S1 at 100k is
   median ≤ 8 ms and p90 ≤ 12 ms. S1 at 300k is median ≤ 12 ms. Or the task is BLOCKED with a STOP-AND-ASK
   containing the numbers.
3. S2–S5 at 100k/300k are within the same budgets, or each exceedance has a profile summary and a fix or a
   STOP-AND-ASK.
4. The hang-span delta (S4 with vs without the global span) is recorded in ms.
5. `open.request->open.firstFrame` median for 100k is ≤ 1 s (warm) and a placeholder shows within 100 ms. 300k is recorded.
6. `pm art dump dev.mdwriter` (or the dexopt fallback) shows the profile installed from the `.dm`. The "Fully drawn"
   median is ≤ 800 ms, with or without the baseline profile module.
7. If the module was added: `make check` is green, `make install KEYSTORE_DIR=/tmp/mdwriter-agent-key
   DEVICE=emulator-5554` succeeds, and `./gradlew :app:assembleRelease` works with no device connected
   (`automaticGenerationDuringBuild = false`).
8. Jank numbers are recorded.
9. The emulator has no `dev.mdwriter` (release) package afterwards: `adb shell pm list packages dev.mdwriter` shows
   only `dev.mdwriter.debug`. `/tmp/mdwriter-agent-key` is gone.
10. perf-results.md contains the phone protocol. STATUS has the headline table.

## Verification commands
```sh
make test                                             # PerfStatsTest
make install-debug DEVICE=emulator-5554
ADB=~/Library/Android/sdk/platform-tools/adb; S=emulator-5554
$ADB -s $S shell cmd package compile -m speed -f dev.mdwriter.debug
scripts/qa/gen-doc.sh 100000 > /tmp/big-100k.md && scripts/qa/push-doc.sh $S /tmp/big-100k.md
scripts/qa/gen-doc.sh 300000 > /tmp/big-300k.md && scripts/qa/push-doc.sh $S /tmp/big-300k.md
$ADB -s $S logcat -c; <harness command from Reference B>; $ADB -s $S logcat -d -s MdPerf
$ADB -s $S shell wm density 320   # S4;  wm density 240 → S5;  wm density reset
make install KEYSTORE_DIR=/tmp/mdwriter-agent-key DEVICE=emulator-5554
$ADB -s $S shell pm art dump dev.mdwriter | grep -iE 'status|reason'
$ADB -s $S shell dumpsys gfxinfo dev.mdwriter.debug reset; $ADB -s $S shell dumpsys gfxinfo dev.mdwriter.debug | grep -E 'Janky|percentile'
make uninstall CONFIRM=yes DEVICE=emulator-5554 && rm -rf /tmp/mdwriter-agent-key
make check && make test-device DEVICE=emulator-5554
```

## Pitfalls
- **Architectural = STOP-AND-ASK.** That covers:
  - replacing the global hang span or the gutter scheme (02 §3, HARD RULE 4);
  - moving the highlighter off the main thread (01 §6.1, §7);
  - replacing EditText or `setText` with incremental install;
  - changing MdEditable's broadcast-suppression rule (01 §4.3);
  - adding UpdateLayout to a document-wide span (HARD RULE 4, factcheck A17);
  - wrapping reconcile in batch edits (HARD RULE 3).
- Research open-to-first-frame on this emulator was 0.6–1.2 s including warm-up (editor-engine §2.1), so the 1 s warm
  budget is tight. Measure warm opens (2nd+), and report the first-ever open separately.
- Debuggable builds run slower. Always speed-compile, and say which mode each number comes from. Never enable
  StrictMode `penaltyDeath` or accessibility services during runs.
- `wm density` persists. Reset it before cold-start runs or you will measure the tablet layout.
- Only one `dev.mdwriter` release app may exist on the emulator. It is signed with the /tmp key; uninstall it at the
  end (emulator only, `CONFIRM=yes`). Never touch `~/.config/mdwriter` (README rule 6).
- `input text` sends key events, not IME composition, and harness numbers exclude the IME. Say so in the results.
- The baseline-profile plugin adds build types (`nonMinifiedRelease`, `benchmarkRelease`). They must reuse the
  release signing config through the same `KEYSTORE_DIR` mechanism. If the plugin forces a device during a normal
  build, set `automaticGenerationDuringBuild = false` (factcheck §7.12).
- The `ReportDrawnWhen` condition must eventually become true on every path, including the empty library and the
  welcome note. Otherwise "Fully drawn" never logs.

## Definition of done
- [ ] Every budget in 01 §9 measured on the emulator and recorded with a pass/fail verdict (or STOP-AND-ASK written).
- [ ] `plans/perf-results.md` complete, including the phone protocol. STATUS entry with the headline numbers.
- [ ] The baseline profile decision recorded (added or not needed, with the number).
- [ ] Emulator clean: release app uninstalled, `/tmp` key removed, `wm` reset.
- [ ] `make check` and `make test-device DEVICE=emulator-5554` green.
- [ ] Commit `T21: performance validation (<pass summary>)`.
