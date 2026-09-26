package dev.mdwriter.data.document

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime

class ConflictNamesTest {
    @Test
    fun formatsConflictSuffix() {
        val name = ConflictNames.name("Walk", "md", LocalDateTime.of(2026, 9, 25, 14, 2))
        assertThat(name).isEqualTo("Walk (conflict 2026-09-25 1402).md")
    }

    @Test
    fun emptyExtensionGivesNoDot() {
        val name = ConflictNames.name("Walk", "", LocalDateTime.of(2026, 9, 25, 14, 2))
        assertThat(name).isEqualTo("Walk (conflict 2026-09-25 1402)")
    }
}
