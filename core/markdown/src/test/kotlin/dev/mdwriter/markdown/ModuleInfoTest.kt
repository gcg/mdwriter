package dev.mdwriter.markdown

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ModuleInfoTest {
    @Test
    fun placeholderIsWired() {
        assertThat(ModuleInfo.NAME).isEqualTo("core-markdown")
    }
}
