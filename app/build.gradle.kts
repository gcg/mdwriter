import java.util.Properties

// Verified on AGP 9.3.3 / Gradle 9.7.1 / Kotlin 2.4.20 (plans/research/toolchain.md §8.5 + build.md §4).
// NOTE: no org.jetbrains.kotlin.android plugin — AGP 9 has built-in Kotlin. Applying it FAILS the build.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------- versioning
// Release versionCode = minutes since 2026-01-01T00:00:00Z, computed when the manifest task RUNS
// (a ValueSource wired lazily through the Variant API), so it is fresh on every build AND the
// configuration cache is still reused. Override: -Pmdwriter.versionCode=<int>  (make install VERSION_CODE=...).
// Never .get() these providers at configuration time (that would defeat the configuration cache).
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
// storeFile may be relative (resolved against the properties file's directory). `make keystore` creates it.
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
    namespace = "dev.mdwriter"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // The Makefile parses this line: keep applicationId a literal string on ONE line.
        applicationId = "dev.mdwriter"
        minSdk { version = release(36) }
        targetSdk { version = release(37) }
        versionCode = 1 // debug stays at 1 (keeps debug builds incremental); release is set in androidComponents below
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            applicationIdSuffix = ".debug" // debug and release install side by side with separate notes
            versionNameSuffix = "-debug"
        }
        release {
            optimization {
                enable = true // AGP >= 9.3: R8 code + resource optimization with default keep rules
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17 // Kotlin jvmTarget follows this automatically
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", // REQUIRED for Robolectric 4.17 on SDK 37
                )
            }
        }
    }

    dependenciesInfo {
        // No Play dependency blob in a sideloaded, privacy-first app.
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.versionCode.set(releaseVersionCode)
            output.versionName.set(gitShortSha.map { sha -> if (sha.isEmpty()) "0.1.0" else "0.1.0+$sha" })
        }
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
    implementation(libs.androidx.webkit) // Preview (T16)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
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
    // REQUIRED: forces espresso 3.7.0 over the transitive 3.5.0, which breaks Robolectric on SDK 37.
    testImplementation(libs.androidx.test.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.truth)
}
