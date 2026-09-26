# Verified reference material (copy, don't re-derive)

| Path | What | Verified how |
|---|---|---|
| `build/` | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `core/markdown/build.gradle.kts` | Built 2026-09-25: `:app:assembleDebug :app:assembleRelease (R8) :core:markdown:test :app:testDebugUnitTest :app:lintDebug spotlessKotlinGradleCheck` all green |
| `Makefile` | The project Makefile (copy verbatim — **tabs matter**) | GNU make 3.81 dry-runs for every target; real runs on the Android 17 emulator (install, signature-mismatch handling, versionCode monotonicity, logcat, backup-notes) — see research/build.md §10 |
| `gitignore`, `editorconfig` | Repo root `.gitignore` / `.editorconfig` | `git check-ignore`, `spotlessCheck` |
| `markdown/` | Reference implementation of `:core:markdown` (package `mdwriter.markdown` → rename to `dev.mdwriter.markdown`) + test harnesses + spec resources | 57/57 curated cases; 641/652 CommonMark spec agreement; 0 fuzz mismatches over 9,000 edits; compiles under explicitApi (see markdown/README.md) |
| `fonts.md`, `scripts/fetch-fonts.sh` | The 12 iA Writer TTFs + OFL licence download | Script run 2026-09-25: 12 files, 1,303,688 bytes |
| `scripts/fetch-icons.sh` | 55 Material Symbols vector drawables (tint attr stripped) | Script run 2026-09-25: all 55 parse as XML |
| `bench/` | The throwaway editor benchmark app's sources (`FastEditable2.kt` = the MdEditable prototype, `MainActivity.kt` = FrameMetrics per-keystroke timing harness, `Md.kt` = synthetic document generator) + raw results | Emulator runs 2026-09-25 (research/editor-engine.md §2). Measurement code only — not production quality |
