# mdwriter: Build, Signing, Makefile and Install-to-Phone (track: build)

Verified 2026-09-25 on macOS arm64 with GNU make **3.81** (`/usr/bin/make`), AGP 9.3.3, Gradle 9.7.1, Kotlin 2.4.20, JBR 21.0.11, build-tools 36.0.0, adb 37.0.1, and the emulator `Pixel_10_Pro_XL` (Android 17, API 37).
Test project: `scratchpad/androidtest/` (NOT the repo). The previous agent's originals are kept alongside as `*.salvaged`.
Deliverables in `scratchpad/research/`:
- `Makefile.final`: the canonical Makefile. **Copy it verbatim** (tabs matter).
- `gitignore.final`
- `editorconfig.final`
- this report

Every claim is marked either **VERIFIED** (I ran it) or **UNVERIFIED** / *secondary source*.

---

## 0. TL;DR: decisions

| Topic | Decision | Confidence |
|---|---|---|
| Install mechanism | `make install` means: device preflight, then `./gradlew :app:installRelease`, then launch. It uses **Gradle's install task, not raw `adb install`**, because only Gradle also installs the baseline-profile `.dm`. Verified: `Installing APK 'app-release.apk, app-release.dm'`, and on the device `status=speed-profile reason=install-dm`. | high (VERIFIED) |
| Device selection | The Makefile always resolves exactly one serial (`DEVICE=` if given, else the single connected device) and runs Gradle with `ANDROID_SERIAL=<serial>` exported in the **same shell**. Without it, AGP installs onto *all* connected devices. | high (VERIFIED) |
| Signing | **(b) A dedicated per-project key**, generated once by `make keystore` (and automatically by `make install`) into `~/.config/mdwriter/release.jks` plus `~/.config/mdwriter/keystore.properties`. The key is PKCS12, RSA-4096, 100-year validity, dir 0700, files 0600, and `storeFile=release.jks` is relative. It lives outside the repo, and the user must **back it up**. Gradle finds it at the same default path, so Android Studio builds use the same key. If it is missing, the release build falls back to the debug key with a loud warning. | high (VERIFIED end to end) |
| Why not the debug key | `~/.android/debug.keystore` is machine-specific and silently regenerated on a new or reinstalled Mac. The next update then fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only fix is uninstall, which **deletes the notes** stored in internal `filesDir`. | high |
| versionCode | Release versionCode = **minutes since 2026-01-01T00:00Z at build time**. It comes from a Gradle `ValueSource` that is wired **lazily** through `androidComponents.onVariants { outputs.versionCode.set(provider) }`. The result: a fresh value on every build **and** the configuration cache is still reused (VERIFIED: 385099 became 385100 with `Configuration cache entry reused`). Debug keeps versionCode = 1. Override with `make install VERSION_CODE=<int>`. | high (VERIFIED) |
| versionName | `0.1.0+<git short sha>` via a lazy `providers.exec { git rev-parse --short=8 HEAD }`. | high (VERIFIED) |
| Launch | `cmd package resolve-activity --brief -a MAIN -c LAUNCHER <pkg> \| tail -n 1`, then `am start -W -n <component>`. The obvious `am start -a MAIN -c LAUNCHER -p <pkg>` **does not resolve**, because launcher filters lack `CATEGORY_DEFAULT` (VERIFIED). | high |
| Make compatibility | GNU make 3.81: no `.ONESHELL`, `$(file)`, `!=`, `::=`, `undefine`. Each multi-line recipe is a single bash invocation (`; \`). Shared logic lives in `define` fragments (`PICK_DEVICE`, `GRADLE_INSTALL`, `EXPLAIN_INSTALL_FAILURE`, `LAUNCH`) plus target-specific variables (`install: TASK := …`). | high (VERIFIED on 3.81) |
| Failure safety | No target ever uninstalls implicitly. A failed install leaves the app and its data untouched (VERIFIED: `firstInstallTime` and `lastUpdateTime` unchanged after a signature-mismatch failure). `make uninstall` refuses without `CONFIRM=yes`. | high (VERIFIED) |
| Release-notes backup over adb | Not possible for a release (non-debuggable) app: `run-as: package not debuggable` (VERIFIED), and `adb backup` excludes apps targeting API 31+ (docs). `make backup-notes` therefore handles the **debug** app only (VERIFIED) and prints the honest alternatives for release. | high |
| Sideloading policy 2026/27 | Google's developer-verification rollout does **not** affect ADB installs. The official FAQ says: "Apps installed using ADB won't require verification" and "The waiting period does not apply to ADB installs". `make install` keeps working. | high (primary source) |

---

## 1. Review of the salvaged Makefile (`androidtest/Makefile.salvaged`, 301 lines)

**What worked, and was kept.** The following all ran correctly and survive into `Makefile.final`:
- `help` via `## ` comments
- `doctor`
- `FIND_JDK`, which picks JDK 17–26 and skips Homebrew's JDK 27
- keystore generation, which is idempotent, refuses when only half the files exist, uses `-storepass:env` so the password never appears in `ps`, and applies `umask 077`
- friendly error messages for `INSTALL_FAILED_*`
- `emulator` (boot plus wait for `sys.boot_completed`)
- `pair` and `connect`
- `logcat --uid=<uids>` (survives restarts and covers release and debug)
- the `uninstall CONFIRM=yes` gate
- colours only on a TTY, respecting `NO_COLOR`
- `local.properties` generated as a file target

**What was broken or suboptimal, and was fixed:**

| # | Salvaged behaviour | Problem | Fix in Makefile.final |
|---|---|---|---|
| 1 | `assembleRelease` then raw `adb install -r app-release.apk` | Skips the baseline-profile `.dm`, so there is no AOT at install and startup is slower. The toolchain track verified that `installRelease` installs APK + `.dm`. | `./gradlew :app:installRelease` with the output tee'd to a temp log, then `EXPLAIN_INSTALL_FAILURE` greps it. VERIFIED: the AGP messages contain the raw `INSTALL_FAILED_*` codes. |
| 2 | `VERSION_CODE` computed in the Makefile and passed as `-Pmdwriter.versionCode=$(minutes)` | Every build changes a `-P` value, which is a configuration-cache input (VERIFIED message: `configuration cache cannot be reused because Gradle property 'mdwriter.keystore' has changed`), so config runs every time. Android Studio builds also got a different (git-time) code. | A lazy `ValueSource` in Gradle (see §3). The Makefile passes `-Pmdwriter.versionCode` only when the user sets `VERSION_CODE=`. |
| 3 | `_launch` used `monkey -p PKG -c LAUNCHER 1` | Noisy, returns odd statuses, and is a stress-test tool. | `resolve-activity` plus `am start -W -n` (V7). |
| 4 | `run: install` (run meant build + install + launch) | The task spec says `install` launches. | `install` = build + install + launch. `run` = launch only. Same for `install-debug` / `run-debug`. |
| 5 | Salvaged Gradle left release **unsigned** when no keystore existed | With an unsigned release the `installRelease` task does not exist (UNVERIFIED detail; AGP only creates install tasks for signed variants), and the task asks for a debug-key fallback. | Falls back to `signingConfigs.getByName("debug")` with a loud `logger.warn` banner (VERIFIED, V1). |
| 6 | `keystore.properties` stored an **absolute** `storeFile=/Users/…` | Breaks when the backup is restored on another Mac or under another username. | `storeFile=release.jks`, resolved relative to the properties file's directory (VERIFIED, V5 and V6). |
| 7 | Makefile used `$(XDG_CONFIG_HOME)/mdwriter` while Gradle hard-coded `~/.config/mdwriter` | If `XDG_CONFIG_HOME` is set, Studio and make would use different keys, which means a signature mismatch. | One path, `$(HOME)/.config/mdwriter`, in both. The Makefile also passes `-Pmdwriter.keystore=<path>` explicitly. |
| 8 | SDK precedence `ANDROID_HOME > … > local.properties` | Gradle gives `local.properties` precedence, so make's adb and Gradle's adb could come from different SDKs. | `local.properties sdk.dir > ANDROID_HOME > ANDROID_SDK_ROOT > OS default`. |
| 9 | JDK order preferred Studio JBR 25 over JDK 21 | The real project pins the daemon JVM to 21 (`gradle/gradle-daemon-jvm.properties`), so launching with 25 spawns a second JVM. | `$JAVA_HOME` (if 17–26), then `java_home -v 21 --failfast`, then Studio JBR, then `java_home -v 17`, then Homebrew `@21`/`@17`, then Linux paths. |
| 10 | `device-check` was a separate prerequisite target | A separate recipe shell cannot `export ANDROID_SERIAL` to the Gradle call, so with 2 devices plus `DEVICE=` it was fine, but with 1 device AGP would still install on "all" of them. It also did not check the API level for the no-`DEVICE` path before building. | A `PICK_DEVICE` fragment runs in the same shell as Gradle and checks for 0 or >1 devices, `unauthorized`, `offline`, an API < 36 message, and then exports `ANDROID_SERIAL` (VERIFIED with a fake adb, V11). |
| 11 | `test` / `lint` hard-coded `:app:testDebugUnitTest` / `:app:lintDebug` | Misses `:core:markdown`. | Aggregate tasks `test` and `lint` (VERIFIED: `:app:test` runs `testDebugUnitTest`, `:app:lint` runs `lintDebug`). `check` = `spotlessCheck lint test :app:assembleRelease`. |
| 12 | `help` descriptions contained `$(APP_ID_DEBUG)` / `$(AVD)` | `help` greps the raw text, so the literal `$(…)` was printed. | Plain text only in `## ` descriptions. |

---

## 2. Signing policy

### 2.1 Options compared

| | (a) Release signed with `~/.android/debug.keystore` | **(b) Dedicated key in `~/.config/mdwriter/` (CHOSEN)** | (c) Keystore committed to the repo |
|---|---|---|---|
| Setup | none | `make keystore`, once (auto-run by `make install`/`make apk`) | once |
| Survives macOS reinstall / new Mac | **No**. AGP regenerates the debug key when it is missing, the signature changes, updates fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only fix is **uninstall = notes deleted**. | Yes, **if the two files are backed up** and restored. | Yes |
| Survives `git clean -fdx` / re-clone | yes | yes (outside repo) | yes |
| Security | The debug key is effectively public (password `android`). | Private. | The repo is `github.com/gcg/mdwriter`, possibly public, which would publish the signing key. Rejected. |
| Studio builds use the same key | yes | yes (Gradle default path) | yes |

**Decision: (b).** The whole product promise is "your notes stay on your phone". The one thing that can silently destroy them is a signing-key change that forces an uninstall. A key that can be backed up and restored anywhere removes that risk for the cost of a single command.
- Verified fact (V1): the debug key here is `CN=Android Debug`, SHA-256 `ef2e7f86…`.
- Verified fact (V8): installing over it with another key fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package … signatures do not match newer version; ignoring!`

### 2.2 Files and format
```
~/.config/mdwriter/            (chmod 700)
├─ release.jks                 (chmod 600)  PKCS12, alias "mdwriter", RSA 4096, validity 36500 d, CN=mdwriter
└─ keystore.properties         (chmod 600)
     storeFile=release.jks          # relative -> resolved against this directory
     storePassword=<32 random [A-Za-z0-9]>
     keyAlias=mdwriter
     keyPassword=<same>             # PKCS12 requires keyPassword == storePassword
```
- Generation command, run by `make keystore`:
  ```
  MDW_KS_PW="$pw" "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -keystore release.jks -storetype PKCS12 -alias mdwriter -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=mdwriter" -storepass:env MDW_KS_PW -keypass:env MDW_KS_PW
  ```
  keytool prints `Generating 4,096 bit RSA key pair and self-signed certificate (SHA384withRSA) with a validity of 36,500 days` (VERIFIED).
- Gradle lookup order (first existing file wins):
  1. `-Pmdwriter.keystore=<path>` (the Makefile always passes `$(KEYSTORE_DIR)/keystore.properties`)
  2. `<repo>/keystore.properties` (git-ignored; for people who want it in the checkout)
  3. `~/.config/mdwriter/keystore.properties`
- **Backup** (README text in §7): copy both files to a password manager (as file attachments) or an encrypted USB stick. Time Machine also covers `~/.config`. `make keystore-info` prints the paths and the SHA-256 fingerprint, so the user can check that a restored key is the right one.
- **Lost key while the app is installed**: first export the notes from inside the app (or keep them in the linked SAF folder), then `make uninstall CONFIRM=yes`, delete the half-restored files, and run `make install` (which creates a new key).
- **Escape hatch, UNVERIFIED and not needed for v1**: APK Signature Scheme v3 key rotation. It works only while the *old* key still exists.
  - Syntax: `apksigner rotate --out lineage --old-signer --ks old.jks --new-signer --ks new.jks`, then `apksigner sign --ks old.jks --next-signer --ks new.jks --lineage lineage app.apk`.
  - The default v3.1 block targets API 33+, which covers minSdk 36.
  - AGP has no DSL for lineage, so this would need a post-build re-sign plus `adb install-multiple apk dm`.
  - Source: https://developer.android.com/tools/apksigner

### 2.3 Configuration-cache behaviour (VERIFIED, V5)
- The keystore is looked up with `File.isFile` and read via `providers.fileContents(...)`. Gradle records the file-system check as a CC input.
- Creating the key after a debug-signed build invalidates the cache automatically. Observed: `Calculating task graph as configuration cache cannot be reused because the file system entry '../ks-cc/keystore.properties' has been created.`
- The next APK is signed `CN=mdwriter`, and its SHA-256 matches `keytool -list -v`.
- Caveat: the debug-key fallback banner is printed at *configuration* time. It is **not** shown on a CC hit, or with `-q`. That is acceptable, because `make install` and `make apk` depend on `keystore`, so the fallback only happens in CI, in `make check` without a key, or in Studio before the first `make`.

---

## 3. versionCode strategy

Constraint: Android rejects a lower versionCode (`INSTALL_FAILED_VERSION_DOWNGRADE`, VERIFIED V10). An equal versionCode is accepted as a replace (VERIFIED: debug reinstall at versionCode 1 kept its data, V9). Downgrading a non-debuggable app on a user build is not possible without uninstalling (AOSP behaviour; **UNVERIFIED here**). So the code must never decrease, whichever commit is checked out.

| Strategy | Monotonic? | CC-friendly? | Verdict |
|---|---|---|---|
| `git rev-list --count HEAD` | **No**: checking out an older commit, a rebase or a squash lowers it, which means a downgrade failure and the uninstall trap. Dirty trees don't bump it. | yes | rejected |
| HEAD commit time (`git log -1 --format=%ct`) | **No** for older checkouts or `git bisect`. | yes | rejected (it was the salvaged fallback) |
| Build time computed by the Makefile, passed as `-P` | yes | **No**: the `-P` value changes every build, so configuration re-runs every build. Studio builds differ. | rejected |
| `System.currentTimeMillis()` directly in the build script | yes | **Wrong**: it is not tracked, so a CC hit reuses a stale code. | rejected |
| **Build time via a lazy `ValueSource` (minutes since 2026-01-01Z)** | yes. Rolling back to an old commit still installs as an upgrade. | **yes**: VERIFIED `Configuration cache entry reused` with a new value. | **CHOSEN** |

- **Range**: minutes since 2026-01-01Z = 385 099 on 2026-09-25. The maximum allowed versionCode, 2 100 000 000 (limit from memory, UNVERIFIED this session), is reached around year 5990. Seconds would overflow around 2092. `yyMMddHHmm` (2609251530) already overflows.
- **Cost** (VERIFIED, V2): on a no-code-change release build the fresh code re-runs manifest, lint-vital, R8 and packaging, which takes about 18 s instead of about 1 s. When code changed, R8 runs anyway, so this is irrelevant in practice. Debug is fixed at 1, so debug and Compose incremental builds are unaffected.
- The versionCode is shared across Macs: builds on two machines with the same key interleave correctly as long as their clocks are sane. If a clock was in the future, use `make install VERSION_CODE=<bigger>`.

---

## 4. Gradle snippets for `app/build.gradle.kts` (VERIFIED on AGP 9.3.3; Spotless/ktlint-clean)

Merge these into the toolchain report's §8.5 file.
1. Replace `versionCode = 1` / `versionName = "0.1.0"` (keep them as the debug defaults).
2. Replace `signingConfig = signingConfigs.getByName("debug")` in `release`.
3. Add the top-level blocks.

```kotlin
import java.util.Properties

plugins { /* unchanged: android.application + kotlin.compose */ }

// ---------------------------------------------------------------- versioning
// Release versionCode = minutes since 2026-01-01T00:00:00Z, computed when the manifest task RUNS
// (a ValueSource wired lazily through the Variant API), so it is fresh on every build and the
// configuration cache is still reused. Override: -Pmdwriter.versionCode=<int>.
abstract class BuildTimeVersionCode : ValueSource<Int, ValueSourceParameters.None> {
    override fun obtain(): Int = ((System.currentTimeMillis() / 1000L - 1_767_225_600L) / 60L).toInt()
}

val releaseVersionCode: Provider<Int> =
    providers
        .gradleProperty("mdwriter.versionCode")
        .map { it.toInt() }
        .orElse(providers.of(BuildTimeVersionCode::class.java) {})

val gitShortSha: Provider<String> =
    providers
        .exec {
            commandLine("git", "rev-parse", "--short=8", "HEAD")
            isIgnoreExitValue = true
        }.standardOutput.asText
        .map { it.trim() }

// ---------------------------------------------------------------- release signing
// First existing file wins:
//   -Pmdwriter.keystore=<path>  >  <repo>/keystore.properties  >  ~/.config/mdwriter/keystore.properties
// storeFile may be relative (resolved against the properties file's directory).
val keystorePropsFile: File? =
    listOfNotNull(
        providers.gradleProperty("mdwriter.keystore").orNull?.let(::File),
        rootProject.file("keystore.properties"),
        providers.systemProperty("user.home").orNull?.let { File(it, ".config/mdwriter/keystore.properties") },
    ).firstOrNull { it.isFile }

val keystoreProps: Properties? =
    keystorePropsFile?.let { f ->
        Properties().apply {
            load(
                providers
                    .fileContents(layout.projectDirectory.file(f.absolutePath))
                    .asText
                    .get()
                    .reader(),
            )
        }
    }

android {
    // ... namespace, compileSdk { version = release(37) } ...
    defaultConfig {
        // ... applicationId, minSdk { version = release(36) }, targetSdk { version = release(37) } ...
        versionCode = 1 // debug stays at 1 (keeps debug builds incremental); release is set below
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                val sf = File(keystoreProps.getProperty("storeFile"))
                storeFile = if (sf.isAbsolute) sf else File(keystorePropsFile!!.parentFile, sf.path)
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = true
            }
            signingConfig =
                if (keystoreProps != null) {
                    signingConfigs.getByName("release")
                } else {
                    logger.warn(
                        """
                        |
                        |!!! mdwriter: NO RELEASE KEYSTORE FOUND - the release APK is signed with the DEBUG key.
                        |!!! A phone that has this build can later only switch keys by UNINSTALLING (= losing notes).
                        |!!! Run `make keystore` BEFORE the first install on a real phone.
                        |
                        """.trimMargin(),
                    )
                    signingConfigs.getByName("debug")
                }
        }
    }
    // ... compileOptions, buildFeatures, lint, testOptions, dependenciesInfo, packaging as in toolchain §8.5 ...
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(releaseVersionCode)
            output.versionName.set(gitShortSha.map { sha -> if (sha.isEmpty()) "0.1.0" else "0.1.0+$sha" })
        }
    }
}
```

Notes:
- **Do not** `.get()` `releaseVersionCode` or `gitShortSha` at configuration time, for example in a `println`. Doing so turns them into CC inputs, and the cache would be invalidated on every build.
- The output was verified in `aapt2 dump badging`: `versionCode='385100' versionName='0.1.0+01fb89b0'`. `output-metadata.json` carries the same values, and `dumpsys package` on the device shows them after install.
- Optional (UNVERIFIED): `git describe --always --dirty --abbrev=8` would add `-dirty` for uncommitted builds.
- `dependenciesInfo { includeInApk = false }` is kept from toolchain §8.5, since there is no Play dependency blob in a sideloaded app.

---

## 5. Final Makefile: `research/Makefile.final` (369 lines; copy verbatim to the repo root)

VERIFIED on GNU make 3.81 against the androidtest project. `diff` shows it is identical to the tested `androidtest/Makefile`. `make -n` succeeds for every target. The real runs are listed in §10.

### 5.1 Targets

| Target | What it does | Verified |
|---|---|---|
| `help` (default) | Lists targets from their `## ` comments, plus the variables. | yes |
| `doctor` | Host and make version; the JDK chosen (and a note if `$JAVA_HOME` was skipped because it is not 17–26); SDK path; `platforms/android-37*`; build-tools 36.0.0; adb version; AVDs; Gradle version; applicationId; signing-key status; **every device with its Android version, API level and ok / TOO OLD**. | yes |
| `install` | `env-check` → `keystore` (created on the first run) → `PICK_DEVICE` → `./gradlew --console=plain :app:installRelease` (APK + `.dm`) → prints installed versionName/versionCode → launches. Failures are explained, and there is never an uninstall. | yes (emulator) |
| `install-debug` | Same with `:app:installDebug` and `<appId>.debug` (a separate app with separate data). | yes |
| `run` / `run-debug` | Launch only (no build). | yes |
| `apk` | Builds the signed release APK and prints its absolute path, size, signer DN and SHA-256. | yes |
| `debug-apk` | Debug APK path. | dry-run |
| `test` | `./gradlew test` (all modules; on `:app` that is `testDebugUnitTest`). | yes |
| `lint` | `./gradlew lint` (`:app:lintDebug`, plus `:core:markdown:lint` in the real project). | yes |
| `format` | `spotlessApply`. | yes |
| `check` | `spotlessCheck lint test :app:assembleRelease`. | yes (28 s warm) |
| `clean` | `gradlew clean` and `rm -rf .kotlin`. Never touches the key or the phone. | dry-run |
| `keystore` | Creates the key once. A no-op if it exists. **Refuses** if only one of the two files exists. | yes |
| `keystore-info` | Paths, alias, validity, SHA-256. | yes |
| `devices` | `adb devices -l` plus Android version and API per device. | yes |
| `pair HOST=ip:port CODE=nnnnnn` | `adb pair HOST CODE` (adb 37 syntax: `pair HOST[:PORT] [PAIRING CODE]`, VERIFIED via `adb help`). Prints usage and the phone path when args are missing. | usage path yes; real pairing UNVERIFIED (no phone) |
| `connect [HOST=ip:port]` | `adb connect HOST`; without HOST, runs `adb mdns services` and lists devices. | yes (no-HOST path) |
| `emulator [AVD=…]` | Boots the AVD in the background and waits up to 6 min for `sys.boot_completed`. A no-op if one is running. | "already running" path yes |
| `logcat` | Resolves the uids of release and debug (`cmd package list packages -U`), then `adb logcat -v color --uid=<uids>`. Survives app restarts. | yes |
| `uninstall` | **Refuses** without `CONFIRM=yes` and prints a data-loss warning. | yes (both paths) |
| `uninstall-debug` | Uninstalls the debug app. | yes |
| `backup-notes` | Debug app only: `adb exec-out run-as <appId>.debug tar -cf - files \| tar -xf - -C notes-backup-<ts>/`. When the debug app is not installed, it explains why release data cannot be read and what to do instead. | yes |

