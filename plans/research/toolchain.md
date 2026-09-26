# mdwriter — Toolchain & Versions Research (verified 2026-09-24)

Track: TOOLCHAIN & VERSIONS. Everything below was checked against primary sources on 2026-09-24
(Google Maven / Maven Central / Gradle Plugin Portal `maven-metadata.xml`, developer.android.com,
docs.gradle.org, services.gradle.org, kotlinlang.org, GitHub releases). Beyond reading the docs,
I built a complete skeleton project on this machine with the exact recommended configuration and
ran it end to end: debug and release builds, R8, JVM tests, Robolectric plus Compose UI tests,
Lint, Spotless/ktlint, configuration-cache reuse, and `installRelease` on the running Android 17
emulator. Those runs back most of the claims here. Anything I could not verify is marked
**UNVERIFIED**.

Verified skeleton (reference only, NOT the repo):
`/private/tmp/claude-501/-Users-gcg-Work-src-github-com-gcg-mdwriter/5b0851bd-6748-4c30-97c8-c488449cb76a/scratchpad/tc-skel`
Verified wrapper bootstrap dir:
`/private/tmp/claude-501/-Users-gcg-Work-src-github-com-gcg-mdwriter/5b0851bd-6748-4c30-97c8-c488449cb76a/scratchpad/wrapper-test`

---

## 0. TL;DR — the decisions

| Topic | Decision | Confidence |
|---|---|---|
| Android Gradle Plugin | **9.3.3** (default). One-line bump to **9.4.1** (latest stable) once Android Studio is updated to Quail 4 (2026.1.4). Both were verified with the identical build scripts. | high |
| Why not 9.4.1 by default | The installed Studio is **Quail 3 (2026.1.3)**. The official table says it supports AGP **7.1–9.3**, and 9.4 needs Quail 4. 9.3.x is also inside Kotlin 2.4.20's tested AGP range (≤ 9.3.1). | high |
| Gradle | **9.7.1** (current stable, 2026-08-19). AGP 9.3 needs ≥ 9.5.0 and AGP 9.4 needs ≥ 9.6.0. Do not use 9.8.0 yet (it is at rc-3). | high |
| JDK | Gradle daemon on **JDK 21** (local JBR 21.0.11), pinned via `gradle/gradle-daemon-jvm.properties` (`toolchainVersion=21`) plus the foojay resolver **1.0.0**. Bytecode target **Java 17** via `compileOptions`. Kotlin `jvmTarget` follows automatically. AGP's minimum is JDK 17. | high |
| JDK pitfall | Homebrew `openjdk` on this Mac is now **27**. Gradle 9.7.1 only runs on **JVM 17–26**, so never launch Gradle with it. Launching with Studio's bundled JBR **25.0.2** is fine. | high |
| Kotlin | **2.4.20** (2026-09-07). Use AGP's **built-in Kotlin** and do **NOT** apply `org.jetbrains.kotlin.android`. Apply `org.jetbrains.kotlin.plugin.compose` 2.4.20. Declaring it (plus `org.jetbrains.kotlin.jvm`) in the root `plugins {}` with `apply false` upgrades AGP's bundled KGP from 2.2.10 to 2.4.20 (verified in `buildEnvironment`). | high |
| compileSdk / targetSdk / minSdk | `compileSdk { version = release(37) }`, `targetSdk { version = release(37) }`, `minSdk { version = release(36) }`. The local `platforms/android-37.0` satisfies this, so nothing gets downloaded. | high |
| compileSdk 37 is mandatory | `androidx.core:core 1.19.1`, `compose ui 1.12.1` and `navigation3 1.2.0` declare `minCompileSdk=37` and `minAndroidGradlePluginVersion=9.1.0` in their AAR metadata. | high |
| Compose | BOM **2026.09.00**. It maps to Compose 1.12.1, material3 1.4.0 and material3-adaptive 1.3.0. | high |
| Icons | Do **not** use `material-icons-extended`. It is frozen at 1.7.8 and Google no longer recommends it. Use Material Symbols vector drawables. | high |
| Splash | Skip `core-splashscreen` because minSdk 36 already has the platform SplashScreen API. It also pulls in `appcompat-resources`. | high |
| Navigation | **No navigation library** for v1. Editor, drawer and sheet are driven by plain state. If a real second screen appears, use **Navigation 3 1.2.0** (stable since 2026-09-23). | medium |
| Modules | `:app` (Android, Compose) plus `:core:markdown` (pure Kotlin/JVM, fast JUnit 6 tests, Android Lint applied through `com.android.lint`). No `build-logic`. | high |
| R8 | New AGP 9.3+ DSL: `release { optimization { enable = true } }`, which enables code and resource optimization plus the default keep rules. Put keep rules in `src/main/keepRules/*.keep`. Full mode is the default. | high |
| Install | `./gradlew :app:installRelease` installs the **APK plus the baseline-profile `.dm`**. On-device result: `status=speed-profile reason=install-dm`. It finds adb through the SDK, so adb does not need to be on PATH. It respects `ANDROID_SERIAL`. | high |
| Code quality | **Spotless 8.10.2 + ktlint 1.8.0 + io.nlopez compose-rules 0.6.6**, plus Android Lint (`abortOnError=true`, `warningsAsErrors=false`). **No detekt**: 1.23.8 targets Kotlin 2.0.21 / AGP 8.8, and 2.0 is still alpha. | high |
| Wrapper bootstrap | Use the cached Gradle 8.7 once: `gradle wrapper --gradle-version 9.7.1 ...`, then `./gradlew wrapper ...` a second time to refresh the jar. SHA256 values are in section 4. | high (executed) |

---

## 1. This machine (re-verified today)

- Android Studio: `/Applications/Android Studio.app`. `product-info.json` gives version `AI-261.26222.65.2613.16025427` and `dataDirectoryName: AndroidStudio2026.1.3`, i.e. **Quail 3 (2026.1.3)**. `minRequiredJavaVersion: 21`. Bundled JBR: **OpenJDK 25.0.2** (`.../Contents/jbr/Contents/Home`).
- Current stable Studio on developer.android.com/studio: **Android Studio Quail 4 | 2026.1.4 Patch 1**.
- `/usr/bin/java` resolves to JBR **21.0.11** at `/Users/gcg/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home`. It is the only entry in `/usr/libexec/java_home -V`, and `java_home -v 21 --failfast` returns it.
- Homebrew: `/opt/homebrew/opt/openjdk` is **27** (2026-09-15) and `/opt/homebrew/opt/openjdk@17` is 17.0.20.1. Neither is registered with `java_home`, so Gradle auto-detection will not find them unless JAVA_HOME points there. Don't point it there.
- SDK `~/Library/Android/sdk`:
  - platforms `android-34`, `android-35`, `android-37.0` (source.properties: `Pkg.Desc=Android SDK Platform 17`, `AndroidVersion.ApiLevel=37.0`, `ExtensionLevel=22`, `Pkg.Revision=2`, empty CodeName, so this is final, not a preview)
  - build-tools 34.0.0 and 36.0.0. AGP 9.x's default is 36.0.0, so no download is needed.
  - platform-tools, emulator, `licenses/` (`android-sdk-license`, `android-sdk-arm-dbt-license`)
  - no cmdline-tools / sdkmanager
