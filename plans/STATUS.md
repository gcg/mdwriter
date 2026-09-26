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
