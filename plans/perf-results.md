# Performance results (T21)

Measured on the emulator only (HARD RULE 13). All numbers are from a **debug build compiled with
`cmd package compile -m speed -f`** (the harness `EditorPerfActivity` is debug-only), except cold start (release build).

## 1. Environment
- AVD `Pixel_10_Pro_XL`, image `google/sdk_gphone16k_arm64/emu64a16k:17/CP31.260623.009/15923651:user/dev-keys`
- Host: Apple M1 Max. Date 2026-10-02. TalkBack off, font scale 1.0, `wm` density default (S4/S5 change it, then reset).
- The harness feeds edits through the `Editable` directly: no IME is involved. `input text` was not used.

## 2. Per-keystroke main-thread time (median of 3 runs' medians / median of p90), ms
Harness: `EditorPerfActivity` with `vary=true`, 24 edits per run. Budgets (01 §9): 100k median <= 8 and p90 <= 12; 300k median <= 12.

| Scenario | 20k | 100k | 300k |
|---|---|---|---|
| S1 | 7.7 / 15.1 | 11.9 / 20.9 | 13.8 / 27.5 |
| S2 | 8.8 / 16.4 | 12.9 / 21.7 | 17.9 / 28.2 |
| S3 | 5.5 / 7.8 | 8.2 / 10.3 | 13.1 / 16.0 |
| S4 | 9.8 / 15.8 | 12.4 / 21.7 | 13.2 / 29.5 |
| S5 | 10.1 / 18.5 | 14.3 / 21.3 | 16.0 / 31.1 |

- S1 phone baseline; S2 Focus Sentence; S3 selection extended by 1 char per step (typing is not the action; the Compose pill is
  NOT part of this harness, so S3 measures selection/layout work only); S4 672 dp (`wm density 320`); S5 896 dp (`wm density 240`).
- **Verdict: FAIL.** S1 100k is 11.9 / 20.9 (budget 8 / 12) and S1 300k is 13.8 (budget 12). S2, S4 and S5 are in the same range;
  S3 at 100k (8.2 / 10.3) is close to budget.
- Where the time goes (S1 100k, median of the 24 edits): the `Editable.insert` call itself 7.1 ms, animation 0.6, layout 1.9,
  draw 1.4, sync 0.1. With the restyler's TextWatcher detached the insert is still 6.6 ms, so the highlighter/restyler is
  about 1 ms of it and the rest is platform text-layout work inside `insert` (DynamicLayout reflow + TextView change handling).
  Disabling `MdEditable` makes an edit ~300 ms, so the broadcast-suppression design is doing its job.
