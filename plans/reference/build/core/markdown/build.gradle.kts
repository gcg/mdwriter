import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: NO Android types allowed here (so tests run in ~1 s on the JVM).
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
    // Preview/export HTML renderer (MarkdownHtml.kt). The live highlighter itself has zero dependencies.
    implementation(libs.bundles.commonmark)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher) // REQUIRED on Gradle 9 ("Failed to load JUnit Platform" otherwise)
    testImplementation(libs.truth)
}

tasks.test {
    useJUnitPlatform()
}