- The Google SDK repository (`https://dl.google.com/android/repository/repository2-3.xml`) currently offers `platforms;android-37.0`, `android-37.1`, `android-37.2`, `build-tools;37.0.0`, `platform-tools 37.0.1`, `cmdline-tools;latest = 23.0`, and emulator 37.1.11 (stable channel).
- Running devices during my tests: emulator(s) `sdk_gphone16k_arm64`, `ro.build.version.sdk=37`, release 17. Other agents may have started them.
- Gradle caches: `~/.gradle/wrapper/dists` now also contains `gradle-9.7.1-bin`. Another agent's run downloaded it during this session, so the first real build will not need to download Gradle.

---

## 2. Android Gradle Plugin ↔ Android Studio ↔ Gradle ↔ JDK

### 2.1 Latest AGP (Google Maven)
`https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml` (lastUpdated 2026-09-18):
- Stable 9.x line: 9.0.0, 9.0.1, 9.1.0, 9.1.1, 9.2.0, 9.2.1, 9.3.0, 9.3.1, 9.3.2, **9.3.3 (POM Last-Modified 2026-09-17)**, 9.4.0 (2026-09-01), **9.4.1 (2026-09-18)**.
- Newest overall: 9.5.0-alpha06. Not for us.

### 2.2 Android Studio ↔ AGP (official table, https://developer.android.com/build/releases/about-agp)
| Android Studio | Supported AGP |
|---|---|
| Quail 4, 2026.1.4 | 7.1–**9.4** |
| **Quail 3, 2026.1.3 (installed)** | 7.1–**9.3** |
| Quail 2, 2026.1.2 | 7.1–9.3 |
| Quail 1, 2026.1.1 | 7.1–9.2 |
| Panda 4, 2025.3.4 | 7.1–9.2 |
| Panda 3, 2025.3.3 | 7.0–9.1 |

The same page lists the **minimum tools per API level**. For API **37.0** the minimum is Studio Panda 3 (2025.3.3 Patch 1) and **AGP 9.1.1**. API 36.1 needs AGP 8.13.0. API 36 needs AGP 8.9.1.

### 2.3 AGP → Gradle minimum (same page)
| AGP | Min Gradle |
|---|---|
| 9.4 | 9.6.0 |
| 9.3 | 9.5.0 |
| 9.2 | 9.4.1 |
| 9.1 | 9.3.1 |
| 9.0 | 9.1.0 |

### 2.4 AGP release-notes compatibility boxes (developer.android.com/build/releases/...)
- AGP 9.4 (`gradle-plugin`, updated 2026-09-18): max API **37**; Gradle min/default 9.6.0; SDK Build Tools 36.0.0; NDK default 28.2.13676358; **JDK 17**.
- AGP 9.3 (`agp-9-3-0-release-notes`): max API **37**; Gradle 9.5.0; Build Tools 36.0.0; JDK 17. New in 9.3: the `optimization {}` DSL, the `src/<variant>/keepRules/*.keep` source set, and `:app:analyzeReleaseR8Config`. Fixed in 9.3.3: "optimization { enable = true } DSL does not set isMinifyEnabled in the ApplicationVariant object" (Issue 556343682), D8 slowdown, and several R8 VerifyError fixes. **Use 9.3.3, not 9.3.0–9.3.2.**
- AGP 9.2: max API 37.0; Gradle 9.4.1. AGP 9.1.1: supports API 37.0 and below; Gradle 9.3.1.
- AGP 9.0: KGP runtime dependency **2.2.10**. The POMs of 9.3.3 and 9.4.1 still declare `kotlin-gradle-plugin:2.2.10` and `kotlin-stdlib:2.2.10` as runtime deps, which I checked in the POM files.
- AGP 9.4 adds `android.newDsl.optOut=:module` and dynamic-feature variant-parity checks. Neither matters for this app, so 9.3.3 loses nothing.

### 2.5 Gradle version
- `https://services.gradle.org/versions/current` returns **9.7.1**, built 2026-08-19. Dist sha256 `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`. wrapper jar sha256 `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`.
- Other recent finals: 9.7.0 (08-06), 9.6.1 (06-26), 9.6.0, 9.5.1, 9.5.0, 8.14.5. In flight: 9.8.0-rc-3 and 9.9.0-milestone-2. Don't use those.
- The Gradle 9.7.1 compatibility page (docs.gradle.org/current/userguide/compatibility.html) says:
  - "A JVM version between 17 and 26 is required to execute Gradle"
  - it is tested with Kotlin 2.0.0 through 2.4.20-Beta1 and with **AGP 9.0 through 9.4.0-alpha03**
  - the embedded Kotlin is 2.4.0
- Kotlin 2.4.20's compatibility row (kotlinlang.org/docs/gradle-configure-project.html) gives Gradle 7.6.3–**9.7.0** and AGP 8.5.2–**9.3.1**. "Max" here means the newest version JetBrains tested. Gradle 9.7.1 and AGP 9.3.3 are patch releases on top and worked in my builds with **zero deprecation warnings** (`--warning-mode all`).

### 2.6 JDK
- AGP 9.x minimum JDK is 17. Gradle 9.7.1 runs on 17–26. Robolectric 4.17 and everything else ran on JBR 21 in my tests.
- **Recommendation**:
  - Pin the **daemon** JDK with Gradle's *Daemon JVM criteria*. It is stable in 9.7.1, and the Android docs say Studio Panda 1+ uses it for new projects.
  - Commit `gradle/gradle-daemon-jvm.properties`, generated with `./gradlew updateDaemonJvm --jvm-version=21`. This **requires** the foojay settings plugin. Without it, the task fails with "Toolchain download repositories have not been configured" (verified).
  - Studio's own wizard ships `gradle-daemon-jvm-{17,21,25}.properties` templates generated with foojay **1.0.0** (from `android.jar!/templates/project/toolchain/metadata.properties`).
- Do **not** set `org.gradle.java.home` in the project (it is machine-specific).
- Bytecode: `android.compileOptions { source/targetCompatibility = VERSION_17 }`. With built-in Kotlin, `kotlin.compilerOptions.jvmTarget` defaults to `targetCompatibility`. Verified: `javap` shows class major version **61** (Java 17) for both `:app` and `:core:markdown`.
- Why not a `java { toolchain { 17 } }`? On this machine JDK 17 is not registered with `java_home`, so Gradle would auto-download a JDK 17 of about 190 MB via foojay. It adds nothing, because the daemon criteria already pin the JDK that compiles.

---

## 3. Kotlin under AGP 9 (built-in Kotlin)

Source: https://developer.android.com/build/migrate-to-built-in-kotlin and the AGP 9.0 release notes.
- AGP 9.0+ turns on built-in Kotlin by default (`android.builtInKotlin` default changed false→true). **Do not apply `org.jetbrains.kotlin.android`**. Applying it fails with "Cannot add extension with name 'kotlin'…" or "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0."
- `kotlin-kapt` is incompatible with it. Use KSP, or `com.android.legacy-kapt`. **We need neither**: no Room, no Hilt, no annotation processors.
- `android.kotlinOptions {}` is gone. Use the top-level `kotlin { compilerOptions { ... } }` in the Android module (verified: `optIn.add(...)` compiles).
- Kotlin source dirs: `src/main/kotlin` and `src/main/java` both work by default. Use `src/main/kotlin` (verified).
- **KGP version bump**: AGP depends on KGP 2.2.10 at runtime. Kotlin 2.4.20 gets onto the classpath in one of two ways:
  - Recommended and verified: declare `org.jetbrains.kotlin.plugin.compose` version `2.4.20` (and `org.jetbrains.kotlin.jvm` 2.4.20) in the **root** `plugins {}` with `apply false`. The `compose-compiler-gradle-plugin-2.4.20.module` metadata depends on `kotlin-gradle-plugin` 2.4.20, so conflict resolution upgrades it. `./gradlew buildEnvironment` shows `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.20`.
  - Alternative from the docs: `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") } }` in the root. It is not needed if the plugin aliases are at the root.