- Reference numbers in editor-engine §2.1 (fast2r: 2.1 / 5.4 / 6.8 ms median at 20k / 100k / 300k) are about 2-3x lower than
  measured here for the same design. The emulator image and load are different; this is the first measurement of the
  finished app, so the real cost may also be spread over our watchers, undo manager and spans. Not resolved: needs a Perfetto
  / method trace to decide whether a local fix exists. Anything that changes the gutter span, `MdEditable` or the highlighter
  threading is architectural (STOP-AND-ASK, see the task's Pitfalls).

## 3. `highlighter.update` (debug timer, `MdPerf hl.update`, 24 edits per line)
| Size | p50 | p95 | max |
|---|---|---|---|
| 20k | 0.7-0.8 ms | 1.5-1.9 ms | 2-4 ms |
| 100k | 1.1-1.4 ms | 1.7-2.4 ms | 2-3.6 ms |
| 300k | 0.6-1.0 ms | 1.5-2.0 ms | 1.6-3.5 ms |

Budget p95 < 1 ms at 100k: **FAIL** (p95 about 2 ms; the `update` includes the first edits of each run, which are
still warming up).

## 4. Hang-room span cost (S4 at 300k, with vs without the document-wide gutter span)
With the span: median 13.2 ms. Without: median 17.3 ms. The span is **not** the bottleneck (removing it did not help; the
difference is within run-to-run noise, 12.2-14.2 ms with vs 17.0-21.0 without). S4 is no worse than S1 (13.2 vs 13.8).

## 5. Open time (warm, from the drawer, 5 alternating opens; `open.request->open.firstFrame`)
| Document | Median | Note |
|---|---|---|
| 100k | 753 ms before the fix, ~610 ms after | budget <= 1 s warm: **PASS**. First open of a process is slower (909 ms before the fix). |
| 300k | 4.3 s before the fix, ~3.5 s after | no budget; target <= 1.2 s: **not met**. |

`open.installed` is within 5-15 ms of `firstFrame`. The cost is the off-main `buildStyledDocument`: 100k = 0.35 s, 300k = 2.8 s
(after the fix). Measured before the fix: `SpannableStringBuilder.setSpan` of 28k spans took 3.0 s (9.3k spans: 0.37 s), so it is
quadratic. Fix applied (`StyledDocument.kt`): set the spans on a `SpannableString` and copy it into the builder in bulk (saves
about 0.9 s at 300k). Still superlinear: `specsForLines` (0.1 s at 100k -> 0.67 s at 300k) and the remaining setSpan cost. A
follow-up (not architectural, but not done here): reduce the per-span cost further or build the spans per chunk.
The loading placeholder (T20) shows within ~30 ms of the request.

## 6. Cold start (release build, `speed-profile`, 11 launches, first dropped)
- `pm art dump me.gcg.mdwriter`: `status=speed-profile reason=install-dm` (the bundled profile is installed).
- `am start -W` TotalTime: 100-151 ms (median ~118 ms).
- `Fully drawn` (via `ReportDrawnWhen`): 166-270 ms, median **217 ms** (budget <= 800 ms: **PASS**).
- Baseline profile module: **not needed** (median 217 ms). Not added.

## 7. Scrolling jank (300k document, debug build, speed-compiled, 10 flings)
878 frames, janky 2 (0.23 %), p50 18 ms, p90 21 ms, p95 21 ms, p99 22 ms. Target janky < 10 % **met**; p90 <= 20 ms missed by
1 ms (follow-up, not a STOP).

## 8. Fixes applied
- `StyledDocument.kt`: bulk span installation (open 300k 4.3 s -> 3.5 s; 100k 0.75 s -> 0.61 s).
- `PerfStats` + Restyler `hl.update` timer; `ReportDrawnWhen`; harness extras `focus`, `extendSelection`, `label`, `noHang`,
  `PARTS|` line (per-keystroke breakdown), `sample=20k`.

## 9. Run it on your phone (for the user; the agent does not do this)
1. `make devices`, then `make install-debug DEVICE=<phone-serial>` and
   `adb -s <phone-serial> shell cmd package compile -m speed -f me.gcg.mdwriter.debug`.
2. Push documents: `scripts/qa/gen-doc.sh 100000 > /tmp/big-100k.md && scripts/qa/push-doc.sh <phone-serial> /tmp/big-100k.md`
   (same for 300000).
3. Per-keystroke: `adb -s <phone-serial> shell am start -S -W -n me.gcg.mdwriter.debug/dev.mdwriter.debug.EditorPerfActivity
   --es sample 100k --ei perfEdits 24 --ez vary true --es label S1`, wait ~15 s, then
   `adb -s <phone-serial> logcat -d -s MDPERF | grep -E 'RESULT|PARTS'` and `logcat -s MdPerf | grep hl.update`.
   Extras: `--es focus sentence`, `--ez extendSelection true`, `--ez noHang true`. For S4/S5 use `wm density 320` / `240`
   and reset with `wm density reset`.
4. Cold start: `make install DEVICE=<phone-serial>` (uses your real key in `~/.config/mdwriter`; never uninstall afterwards,
   it deletes notes), then 10x `am force-stop me.gcg.mdwriter; am start -W -n me.gcg.mdwriter/.MainActivity` and read
   `logcat -d | grep "Fully drawn me.gcg.mdwriter"`.
5. Paste the RESULT lines into a new "Phone" column here. Remove the debug app with `make uninstall-debug DEVICE=<phone-serial>`
   (a separate app; the real notes are safe).
