package dev.mdwriter.data.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UniqueNameTest {
    @Test
    fun splitNameRecognizesNoteExtensions() {
        assertThat(UniqueName.splitName("Groceries.md")).isEqualTo("Groceries" to "md")
        assertThat(UniqueName.splitName("Groceries.MD")).isEqualTo("Groceries" to "md")
        assertThat(UniqueName.splitName("notes.backup")).isEqualTo("notes.backup" to null)
        assertThat(UniqueName.splitName("noextension")).isEqualTo("noextension" to null)
        assertThat(UniqueName.splitName(".hidden")).isEqualTo(".hidden" to null)
    }

    @Test
    fun numberedPicksFirstFreeSlot() {
        assertThat(UniqueName.numbered("Untitled", "md", emptySet())).isEqualTo("Untitled.md")
        assertThat(UniqueName.numbered("Untitled", "md", setOf("untitled.md"))).isEqualTo("Untitled 2.md")
        assertThat(
            UniqueName.numbered("Untitled", "md", setOf("untitled.md", "untitled 2.md")),
        ).isEqualTo("Untitled 3.md")
    }

    @Test
    fun numberedIsCaseInsensitive() {
        // [taken] holds LOWER-CASED sibling names, per the contract; the candidate itself is lower-cased to compare.
        assertThat(UniqueName.numbered("Notes", "md", setOf("notes.md"))).isEqualTo("Notes 2.md")
    }

    @Test
    fun copyOfAddsCopySuffix() {
        assertThat(UniqueName.copyOf("Groceries", "md", emptySet())).isEqualTo("Groceries copy.md")
        assertThat(UniqueName.copyOf("Groceries", "md", setOf("groceries copy.md"))).isEqualTo("Groceries copy 2.md")
    }
}