- Exact plugin IDs:
  - `com.android.application` (AGP version)
  - `org.jetbrains.kotlin.plugin.compose` (Kotlin version; this is the Compose compiler, bundled with Kotlin since 2.0)
  - `org.jetbrains.kotlin.jvm` (Kotlin version, for the pure-JVM module)
  - `com.android.lint` (AGP version, to lint the JVM module)
  - `org.jetbrains.kotlin.plugin.serialization` 2.4.20, **only if** Nav3 saveable keys or JSON are ever needed. Not needed for v1.
- Kotlin 2.4.20 notes (kotlinlang.org/docs/whatsnew2420.html): released 2026-09-07. Nothing breaking for Android. `when` via invokedynamic is stable on JVM 21+ targets, which doesn't affect us since we target 17.
- Compose compiler options are optional (`composeCompiler { ... }`), and strong skipping is on by default. Nothing needs configuring.

---

## 4. Bootstrapping the Gradle wrapper on THIS machine (executed, works)

No `gradle` on PATH, but `~/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/` is cached. Gradle 8.7 runs fine on JBR 21.

```bash
cd <repo root>
# Settings file so Gradle 8.7 sees a build (an empty one is fine; the real one also works)
[ -f settings.gradle.kts ] || echo 'rootProject.name = "mdwriter"' > settings.gradle.kts

SHA=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a   # gradle-9.7.1-bin.zip
# 1st run: the old Gradle writes the properties + an 8.7-era wrapper jar/scripts
~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle --no-daemon -q \
  wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum "$SHA"
# 2nd run: Gradle 9.7.1 regenerates gradlew, gradlew.bat and gradle-wrapper.jar at its own version
./gradlew -q wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum "$SHA"
shasum -a 256 gradle/wrapper/gradle-wrapper.jar
#   -> 7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d  (== official wrapperChecksum)
./gradlew --version    # Gradle 9.7.1, Kotlin 2.4.0 (embedded), Launcher JVM 21.0.11
```
After the 1st run the jar sha was `cb0da675…` (8.7's jar, 43,453 bytes). After the 2nd run it was `7a9ce74c…` (47,505 bytes). Run it **twice**, as developer.android.com/build/releases/about-agp also says.

Resulting `gradle/wrapper/gradle-wrapper.properties` (verified content):
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
networkTimeout=10000
retries=0
retryBackOffMs=500
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Then generate the daemon JVM criteria. The foojay plugin must already be in `settings.gradle.kts`:
```bash
./gradlew -q updateDaemonJvm --jvm-version=21
./gradlew --version | grep "Daemon JVM"
#   Daemon JVM: Compatible with Java 21, any vendor, nativeImageCapable=false (from gradle/gradle-daemon-jvm.properties)
```
The generated `gradle/gradle-daemon-jvm.properties` has `toolchainVersion=21` plus `toolchainUrl.<OS>.<ARCH>=https\://api.foojay.io/disco/v3.0/ids/<id>/redirect` lines for FREE_BSD/LINUX/MAC_OS/UNIX/WINDOWS × AARCH64/X86_64. Commit it and don't hand-edit it.

Fallbacks, if the cached 8.7 were ever gone:
- **Hand-write** the properties file above and fetch the jar from the Gradle repo tag: `curl -fsSLo gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v9.7.1/gradle/wrapper/gradle-wrapper.jar`. Verified sha is `7a9ce74c…`, identical to the official one. Get `gradlew`/`gradlew.bat` from the same tag path. Note: `https://services.gradle.org/distributions/gradle-9.7.1-wrapper.jar` returns **404**; only the `.sha256` exists there.
- Or download `gradle-9.7.1-bin.zip`, check it against the `.sha256` URL, unzip it to the scratch dir, and run its `bin/gradle wrapper ...` once.
- Studio also ships a wrapper template (`android.jar!/templates/project/wrapper/`), but its properties point at 9.0.0. Not recommended.

Commit `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` and `gradle/gradle-daemon-jvm.properties`.

---

## 5. SDK levels, DSL, and the SDK on disk

### 5.1 DSL (AGP 9.x, verified on 9.3.3 and 9.4.1)
New block syntax, shown on https://developer.android.com/build/gradle-build-overview:
```kotlin
android {
    compileSdk {
        version = release(37)            // optional: release(37) { minorApiLevel = 1 } for 37.1
    }
    defaultConfig {
        minSdk { version = release(36) }
        targetSdk { version = release(37) }
    }
}
```
The legacy `compileSdk = 37` / `minSdk = 36` / `targetSdk = 37` still compiles on AGP 9.4.1 with **no** deprecation warning (tested with `--warning-mode all`), and the Android 17 setup page still shows it. Use the block form for consistency with current docs.

Verified APK badging (`aapt2 dump badging`): `compileSdkVersion='37' compileSdkVersionCodename='17'`, `minSdkVersion:'36'`, `targetSdkVersion:'37'`.

### 5.2 Does `platforms/android-37.0` satisfy compileSdk 37?
Yes. Builds used it with **no downloads**: SDK directory timestamps stayed unchanged at Aug 12/26 after all builds. `release(37)` means 37.0. Don't use `minorApiLevel = 1/2` (37.1 or 37.2). They would require those platforms, and AGP would auto-download them. The AGP docs only say "max API level 37" (for 9.3 and 9.4) and don't mention 37.1 or 37.2, so the minor levels are **UNVERIFIED** and we don't need them.

Auto-download: "Gradle can automatically download missing SDK packages that a project depends on, as long as the corresponding SDK license agreements have already been accepted". `licenses/` exists here. Disable with `android.builder.sdkDownload=false`. Source: https://developer.android.com/studio/intro/update. I did not trigger a real auto-download to avoid touching the SDK, so whether it works here without sdkmanager is **UNVERIFIED**.

The SDK location **must** be provided. Without `local.properties` and without `ANDROID_HOME`, the build fails with "SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file" (verified). The Makefile should create `local.properties` (`sdk.dir=$HOME/Library/Android/sdk`) if it is missing and/or export `ANDROID_HOME`. `local.properties` is git-ignored.

### 5.3 minSdk recommendation: **36** (Android 16 + Android 17)
- The user explicitly asked for "the current 1–2 versions". API 37 is Android 17: its platform package is final, and the stable release date per Wikipedia and secondary sources is **2026-06-16**, which is medium confidence since developer.android.com's release-notes page lists only betas. API 36 is Android 16. So minSdk 36 covers exactly two versions.
- AndroidX libraries have `minSdkVersion` 21–24 in their AAR manifests, so they don't constrain this.
- Benefits: no `Build.VERSION.SDK_INT` branches, lint `NewApi` is quiet for API ≤ 36, platform SplashScreen, predictive back and edge-to-edge are all guaranteed.
- Risk: if the user's phone is still on Android 15, install fails with `INSTALL_FAILED_OLDER_SDK`. The Makefile `doctor` target should check `adb shell getprop ro.build.version.sdk` ≥ 36 (the sketch in section 11 does this). Lowering to 35 later is a one-line change.
- targetSdk 37 behavior changes that touch an editor are all benign: large-screen orientation/resizability can no longer be opted out of (sw ≥ 600dp), an accessibility attribute for complex-IME typing, and passwords hidden for physical keyboards. Source: https://developer.android.com/about/versions/17/behavior-changes-17.

---

## 6. Library versions (latest STABLE, fetched today)

"Stable" means the newest version with no alpha/beta/rc/dev tag. Where there is a BOM, use the BOM and omit versions.

### 6.1 Build plugins
| Artifact / plugin id | Version | Metadata URL |
|---|---|---|
| `com.android.application` / `com.android.tools.build:gradle` | **9.3.3** (default) / 9.4.1 (latest) | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml |
| `com.android.lint` | same as AGP | (plugin marker on Google Maven; resolved in build) |
| `org.jetbrains.kotlin.plugin.compose` | **2.4.20** | https://plugins.gradle.org/m2/org/jetbrains/kotlin/plugin/compose/org.jetbrains.kotlin.plugin.compose.gradle.plugin/maven-metadata.xml |
| `org.jetbrains.kotlin.jvm` / `kotlin-gradle-plugin` | **2.4.20** (next: 2.5.0-Beta1) | https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml |
| `org.jetbrains.kotlin.plugin.serialization` (optional) | 2.4.20 | https://plugins.gradle.org/m2/org/jetbrains/kotlin/plugin/serialization/org.jetbrains.kotlin.plugin.serialization.gradle.plugin/maven-metadata.xml |
| `org.gradle.toolchains.foojay-resolver-convention` | **1.0.0** | https://plugins.gradle.org/m2/org/gradle/toolchains/foojay-resolver-convention/org.gradle.toolchains.foojay-resolver-convention.gradle.plugin/maven-metadata.xml |
| `com.diffplug.spotless` | **8.10.2** (2026-09-04) | https://plugins.gradle.org/m2/com/diffplug/spotless/com.diffplug.spotless.gradle.plugin/maven-metadata.xml |
| `androidx.baselineprofile` (optional, later) | 1.5.0 | https://dl.google.com/dl/android/maven2/androidx/baselineprofile/androidx.baselineprofile.gradle.plugin/maven-metadata.xml |
| `org.jlleitschuh.gradle.ktlint` (alternative, not chosen) | 14.2.0 | https://plugins.gradle.org/m2/org/jlleitschuh/gradle/ktlint/org.jlleitschuh.gradle.ktlint.gradle.plugin/maven-metadata.xml |
| `io.gitlab.arturbosch.detekt` (not chosen) | 1.23.8 (Feb 2025); `dev.detekt` 2.0.0-alpha.6 only | https://plugins.gradle.org/m2/io/gitlab/arturbosch/detekt/io.gitlab.arturbosch.detekt.gradle.plugin/maven-metadata.xml |
| `com.github.ben-manes.versions` (optional) | 0.64.0 | https://plugins.gradle.org/m2/com/github/ben-manes/versions/com.github.ben-manes.versions.gradle.plugin/maven-metadata.xml |
| `nl.littlerobots.version-catalog-update` (optional) | 1.1.1 | https://plugins.gradle.org/m2/nl/littlerobots/version-catalog-update/nl.littlerobots.version-catalog-update.gradle.plugin/maven-metadata.xml |

### 6.2 Runtime (Google Maven; base URL `https://dl.google.com/dl/android/maven2/<group/as/path>/<artifact>/maven-metadata.xml`)
| Artifact | Stable | Newest (pre-release) | Use? |
|---|---|---|---|
| `androidx.compose:compose-bom` | **2026.09.00** (2026-09-09) | — | **yes** |
| ↳ `androidx.compose.ui:ui`, `ui-graphics`, `ui-text`, `ui-tooling(-preview)`, `ui-test-junit4`, `ui-test-manifest`; `androidx.compose.foundation:foundation`; `androidx.compose.runtime:runtime` | 1.12.1 (via BOM) | 1.13.0-alpha03 | yes (BOM) |
| ↳ `androidx.compose.material3:material3` | 1.4.0 (via BOM; released 2025-09-24) | 1.5.0-alpha29 | yes (BOM) |
| ↳ `androidx.compose.material3.adaptive:adaptive*` | 1.3.0 (via BOM) | 1.4.0-alpha02 | not needed (see 6.4) |
| ↳ `androidx.compose.material:material-icons-core/extended` | 1.7.8 (frozen since 2025-02) | — | **no** (see 6.4) |
| `androidx.activity:activity-compose` | **1.13.0** | 1.14.0-alpha03 | yes |
| `androidx.lifecycle:lifecycle-runtime-ktx` / `-runtime-compose` / `-viewmodel-compose` | **2.11.0** | 2.12.0-alpha04 | yes |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 | 2.12.0-alpha04 | only with Nav3 |
| `androidx.core:core-ktx` | **1.19.1** (minCompileSdk 37, min AGP 9.1.0) | — | yes |
| `androidx.core:core-splashscreen` | 1.2.0 | — | **no** (platform API; pulls appcompat-resources 1.7.0) |
| `androidx.datastore:datastore-preferences` | **1.2.1** | 1.3.0-alpha11 | yes (settings) |
| `androidx.documentfile:documentfile` | **1.1.0** | — | yes (SAF helpers) |
| `androidx.navigation3:navigation3-runtime` / `-ui` | 1.2.0 (stable 2026-09-23) | 1.3.0-alpha01 | only if a 2nd screen appears |
| `androidx.navigation:navigation-compose` | 2.10.2 | — | no |
| `androidx.window:window` | 1.5.1 | 1.6.0-alpha05 | no |
| `androidx.compose.material3:material3-window-size-class` | 1.4.0 | 1.5.0-alpha29 | no |
| `androidx.profileinstaller:profileinstaller` | **1.4.1** | — | yes (explicit; also transitive) |
| `androidx.benchmark:benchmark-macro-junit4` | 1.5.0 | — | optional, later |
| `androidx.test.uiautomator:uiautomator` | 2.4.0 | — | optional, later |
| `androidx.test.ext:junit` | **1.3.0** | — | yes |
| `androidx.test.espresso:espresso-core` | **3.7.0** | — | yes (**also in `testImplementation`**, see 9.2) |
| `androidx.test:runner` / `core` / `rules` | **1.7.0** | — | runner yes |
| `androidx.collection:collection` | 1.6.0 | 1.7.0-rc01 | transitive only |
| `androidx.annotation:annotation` | 1.11.0 | — | transitive only |

### 6.3 Maven Central (base `https://repo1.maven.org/maven2/<group/path>/<artifact>/maven-metadata.xml`)
| Artifact | Stable | Use? |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` / `-android` / `-test` | **1.11.0** | yes |
| `junit:junit` | **4.13.2** | yes (Android unit tests / Robolectric / Compose tests are JUnit4) |
| `org.junit:junit-bom` + `org.junit.jupiter:junit-jupiter` + `org.junit.platform:junit-platform-launcher` | **6.1.3** | yes (`:core:markdown` only) |
| `org.robolectric:robolectric` | **4.17** (2026-09-10, "supports SDK 37") | yes |
| `com.google.truth:truth` | **1.4.5** | yes (assertions everywhere) |
| `app.cash.turbine:turbine` | **1.2.1** | yes (Flow tests) |
| `io.kotest:kotest-assertions-core` | 6.2.5 | alternative to Truth, pick one (Truth chosen) |
| `io.mockk:mockk` | 1.14.11 | avoid; prefer fakes |
| `org.jetbrains.kotlinx:kotlinx-serialization-core/json` | 1.11.0 | only with Nav3 keys / JSON |
| `org.jetbrains.kotlinx:kotlinx-collections-immutable` | 0.5.2 | optional (stable Compose params) |
| `com.pinterest.ktlint:ktlint-cli` | **1.8.0** (2025-11-14; Spotless default) | yes (via Spotless) |
| `io.nlopez.compose.rules:ktlint` | **0.6.6** (2026-09-01) | yes (Spotless custom rule set) |
| `org.jetbrains:markdown` | 0.7.14 (2026-09-16) | parser candidate, other track |
| `org.commonmark:commonmark` (+ `-ext-gfm-tables`, etc.) | 0.30.0 (2026-08-06) | parser candidate, other track |

### 6.4 Notes on specific libraries
- **Material icons**: The compose-material3 release notes say: "The androidx.compose.material.icons library is no longer recommended… We have stopped publishing updates to this library and it has been removed from the latest Material 3 library release". The notes recommend downloading Vector Drawable XML from https://fonts.google.com/icons (Android tab), i.e. Material Symbols. The library also slows builds. For the few toolbar glyphs (bold, italic, link, heading, list, quote, code, sidebar), add `res/drawable/ic_*.xml` vector drawables (Material Symbols Outlined/Rounded, weight 300–400) and render with `painterResource`/`Icon`.
- **Splash screen**: minSdk 36 includes the API 31+ platform SplashScreen. Set `android:windowSplashScreenBackground`, `android:windowSplashScreenAnimatedIcon` and `android:windowSplashScreenIconBackgroundColor` in the theme, and optionally `postSplashScreenTheme`. That works without the library. `core-splashscreen:1.2.0` would pull `androidx.appcompat:appcompat-resources:1.7.0` (verified with `dependencyInsight`).
- **Navigation**: The app is essentially one screen: the editor, a `ModalNavigationDrawer` for the file library, and a settings `ModalBottomSheet`/dialog. Plain `rememberSaveable` state plus `BackHandler`/predictive back covers this without a nav library. If a full-screen destination is ever added, use **Navigation 3 1.2.0**:
  - artifacts `navigation3-runtime` and `navigation3-ui`
  - optionally `lifecycle-viewmodel-navigation3`
  - `rememberNavBackStack` needs `@Serializable` `NavKey`s, which means the serialization plugin 2.4.20 plus `kotlinx-serialization-core` 1.11.0
  - the official setup page (developer.android.com/guide/navigation/navigation-3/get-started) requires compileSdk ≥ 36, and the navigation3-ui 1.2.0 AAR actually needs compileSdk 37
- **Adaptive / window size**: For an iA-Writer-style centred text column, `Modifier.widthIn(max = …)` inside a full-width box is enough. If the UI must branch on window class, use `currentWindowAdaptiveInfo()` from `androidx.compose.material3.adaptive:adaptive` (1.3.0 via the BOM). Don't use `material3-window-size-class` or `androidx.window` directly.
- **Compose test API change**: `androidx.compose.ui.test.junit4.createComposeRule` is **deprecated** in Compose 1.12. The compiler warning says: "Use `androidx.compose.ui.test.junit4.v2.createComposeRule` instead. The v2 APIs use StandardTestDispatcher…". Tests must import `androidx.compose.ui.test.junit4.v2.createComposeRule` (verified working).

---

## 7. Project structure recommendation

```
mdwriter/
├─ settings.gradle.kts
├─ build.gradle.kts              # plugins apply false + spotless
├─ gradle.properties
├─ gradle/libs.versions.toml
├─ gradle/gradle-daemon-jvm.properties
├─ gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties}
├─ gradlew, gradlew.bat, Makefile, .editorconfig, .gitignore
├─ app/                          # com.android.application + compose; UI, SAF/file IO, DataStore
│  └─ src/{main,test,androidTest}/kotlin/..., src/main/keepRules/app.keep
└─ core/markdown/                # org.jetbrains.kotlin.jvm + com.android.lint; pure Kotlin
   └─ src/{main,test}/kotlin/...
