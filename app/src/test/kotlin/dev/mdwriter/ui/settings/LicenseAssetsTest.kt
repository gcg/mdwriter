package dev.mdwriter.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class LicenseAssetsTest {
    private fun read(name: String): String {
        val f = File("src/main/assets/licenses/$name")
        assertThat(f.exists()).isTrue()
        assertThat(f.length()).isGreaterThan(500)
        return f.readText()
    }

    @Test
    fun ofl() = assertThat(read("iA-Writer-fonts-OFL.txt")).contains("SIL OPEN FONT LICENSE")

    @Test
    fun bsd() = assertThat(read("commonmark-java-BSD-2-Clause.txt")).contains("Redistribution and use")

    @Test
    fun mit() = assertThat(read("autolink-java-MIT.txt")).contains("Permission is hereby granted, free of charge")

    @Test
    fun apache() {
        val t = read("Apache-2.0.txt")
        assertThat(t).contains("Apache License")
        assertThat(t).contains("Version 2.0")
    }

    @Test
    fun everyNoticeAssetExists() {
        LicenseNotices.forEach { assertThat(File("src/main/assets/${it.assetPath}").exists()).isTrue() }
    }
}
