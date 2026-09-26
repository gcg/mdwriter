plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false // also lifts AGP's bundled KGP 2.2.10 -> 2.4.20
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