```
- **Why `:core:markdown` as a pure JVM module**:
  - The Markdown tokenizer/highlighter is the most test-heavy code. As plain JVM code, its tests run in about a second with `./gradlew :core:markdown:test` (JUnit 6), with no Android or Robolectric.
  - The module boundary stops Android types from leaking into the parser.
  - It lints fine: applying `com.android.lint` there removed the "Lint will treat :core:markdown as an external dependency" warning (verified).
- **Constraint for the plan**: Compose's `AnnotatedString`/`SpanStyle` are Android artifacts and are **not** available in a plain JVM module. So `:core:markdown` must output a neutral model, e.g. `data class StyledRange(start: Int, end: Int, kind: MarkupKind)` with `enum MarkupKind { H1..H6, STRONG, EMPHASIS, CODE_SPAN, SYNTAX_MARKER, LINK, … }`. `:app` maps `MarkupKind` → `SpanStyle`/`ParagraphStyle` (font size for headings, etc.).
- Both candidate parsers are pure JVM, so they fit in `:core:markdown`: `org.jetbrains:markdown` 0.7.14 and `org.commonmark:commonmark` 0.30.0. Choosing between them belongs to another track.
- **No** `build-logic`/convention plugins, no `:core:ui`, no DI framework. That would be overkill for two modules. Use `project(":core:markdown")`, not type-safe project accessors.

---

## 8. Verified build files (copy these; they passed `make check` on AGP 9.3.3 and 9.4.1)

### 8.1 `settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "mdwriter"
include(":app")
include(":core:markdown")
```
`repositoriesMode = …` works as a plain assignment in Gradle 9.7.1 (verified). The foojay plugin version must be a literal: the `plugins {}` block in settings can't read the version catalog.

### 8.2 `gradle/libs.versions.toml`
```toml
[versions]
agp = "9.3.3"            # bump to "9.4.1" after updating Android Studio to Quail 4 (2026.1.4)
kotlin = "2.4.20"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
coreKtx = "1.19.1"
datastore = "1.2.1"
documentfile = "1.1.0"
coroutines = "1.11.0"
profileinstaller = "1.4.1"
junit4 = "4.13.2"
junitBom = "6.1.3"
androidxTestExtJunit = "1.3.0"
androidxTestRunner = "1.7.0"
espresso = "3.7.0"
robolectric = "4.17"
truth = "1.4.5"
turbine = "1.2.1"
spotless = "8.10.2"
ktlint = "1.8.0"
composeRules = "0.6.6"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
androidx-documentfile = { module = "androidx.documentfile:documentfile", version.ref = "documentfile" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-text = { module = "androidx.compose.ui:ui-text" }
androidx-compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junitBom" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExtJunit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-espresso-core = { module = "androidx.test.espresso:espresso-core", version.ref = "espresso" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
truth = { module = "com.google.truth:truth", version.ref = "truth" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-lint = { id = "com.android.lint", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```
The verified skeleton also had `core-splashscreen` (removed per 6.4). No `kotlin-android` alias.

### 8.3 Root `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false   // also lifts AGP's KGP 2.2.10 -> 2.4.20
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"))
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}
```

### 8.4 `gradle.properties`
```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.configuration-cache=true
kotlin.code.style=official
```
What is **no longer needed**, because these are now defaults:
- `android.useAndroidX` (false→true in AGP 9.0; Studio's template only emits it for older AGP)
- `android.nonTransitiveRClass` and `android.nonFinalResIds` (default true since AGP 8.0)
- `android.enableR8.fullMode` (default true since 8.0)
- `android.r8.optimizedResourceShrinking` (default true in 9.0)
- `android.enableAppCompileTimeRClass`, `android.builtInKotlin`, `android.newDsl`, `android.uniquePackageNames` (all flipped on in 9.0)

Also, don't set `android.enableJetifier`.

- Configuration cache is **not** on by default in Gradle 9.7.1 ("By default, Gradle does not use the Configuration Cache"), so keep `org.gradle.configuration-cache=true`. Studio's new-project template also sets it. Verified: "Configuration cache entry reused" on the 2nd build, and AGP, Spotless and foojay are all CC-compatible.
- `org.gradle.configuration-cache.parallel` is still incubating. Skip it.
- `kotlin.code.style=official` is only an IDE hint. Studio's template still emits it, and it is harmless.

### 8.5 `app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // NOTE: no org.jetbrains.kotlin.android — AGP 9 built-in Kotlin compiles Kotlin
}

android {
    namespace = "dev.mdwriter"                  // final package name is the plan author's call
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.mdwriter"
        minSdk { version = release(36) }
        targetSdk { version = release(37) }
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true                    // R8 code + resource optimization, default keep rules (AGP >= 9.3)
            }
            signingConfig = signingConfigs.getByName("debug")   // see §10 for a dedicated keystore
        }
        debug {
            applicationIdSuffix = ".debug"       // debug and release can be installed side by side
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17   // Kotlin jvmTarget follows this automatically
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",   // REQUIRED for Robolectric 4.17 on SDK 37
                )
            }
        }
    }

    dependenciesInfo {                           // no Play-signed dependency blob in a sideloaded, privacy-first app
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")   // optional; example of built-in-Kotlin DSL
    }
}