Variables:
- `DEVICE=<serial>` (default `$ANDROID_SERIAL`)
- `HOST`, `CODE`
- `AVD` (default `Pixel_10_Pro_XL`)
- `EMU_FLAGS`
- `CONFIRM=yes`
- `VERSION_CODE=<int>`
- `KEYSTORE_DIR` (default `~/.config/mdwriter`; I overrode it for every test, so **the user's real `~/.config/mdwriter` was never created**)
- `GRADLE_FLAGS`
- `NO_COLOR`

### 5.2 Design points a plan author must keep
1. **`SHELL := /bin/bash`**. It is needed for `PIPESTATUS` in `GRADLE_INSTALL` (`./gradlew … | tee log | grep --line-buffered -v '^> Task '`, then `rc=${PIPESTATUS[0]}`).
2. **The device check runs before the Gradle build** (fail fast) and in the same shell as Gradle, so `export ANDROID_SERIAL` reaches AGP.
3. `APP_ID` is `sed`-extracted from `applicationId = "…"` in `app/build.gradle.kts` (one source of truth; the fallback is `dev.mdwriter`). **Keep `applicationId` as a literal string on one line.**
4. `EXPLAIN_INSTALL_FAILURE` matches `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, `_VERSION_DOWNGRADE`, `_OLDER_SDK`/`minSdk`, `_INSUFFICIENT_STORAGE`, `_USER_RESTRICTED`/`_ABORTED`, and `INSTALL_PARSE_FAILED*`. AGP 9.3.3 prints the raw PackageManager text, for example `Error: INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package dev.mdwriter.bt signatures do not match newer version; ignoring!` (VERIFIED).
5. `doctor`'s `while read` loop calls `adb shell … </dev/null`. Otherwise adb swallows the loop's stdin.
6. The colour escapes are make variables (`\033[…m`) that are empty unless stderr is a TTY and `NO_COLOR` is unset.
7. GNU make 4.x (Linux) is expected to work, since only 3.81 features are used. **UNVERIFIED**: no gmake on this Mac.

### 5.3 Timings measured (emulator, M-series Mac)
- First `make install` after the other builds: 3–5 s when outputs are up to date.
- No-code-change release rebuild with a fresh versionCode: about 18 s.
- Cold first release build: about 65 s (the toolchain track measured cold debug at 56 s).
- `make check` warm: 28 s.

---

## 6. `.gitignore` and `.editorconfig`

### 6.1 `.gitignore` (`research/gitignore.final`, VERIFIED with `git check-ignore`)
```
# Gradle / Kotlin build state
.gradle/
.kotlin/
build/

# Machine-specific (generated by `make` from ANDROID_HOME / the default SDK path)
local.properties

# Signing secrets - the real key lives in ~/.config/mdwriter/, never in the repo
*.jks
*.keystore
keystore.properties

# Output of `make backup-notes` (personal notes - never commit)
notes-backup-*/

# IDE / OS
.idea/
*.iml
captures/
.externalNativeBuild/
.cxx/
.DS_Store
```
**Bug in toolchain.md §8.8 (VERIFIED):** the line `.kotlin/          # Kotlin 2.x session dir` does **not** ignore `.kotlin/`. In `.gitignore`, `#` starts a comment only at the beginning of a line, so the comment becomes part of the pattern (`git check-ignore` returned 1). Put comments on their own lines, as above.

### 6.2 `.editorconfig` (`research/editorconfig.final`; `spotlessCheck` passes with it, VERIFIED)
```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.{kt,kts}]
ktlint_code_style = ktlint_official
ktlint_function_naming_ignore_when_annotated_with = Composable
max_line_length = 120

[*.{yml,yaml,json,toml,xml}]
indent_size = 2

# Make recipes MUST be indented with a real tab.
[{Makefile,*.mk}]
indent_style = tab
indent_size = 8

# Markdown: two trailing spaces are a hard line break.
[*.md]
trim_trailing_whitespace = false
```
The Kotlin section is identical to toolchain §8.7. The additions are non-Kotlin only. The `Makefile` tab rule matters: an editor that expands tabs to spaces breaks every recipe (`*** missing separator`).

---

## 7. README section, ready to paste

````markdown
## Install on your phone

mdwriter runs on **Android 16 or 17**. You install it from this Mac with one command; no Play Store,
no account. Your notes are stored inside the app on the phone.

### 1. One-time setup on the Mac
- Install **Android Studio** (it brings the Android SDK, `adb` and a Java runtime). Nothing else is
  needed; you do not have to put `adb` on your PATH.
- Check everything: `make doctor`

### 2. One-time setup on the phone
1. **Enable Developer options:** Settings > About phone > tap **Build number** 7 times, enter your
   PIN ("You are now a developer!").
2. Open **Settings > System > Developer options**.

Then pick **one** way to connect:

**USB cable (simplest)**
1. In Developer options, turn on **USB debugging**.
2. Plug the phone into the Mac. On the phone, accept **Allow USB debugging?**; tick
   **Always allow from this computer**.

**Wi-Fi (no cable; phone and Mac on the same Wi-Fi network)**
1. In Developer options, tap **Wireless debugging**, turn it on, and allow it on this network.
2. Tap **Pair device with pairing code**. The phone shows a 6-digit code and an
   *IP address & port*.
3. On the Mac: `make pair HOST=192.168.1.23:37123 CODE=123456` (use the values from the phone).
4. The phone normally connects by itself a few seconds later (`make devices` shows it). If it
   does not: `make connect HOST=<IP address & port shown on the main Wireless debugging screen>`
   (this port is different from the pairing port).
   Pairing is remembered; next time just turn Wireless debugging on.

### 3. Install / update
```sh
make install        # builds, installs (or updates) and opens mdwriter; your notes are kept
```
Run the same command after every `git pull`: each build gets a higher version number, so it is
always an in-place update. Several phones connected? `make install DEVICE=<serial>` (see `make devices`).
You can turn Developer options off again afterwards; the app keeps working.

### 4. Your signing key: back it up now
The first `make install` creates `~/.config/mdwriter/release.jks` and
`~/.config/mdwriter/keystore.properties`. **Every future update must be signed with this key.**
Copy both files to your password manager or an encrypted backup (`make keystore-info` shows where
they are and their fingerprint). On a new Mac, put them back in `~/.config/mdwriter/` *before*
running `make install`.

### Troubleshooting
| Message | Meaning / fix |
|---|---|
| `no Android device connected` | USB: cable/port, USB debugging on, accept the prompt. Wi-Fi: Wireless debugging on, same network, `make pair` again. |
| device `unauthorized` | Unlock the phone and accept **Allow USB debugging?**. No prompt? Developer options > **Revoke USB debugging authorizations**, unplug, replug. |
| `2 devices connected - choose one` | `make install DEVICE=<serial>` (serials from `make devices`). |
| `needs Android 16 (API 36) or newer` / `INSTALL_FAILED_OLDER_SDK` | The phone runs Android 15 or older; mdwriter supports Android 16 and 17 only. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (signed with a DIFFERENT key) | The app on the phone was signed with another key (new Mac, lost `~/.config/mdwriter`). **Nothing was changed on the phone.** Restore your backed-up key files and run `make install` again. Only if the key is truly lost: export your notes first, then `make uninstall CONFIRM=yes && make install` (uninstalling deletes the notes inside the app). |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The phone has a newer build (Mac clock wrong?). `make install VERSION_CODE=<bigger number>`. |
| `INSTALL_FAILED_USER_RESTRICTED` | Accept the prompt on the phone; on Xiaomi/Oppo/Vivo also enable Developer options > **Install via USB**. |
| Samsung: USB commands ignored | Settings > Security and privacy > **Auto Blocker** blocks USB commands; turn it off while installing. |
| `offline` device | Unplug/replug, or restart adb: `~/Library/Android/sdk/platform-tools/adb kill-server`. |
| `no JDK 17-26 found` | Install Android Studio, or `brew install openjdk@21`. Gradle cannot run on Java 27. |
````

Notes behind the README:
- **Menu paths.** "Settings > About phone > Build number" ×7 and "Settings > System > Developer options > Wireless debugging" for Android 16+ are from https://developer.android.com/studio/debug/dev-options. The pairing flow is from https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi.
- **Pairing label.** The adb docs call the item "Pair using pairing code". The Pixel UI label is "Pair device with pairing code" (from memory, UNVERIFIED on Android 17 hardware). The README uses the UI wording.
- **Auto-connect.** The docs say "The device and the workstation will automatically connect when they are on the same network". adb 37.0.1 here reports `mdns_enabled: true`, and `ADB_MDNS_AUTO_CONNECT` defaults to `adb-tls-connect` (VERIFIED via `adb server-status` and `adb help`). So `make connect` is only a fallback.
- **Samsung Auto Blocker.** From secondary sources (UNVERIFIED on hardware), and the row is harmless. The Android 16 "Advanced Protection" mode may restrict ADB/sideloading according to secondary reports (UNVERIFIED). I left it out of the README table; add a row only if confirmed.

---

## 8. Error-code reference (what `EXPLAIN_INSTALL_FAILURE` covers)

| Code | Cause | Data on phone | Makefile advice |
|---|---|---|---|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | Signing cert differs from the installed app | untouched (VERIFIED) | restore key from backup; never auto-uninstall |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | new versionCode < installed (VERIFIED message: `Downgrade detected: Update version code 5 is older than current 385110`) | untouched | fix clock / `VERSION_CODE=` |
| `INSTALL_FAILED_OLDER_SDK` | device API < minSdk 36 (normally caught earlier by `PICK_DEVICE`: `runs Android 15 (API 35)`, VERIFIED with fake adb) | n/a | needs Android 16+ |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | full storage | untouched | free space |
| `INSTALL_FAILED_USER_RESTRICTED` / `_ABORTED` | user declined, or OEM "Install via USB" off | untouched | accept prompt / enable setting |
| `INSTALL_PARSE_FAILED_NO_CERTIFICATES` | unsigned/corrupt APK | untouched | `make clean install` |

---

## 9. Backing up notes (`backup-notes`): the honest options

- **Release app over adb: impossible by design.**
  - `adb shell run-as dev.mdwriter.bt ls` returns `run-as: package not debuggable` (VERIFIED).
  - `adb backup` excludes app data for apps targeting API 31+ unless `android:debuggable=true` (https://developer.android.com/about/versions/12/behavior-changes-12#adb-backup-restriction).
  - A debuggable release is not acceptable (performance, security).
- **Debug app:** `make backup-notes` copies `files/` of `<appId>.debug` via `run-as … tar`. VERIFIED: I wrote `files/library/Hello.md`, reinstalled the debug app (it persisted), and backed it up to `notes-backup-<ts>/files/library/Hello.md`.
- **What the plan should do for release notes:**
  1. **Primary:** an in-app **"Export all notes"** action (a zip via `ACTION_CREATE_DOCUMENT`) somewhere unobtrusive, such as the file sidebar's overflow. This is the only path that works without a computer. It belongs to the storage/UI tracks.
  2. The optional **linked SAF folder** (already decided by the platform track). If the user links e.g. `Documents/mdwriter`, `adb pull /sdcard/Documents/mdwriter` backs it up, and uninstalling does not delete it.
  3. **Fact for the storage track (VERIFIED on API 37):**
     - The adb shell user is in group `ext_data_rw`, so it **can** read `/sdcard/Android/data/<pkg>/` (app-specific *external* storage, `getExternalFilesDir()`).
     - If the default library lived there instead of internal `filesDir`, `adb pull /sdcard/Android/data/dev.mdwriter/files/library` would back up release notes.
     - Trade-offs: that storage is still deleted on uninstall, and other apps cannot read it on Android 11+.
     - This is an open question for the storage track, not a decision here.
  4. **Idea, UNVERIFIED:** a broadcast receiver protected by `android:permission="android.permission.DUMP"` (only shell/system holds it) that writes a zip into `Download/` via MediaStore, triggered with `adb shell am broadcast -n dev.mdwriter/.ExportReceiver`, followed by `adb pull`. It is not recommended for v1.

---

## 10. Verification log (all on 2026-09-25, test app id `dev.mdwriter.bt`, since uninstalled)

| # | What | Result |
|---|---|---|
| V1 | New app/build.gradle.kts (ValueSource versionCode + keystore lookup), `:app:assembleRelease`, no keystore | BUILD SUCCESSFUL 1m05s; loud `!!! mdwriter: NO RELEASE KEYSTORE FOUND` warning printed; APK signed by `CN=Android Debug` SHA-256 ef2e7f86...; aapt2: versionCode=385099 versionName=0.1.0+01fb89b0 |
| V2 | Same command again ~1 min later | `Configuration cache entry reused`, versionCode=385100 (fresh), 18 s (manifest->R8->package rerun; that is the cost of a fresh versionCode on a no-op build) |
| V3 | `make` (help), `make doctor` on GNU make 3.81 | rc 0; doctor lists JBR 21.0.11, SDK, android-37.0, build-tools 36.0.0, adb 37.0.1, AVD, Gradle 9.7.1, `emulator-5554 ... Android 17 (API 37) ok` |
| V4 | `make keystore` x2 (KEYSTORE_DIR=scratch), then with only release.jks present | 1st creates PKCS12 RSA-4096 key (`storeFile=release.jks`, both files 0600, dir 0700); 2nd is a no-op (md5 unchanged); half-present -> refuses with restore-from-backup message, rc 2 |
| V5 | Build with `-Pmdwriter.keystore=<missing>` -> debug-key; `make keystore`; rebuild with the SAME -P value | `configuration cache cannot be reused because the file system entry '../ks-cc/keystore.properties' has been created` -> APK now `CN=mdwriter`, SHA-256 equals keytool fingerprint. (Note: `-q` hides the logger.warn fallback banner.) |
| V6 | `make install` (real, emulator API 37) | `Installing APK 'app-release.apk, app-release.dm'`, `Installed on 1 device.`; dumpsys `status=speed-profile reason=install-dm`; versionCode 385106 |
| V7 | `am start -p PKG -a MAIN -c LAUNCHER` | FAILS (`unable to resolve Intent`: launcher filters lack CATEGORY_DEFAULT). Fixed LAUNCH: `cmd package resolve-activity --brief ... | tail -n 1` then `am start -W -n <comp>` -> `Status: ok`, `LaunchState: COLD`, TotalTime 337 ms; non-installed pkg -> `No activity found` (detected) |
| V8 | `make install` with a DIFFERENT key over the installed app | AGP: `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package ... signatures do not match newer version; ignoring!`, BUILD FAILED; Makefile prints the "NOT INSTALLED ... different key ... notes untouched" advice; `firstInstallTime`/`lastUpdateTime` unchanged -> AGP never uninstalls. `-P` value change is a CC input ("Gradle property 'mdwriter.keystore' has changed") |
| V9 | `make install-debug`; `run-as … echo "# Hello" > files/library/Hello.md`; `make install-debug` again (same versionCode 1); `make backup-notes` | Installed and launched `dev.mdwriter.bt.debug`. The file survived the reinstall. Backup: `Saved debug-app notes to notes-backup-…/ (2 files)` with the correct content. |
| V10 | `make install` again (new versionCode), then `make install VERSION_CODE=5` | Upgrade: `versionCode 385110`, `lastUpdateTime` changed, `firstInstallTime` kept, 4.6 s. Downgrade: `INSTALL_FAILED_VERSION_DOWNGRADE: Downgrade detected: Update version code 5 is older than current 385110`, and the Makefile printed its advice. |
| V11 | Fake adb (`ADB_BIN=` override) scenarios: none / two / unauthorized / API 35 device; `make uninstall` without CONFIRM; `make pair` without args; `make run DEVICE=zzz` | Each prints the intended message and exits non-zero **before** any Gradle work (`2 devices connected - choose one: make install DEVICE=<serial>`, `Pixel 7 (OLD1) runs Android 15 (API 35)…`, `Refusing…`, and so on). |
| V12 | `make logcat` (6 s alarm) while relaunching the app | `Logs of dev.mdwriter.bt/dev.mdwriter.bt.debug (uid 10232,10233)`, and the app's lines were streamed. `logcat --uid` exists on API 37. |
| V13 | `make apk`, `make devices`, `make emulator` (running), `make connect` (no HOST), `make test lint`, `make format`, `make check` | All rc 0. `apk` printed the path, `1.9M`, `CN=mdwriter` and the SHA-256. `check` first failed on my own snippet's ktlint formatting, and passed after `make format` (the snippet in §4 is the formatted version). |
| V14 | `make -n` for every target | All parse on make 3.81. |
| V15 | Removed `local.properties`, then `make env-check` | `Created local.properties (sdk.dir=/Users/gcg/Library/Android/sdk)` |
| V16 | Final `.gitignore` with `git check-ignore`; final `.editorconfig` with `spotlessCheck` | All sample paths are ignored and spotless passes. The toolchain §8.8 inline-comment `.kotlin/` line is NOT ignored (bug confirmed). |
| V17 | `adb shell id` and `ls /sdcard/Android/data/<pkg>` | shell uid 2000 is in `ext_data_rw` and can list other apps' external app dirs. |
| V18 | `make uninstall CONFIRM=yes`, `make uninstall-debug` | `Success` twice. The emulator no longer has any `mdwriter` package. The emulator was already running before I started (another agent's), so I left it running. |

---

## 11. Suggested plan tasks (for Sonnet executors)

1. **Build files.** Apply §4 to `app/build.gradle.kts` (keep the literal `applicationId = "dev.mdwriter"` on one line; the Makefile parses it). Acceptance:
   - `./gradlew :app:assembleRelease` twice in a row prints `Configuration cache entry reused` the second time.
   - `aapt2 dump badging` shows a versionCode about equal to `(now_epoch_s - 1767225600)/60`.
2. **Makefile.** Copy `research/Makefile.final` byte-for-byte (tabs). Acceptance:
   - `make`, `make doctor`, `make -n install` succeed.
   - With `KEYSTORE_DIR=$(mktemp -d)`: `make keystore` twice creates the files once.
   - `make apk KEYSTORE_DIR=…` prints `CN=mdwriter`.
   - Agents must **not** run `make keystore` / `make install` without `KEYSTORE_DIR=<scratch>`. Only the user creates the real `~/.config/mdwriter` key.
3. **Repo hygiene.** Add `research/gitignore.final` as `.gitignore` and `research/editorconfig.final` as `.editorconfig`.
4. **README.** Paste §7.
5. **Emulator smoke** (optional; needs a running emulator): `make emulator && make install KEYSTORE_DIR=<scratch> && make logcat` (Ctrl-C), then `make uninstall CONFIRM=yes`.
6. **Later task** (storage/UI tracks): the in-app "Export all notes" zip (§9).

---

## 12. Risks and open questions

1. **Key backup is a human step.** If the user never backs up `~/.config/mdwriter/`, a Mac loss eventually forces an uninstall. Mitigations: the loud message on creation, the README section, `make doctor`'s "keep a backup" line, and an in-app export as the data-safety net.
2. **Debug-key fallback in Studio.** If the user first installs a *release* build from Android Studio before ever running `make`, it is debug-signed (with a warning only at configuration time). A later `make install` would then fail with UPDATE_INCOMPATIBLE. Two options, which are the plan's call:
   - Accept it; the README says to use `make install`.
   - Make the fallback fail unless `-Pmdwriter.allowDebugSigning=true`. This would also require `make check` to pass that flag in CI.
3. **Build-time versionCode** makes no-op release rebuilds take about 18 s (R8 re-runs). This is a deliberate trade for robustness.
4. **Wireless pairing, OEM quirks** (Samsung Auto Blocker, Xiaomi "Install via USB") and the Android 16 Advanced Protection interaction were **not** tested on hardware.
5. **GNU make 4.x on Linux** is untested (expected to work).
6. The **max versionCode 2 100 000 000** figure is from memory (UNVERIFIED this session); the chosen scheme is far below it.
7. **Key rotation** (apksigner lineage) is documented but UNVERIFIED. It is not needed if the policy is followed.
8. **Developer verification** (2026-09-30 regional, 2027 global) exempts ADB installs per the official FAQ. If Google changes this, `make install` over adb would still be the least affected path. Watch https://developer.android.com/developer-verification/guides/faq.

---

## 13. Sources (fetched 2026-09-25)

- adb user guide: Wi-Fi pairing (Android 11+), auto-connect, `-s` / `ANDROID_SERIAL`, troubleshooting (`adb server-status`, version 37.0.0+): https://developer.android.com/tools/adb
- Developer options: Build number ×7, and "Android 16 and higher: Settings > System > Developer options > Wireless debugging": https://developer.android.com/studio/debug/dev-options
- adb backup restriction for targetSdk 31+ (anchor `#adb-backup-restriction`): https://developer.android.com/about/versions/12/behavior-changes-12
- Developer verification overview and timeline: https://developer.android.com/developer-verification
- Developer verification FAQ, ADB exemption ("Apps installed using ADB won't require verification"; "The waiting period does not apply to ADB installs", updated 2026-03-23): https://developer.android.com/developer-verification/guides/faq
- apksigner, rotate and lineage (v3.1 default targets API 33+): https://developer.android.com/tools/apksigner
- Gradle configuration-cache requirements, ValueSource and external inputs: https://docs.gradle.org/current/userguide/configuration_cache_requirements.html (the lazy-ValueSource behaviour was verified empirically, V2)
- Secondary only (Samsung Auto Blocker; Advanced Protection and ADB): https://www.makeuseof.com/galaxy-phone-hiding-setting-locks-down-usb-debugging/ , https://www.androidauthority.com/android-advanced-protection-mode-developer-options-3679725/
- Local tools: `adb help` / `adb server-status` (adb 37.0.1-15733141), `logcat --help` on the API 37 emulator, `keytool` (JBR 21.0.11), `apksigner` and `aapt2` from build-tools 36.0.0.
- Earlier tracks: `research/toolchain.md` §8, §10 and §11 (installRelease + .dm, ANDROID_SERIAL, JDK rules), and `research/platform.md` (internal filesDir storage + optional SAF folder).