dependencies {
    implementation(project(":core:markdown"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)   // REQUIRED: forces 3.7.0 over transitive 3.5.0 (see §9.2)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
```
Keep rules go in `app/src/main/keepRules/app.keep` (suffix `.keep`). Verified: a probe rule placed there showed up in `build/outputs/mapping/release/configuration.txt` and the probe class was kept. With `optimization { enable = true }` you do **not** list `proguard-android-optimize.txt` yourself.

### 8.6 `core/markdown/build.gradle.kts`
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)   // REQUIRED on Gradle 9 (verified: without it "Failed to load JUnit Platform")
    testImplementation(libs.truth)
}

tasks.test {
    useJUnitPlatform()
}
```

### 8.7 `.editorconfig` (ktlint config; verified with Spotless)
```ini
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
indent_style = space
indent_size = 4

[*.{kt,kts}]
ktlint_code_style = ktlint_official
ktlint_function_naming_ignore_when_annotated_with = Composable
max_line_length = 120
```

### 8.8 `.gitignore` (minimum)
```
.gradle/
.kotlin/          # Kotlin 2.x session dir (created at project root; verified)
build/
local.properties
*.jks
*.keystore
keystore.properties
.idea/
*.iml
captures/
.externalNativeBuild/
.cxx/
.DS_Store
```

### 8.9 Minimal sources used for verification (for reference)
- `MainActivity : ComponentActivity`, calling `enableEdgeToEdge()` then `setContent { MaterialTheme { Surface { BasicTextField(state = rememberTextFieldState(...)) } } }` with `Modifier.safeDrawingPadding().imePadding()` and `android:windowSoftInputMode="adjustResize"`. This compiles and starts on the Android 17 emulator: COLD start about 1.05 s, and the process stays alive with no crash-buffer entries.
- Manifest theme parent: `android:Theme.Material.Light.NoActionBar`, which needs no AppCompat or MDC dependency.
- Lint flagged only `DataExtractionRules` (use `android:dataExtractionRules`, not `allowBackup`) and `MissingApplicationIcon`, both as warnings. The real app should add `android:dataExtractionRules="@xml/data_extraction_rules"` (or `android:allowBackup="false"` for a no-cloud app) and an adaptive icon.

---

## 9. Testing setup (verified)

### 9.1 Layers
| Layer | Where | Framework | Command |
|---|---|---|---|
| Markdown tokenizer/highlighter | `:core:markdown` | JUnit **6.1.3** Jupiter + Truth | `./gradlew :core:markdown:test` |
| ViewModels, repositories, Compose UI on the JVM | `:app` `src/test` | JUnit4 + **Robolectric 4.17** + Compose `ui-test-junit4` (v2 API) + coroutines-test + Turbine + Truth | `./gradlew :app:testDebugUnitTest` |
| On-device UI (optional, few) | `:app` `src/androidTest` | AndroidJUnitRunner (default since AGP 9) + Compose test + Espresso 3.7.0 | `./gradlew :app:connectedDebugAndroidTest` |

### 9.2 Pitfalls found by actually running them
1. **Robolectric on JDK 17+ with SDK 37** needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED`. Without it the error is: `RuntimeException: Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?`, caused by `IllegalAccessException … jdk.internal.access.SharedSecrets`. The flag list is from robolectric.org/getting-started. The subset in 8.5 is sufficient.
2. **Espresso 3.5.0 breaks on SDK 37**. Compose `ui-test-junit4` 1.12.1 transitively brings `espresso-core:3.5.0`, which calls the removed `InputManager.getInstance()`. The result is `NoSuchMethodException: android.hardware.input.InputManager.getInstance()` inside `RobolectricIdlingStrategy`. The fix is `testImplementation("androidx.test.espresso:espresso-core:3.7.0")` (verified).
3. `createComposeRule` has moved to `androidx.compose.ui.test.junit4.v2` (deprecation, see 6.4).
4. Robolectric downloads `org.robolectric:android-all-instrumented:17-robolectric-15733970-i7` into `~/.m2/repository` on the **first** test run, so it needs network once. It uses targetSdk 37 by default.
5. Gradle 9 requires an explicit `junit-platform-launcher` on the JUnit Platform test runtime classpath (verified).
6. AGP 9 default: `android.onlyEnableUnitTestForTheTestedBuildType=true`, so unit tests exist only for `debug` (`testDebugUnitTest`). Don't script `testReleaseUnitTest`.

---

## 10. Release build, R8, signing, install, baseline profiles

- `./gradlew :app:assembleRelease` with `optimization { enable = true }`: the release APK is **1.9 MB**, versus **32 MB** for the debug APK. Mapping and R8 reports are in `app/build/outputs/mapping/release/` (`mapping.txt`, `configuration.txt`, `configanalyzer.html`, …). For daily use, install **release**: debug Compose builds are noticeably slower because they are not R8-optimized and debuggable.
- **Baseline profiles**: library profiles (Compose, etc.) are merged automatically into `app/build/outputs/apk/release/baselineProfiles/{0,1}/app-release.dm`. **`./gradlew :app:installRelease` installs both** ("Installing APK 'app-release.apk, app-release.dm' on 'Pixel_10_Pro_XL(AVD) - 17'"). `dumpsys package` then showed `arm64: [status=speed-profile] [reason=install-dm]`, meaning AOT compilation at install. A bare `adb install app-release.apk` would skip the `.dm`, so **`make install` should call Gradle's `installRelease`, not raw adb**.
- A custom app baseline profile (the `androidx.baselineprofile` plugin 1.5.0, a `com.android.test` module, `benchmark-macro-junit4` 1.5.0, `uiautomator` 2.4.0) needs a device to generate. Benchmark 1.5.0 notes say the plugin "no longer requires newDsl=false in AGP 9.0". Treat this as an **optional late task**. The library profiles already cover Compose.
- **Signing**: release is signed with the debug keystore, `~/.android/debug.keystore`, which exists and has cert SHA-256 `ef2e7f86…`. That is acceptable for a personal sideloaded app. **Pick the signing key once**: switching keys later forces an uninstall, which deletes app-private data. Options for the plan:
  - (a) keep debug signing (simplest)
  - (b) `make keystore` runs `$(JAVA_HOME)/bin/keytool -genkeypair …` once, writing to e.g. `~/.android/mdwriter-release.jks` plus a git-ignored `keystore.properties`. `app/build.gradle.kts` creates `signingConfigs.create("release")` if that file exists and otherwise falls back to debug. Reading the properties file at configuration time is fine with configuration cache, because the file becomes a CC input. The (b) code sketch itself was **UNVERIFIED** in this session.
- `applicationIdSuffix = ".debug"` keeps dev builds from overwriting the user's real install.
- `ANDROID_SERIAL=<serial>` is honoured by AGP's install task: with two emulators connected, only the chosen one got the APK (verified). Without it, AGP installs on **all** connected devices.

---

## 11. Makefile sketch (toolchain parts verified; full Makefile belongs to the Makefile track)

Verified targets: `help`, `doctor`, `install` (with `ANDROID_SERIAL`), `check`.
```make
SHELL := /bin/bash
.DEFAULT_GOAL := help

APP_ID      ?= dev.mdwriter
ANDROID_SDK ?= $(or $(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(HOME)/Library/Android/sdk)
ADB         := $(ANDROID_SDK)/platform-tools/adb
STUDIO_JBR  := /Applications/Android Studio.app/Contents/jbr/Contents/Home

# Gradle 9.7.x must be *launched* by a JDK 17..26 (Homebrew openjdk is 27 -> would fail).
JAVA_HOME ?= $(shell /usr/libexec/java_home -v 21 --failfast 2>/dev/null || echo "$(STUDIO_JBR)")
export JAVA_HOME
export ANDROID_HOME := $(ANDROID_SDK)

GRADLE := ./gradlew --console=plain

local.properties:
	@echo "sdk.dir=$(ANDROID_SDK)" > $@

doctor: ## toolchain + device check (API >= 36)
	@echo "JAVA_HOME=$(JAVA_HOME)"; "$(JAVA_HOME)/bin/java" -version 2>&1 | head -1
	@test -d "$(ANDROID_SDK)/platforms/android-37.0" && echo "platform android-37.0: ok" || echo "platform android-37.0: MISSING"
	@test -d "$(ANDROID_SDK)/build-tools/36.0.0" && echo "build-tools 36.0.0: ok" || echo "build-tools 36.0.0: MISSING"
	@"$(ADB)" devices -l
	@for s in $$("$(ADB)" devices | awk 'NR>1 && $$2=="device"{print $$1}'); do \
	  api=$$("$(ADB)" -s $$s shell getprop ro.build.version.sdk | tr -d '\r'); \
	  if [ "$$api" -ge 36 ]; then echo "$$s API $$api: ok"; else echo "$$s API $$api: TOO OLD (minSdk 36)"; fi; done

install: local.properties ## build + install release (APK + .dm) + launch
	@test -n "$$("$(ADB)" devices | awk 'NR>1 && $$2=="device"')" || { echo "No device. Enable USB/Wireless debugging; see 'make doctor'."; exit 1; }
	$(GRADLE) :app:installRelease
	"$(ADB)" shell am start -n $(APP_ID)/.MainActivity

check: local.properties ## what CI would run
	$(GRADLE) spotlessCheck :core:markdown:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```
Notes:
- `java_home -v 21 --failfast` prints nothing and exits 1 when no JDK 21 is registered. Without `--failfast` it prints to stderr only, but keep the flag.
- The Studio JBR path contains spaces. Quote it everywhere, as done above.
- Measured timings on this Mac (M-series): cold first `assembleDebug` 56 s, incremental `assembleRelease` about 34 s, a no-op build with CC reuse 0.6 s, `make check` warm 14–27 s.

---

## 12. Code quality tooling

- **Chosen: Spotless 8.10.2 + ktlint 1.8.0 + `io.nlopez.compose.rules:ktlint:0.6.6`**.
  - Spotless's current default ktlint is 1.8.0 (plugin-gradle CHANGES: "Bump default ktlint version to latest 1.7.1 -> 1.8.0"). The KtLintStep coordinate is `com.pinterest.ktlint:ktlint-cli:`.
  - Verified on Kotlin 2.4.20 sources: `spotlessCheck` caught an import-order violation, `spotlessApply` fixed it, and the compose rule set is active (`ktlint(compose:modifier-missing-check) This @Composable function emits content but doesn't have a modifier parameter`).
  - CC-compatible.
- ktlint status: 1.8.0 (2025-11-14) is the latest **stable**. The project is no longer maintained by Pinterest and has moved to `github.com/ktlint/ktlint`. 2.0.0-ALPHA-4 (2026-08-21) changes Maven coordinates to `io.github.ktlint`, but nothing is published there on Maven Central yet (the metadata URL returns 404). Stay on 1.8.0, and expect a coordinate change when 2.0 goes stable.
- **detekt: not recommended now**. 1.23.8 (Feb 2025) is built against Kotlin 2.0.21, Gradle 8.12.1 and AGP 8.8.1, per detekt.dev/docs/introduction/compatibility. Only 2.0.0-alpha.6 (built against Kotlin 2.4.10, AGP 9.3.1) supports current toolchains. The 2.0.0-alpha.3 notes say the workaround of disabling newDsl and builtInKotlin for AGP 9 "is no longer required", which implies 1.23.x needs those opt-outs, and that conflicts with built-in Kotlin. Revisit when detekt 2.0 is stable.
- **Android Lint**: config in 8.5 (`abortOnError=true`, `warningsAsErrors=false`, `checkDependencies=true`, version-nag checks disabled). The release build's `lintVital` task runs automatically during `assembleRelease`/`installRelease`, so fatal issues block install, which is good. Optionally add `baseline = file("lint-baseline.xml")` once the codebase grows.
- The `ktlint_function_naming_ignore_when_annotated_with = Composable` line in `.editorconfig` prevents ktlint's function-naming rule from firing on PascalCase composables.

---

## 13. Verification log (commands actually run today)

| # | What | Result |
|---|---|---|
| 1 | `mvn.sh` curl of 90+ `maven-metadata.xml` files | versions in section 6 |
| 2 | Wrapper bootstrap via cached Gradle 8.7, run twice | Gradle 9.7.1, jar sha matches official |
| 3 | `updateDaemonJvm --jvm-version=21` without, then with, foojay | fails, then succeeds |
| 4 | `:app:assembleDebug` (AGP 9.4.1, Kotlin 2.4.20, compileSdk 37, minSdk 36) | BUILD SUCCESSFUL 56 s (cold) |
| 5 | `buildEnvironment` | `kotlin-gradle-plugin:2.2.10 -> 2.4.20` |
| 6 | `:core:markdown:test` (JUnit 6.1.3) | 1/1 pass |
| 7 | `:app:testDebugUnitTest` (Robolectric 4.17 + Compose v2 rule) | failed until the jvmArgs and espresso 3.7.0 fixes, then 1/1 pass |
| 8 | `:app:assembleRelease` with `optimization { enable = true }` | 1.9 MB APK, mapping present, `.dm` baseline profiles produced |
| 9 | `aapt2 dump badging`, `apksigner verify` | min 36 / target 37 / compile 37; debug-cert signed |
| 10 | `:app:installRelease` on emulator (API 37) + `am start` + `dumpsys package` | installed APK + .dm; `speed-profile / install-dm`; cold start about 1.05 s; no crash |
| 11 | `ANDROID_SERIAL=emulator-5554 make install` with 2 emulators | installed only on 5554 |
| 12 | `:app:lintDebug :core:markdown:lint` with `com.android.lint` | success, 2 warnings (backup rules, icon) |
| 13 | `spotlessCheck` / `spotlessApply` / compose-rule probe | works; compose rule fires |
| 14 | Second `assembleDebug` | "Configuration cache entry reused", 0.6 s |
| 15 | `--warning-mode all` builds | zero deprecation warnings |
| 16 | Legacy int SDK DSL | builds, no deprecation |
| 17 | AGP **9.3.3**: `clean assembleRelease testDebugUnitTest core:test` and `make check` | BUILD SUCCESSFUL |
| 18 | Build without `local.properties` / ANDROID_HOME | "SDK location not found" |
| 19 | `javap` on outputs | class major 61 (Java 17) |
| 20 | Removed `junit-platform-launcher` | "Failed to load JUnit Platform" |
| 21 | SDK dir timestamps after all builds | unchanged (no auto-downloads) |

The test app (`dev.mdwriter.tctest`) was uninstalled from the emulator afterwards.

---

## 14. Risks & open questions

1. **Studio vs AGP**: with AGP 9.4.1, the installed Studio Quail 3 would refuse to sync (the table caps it at 9.3). Default to 9.3.3. The plan should include an optional step: "update Studio to Quail 4 (2026.1.4 Patch 1), then set `agp = "9.4.1"`". I did not do an actual IDE sync test, so that part is **UNVERIFIED**, but the table is official.
2. The **tested-matrix edge**: KGP 2.4.20 is officially tested up to AGP 9.3.1 and Gradle 9.7.0. We use patch releases above both (9.3.3 and 9.7.1). This worked cleanly, but it is technically outside the stated range. 9.4.1 is further outside it.
3. **User's phone OS version is unknown**. minSdk 36 excludes Android 15 devices. `make doctor` guards against this.
4. The **Android 17 stable date** (2026-06-16) comes from secondary sources. The final status of the API 37.0 platform package itself is verified from the SDK metadata.
5. **ktlint 2.0** will change Maven coordinates, and Spotless will need an update then. Material3 1.5.0 has been in alpha for about a year; stay on the BOM (1.4.0).
6. **Robolectric** needs network on the first run to fetch android-all-instrumented for SDK 37.
7. **Custom baseline profile** generation needs a device or emulator. It is deferred and optional.
8. **Signing-key choice** must be final before the user's first real install.
9. Studio Quail 3's Kotlin IDE plugin with Kotlin 2.4.20: its IDE support level is **UNVERIFIED**. CLI builds are unaffected.

---

## 15. Sources (all fetched 2026-09-24)
- AGP 9.4 release notes (compat box, max API 37, JDK 17, Gradle 9.6.0): https://developer.android.com/build/releases/gradle-plugin
- AGP 9.3 release notes (optimization DSL, keepRules, 9.3.3 fixes): https://developer.android.com/build/releases/agp-9-3-0-release-notes
- AGP 9.2 / 9.1 / 9.0 release notes: https://developer.android.com/build/releases/agp-9-2-0-release-notes , https://developer.android.com/build/releases/agp-9-1-0-release-notes , https://developer.android.com/build/releases/agp-9-0-0-release-notes
- AGP 8.0 defaults (nonTransitiveRClass, nonFinalResIds, R8 full mode): https://developer.android.com/build/releases/past-releases/agp-8-0-0-release-notes
- Studio↔AGP table, AGP→Gradle table, min tools per API level: https://developer.android.com/build/releases/about-agp
- Studio download page (Quail 4, 2026.1.4 Patch 1): https://developer.android.com/studio/releases
- Built-in Kotlin migration: https://developer.android.com/build/migrate-to-built-in-kotlin
- Kotlin↔AGP/R8 table: https://developer.android.com/build/kotlin-support
- JDK guidance / Daemon JVM criteria in Studio: https://developer.android.com/build/jdks
- New SDK DSL example: https://developer.android.com/build/gradle-build-overview
- Android 17 SDK setup: https://developer.android.com/about/versions/17/setup-sdk ; behavior changes: https://developer.android.com/about/versions/17/behavior-changes-17 ; release notes: https://developer.android.com/about/versions/17/release-notes ; date (secondary): https://en.wikipedia.org/wiki/Android_17
- R8 / optimization DSL: https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- SDK auto-download & licenses: https://developer.android.com/studio/intro/update
- SDK repository: https://dl.google.com/android/repository/repository2-3.xml
- Compose material / material3 release notes (icons deprecation, versions): https://developer.android.com/jetpack/androidx/releases/compose-material , https://developer.android.com/jetpack/androidx/releases/compose-material3
- Compose BOM POM: https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/2026.09.00/compose-bom-2026.09.00.pom
- Navigation 3 release notes & setup: https://developer.android.com/jetpack/androidx/releases/navigation3 , https://developer.android.com/guide/navigation/navigation-3/get-started
- Activity / DataStore / Benchmark release notes: https://developer.android.com/jetpack/androidx/releases/activity , https://developer.android.com/jetpack/androidx/releases/datastore , https://developer.android.com/jetpack/androidx/releases/benchmark
- Gradle current version & checksums: https://services.gradle.org/versions/current , https://services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256 , https://services.gradle.org/distributions/gradle-9.7.1-wrapper.jar.sha256
- Gradle compatibility (JVM 17–26, tested Kotlin/AGP): https://docs.gradle.org/current/userguide/compatibility.html
- Gradle Daemon JVM criteria: https://docs.gradle.org/current/userguide/gradle_daemon.html
- Gradle configuration cache default: https://docs.gradle.org/current/userguide/configuration_cache_enabling.html
- Wrapper jar at tag: https://raw.githubusercontent.com/gradle/gradle/v9.7.1/gradle/wrapper/gradle-wrapper.jar
- Kotlin KGP compat: https://kotlinlang.org/docs/gradle-configure-project.html ; Kotlin 2.4.20 what's new: https://kotlinlang.org/docs/whatsnew2420.html ; GitHub release v2.4.20 (2026-09-07)
- Compose compiler Gradle plugin module metadata: https://repo1.maven.org/maven2/org/jetbrains/kotlin/compose-compiler-gradle-plugin/2.4.20/compose-compiler-gradle-plugin-2.4.20.module
- Robolectric 4.17 release (supports SDK 37): https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17 ; JVM flags: https://robolectric.org/getting-started/
- ktlint releases: https://github.com/pinterest/ktlint/releases (1.8.0; 2.0.0-ALPHA-4 now under ktlint/ktlint)
- Spotless changelog & KtLintStep: https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md , https://github.com/diffplug/spotless/blob/main/lib/src/main/java/com/diffplug/spotless/kotlin/KtLintStep.java
- detekt releases & compatibility: https://github.com/detekt/detekt/releases , https://detekt.dev/docs/introduction/compatibility/
- Local Studio templates (daemon JVM templates, foojay 1.0.0, gradle.properties template): `/Applications/Android Studio.app/Contents/plugins/android/lib/android.jar` (`templates/project/toolchain/*`, `AndroidProjectGradlePropertiesKt.class`)
