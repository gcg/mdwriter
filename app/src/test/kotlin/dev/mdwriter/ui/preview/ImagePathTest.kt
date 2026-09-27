package dev.mdwriter.ui.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Acceptance 2. */
class ImagePathTest {
    @Test
    fun splitDropsDotAndEmptySegments() {
        assertThat(ImagePath.split("img/a.png")).containsExactly("img", "a.png").inOrder()
        assertThat(ImagePath.split("a//./b.png")).containsExactly("a", "b.png").inOrder()
    }

    @Test
    fun splitRejectsBackslash() {
        assertThat(ImagePath.split("a\\b.png")).isNull()
    }

    @Test
    fun splitRejectsAbsolutePaths() {
        assertThat(ImagePath.split("/abs.png")).isNull()
    }

    @Test
    fun splitRejectsNul() {
        assertThat(ImagePath.split("a\u0000b.png")).isNull()
    }

    @Test
    fun splitKeepsDotDot() {
        assertThat(ImagePath.split("../a.png")).containsExactly("..", "a.png").inOrder()
    }

    // A simple string-node tree for walk(): "root/notes/img/x.png" etc. child() looks the name up directly.
    private val tree =
        mapOf(
            "root" to setOf("notes", "img"),
            "root/notes" to setOf("img"),
            "root/notes/img" to setOf("x.png"),
            "root/img" to setOf("x.png"),
        )

    private fun childOf(
        parent: String,
        name: String,
    ): String? = if (tree[parent]?.contains(name) == true) "$parent/$name" else null

    @Test
    fun walkEscapingPastBaseIsNull() {
        val base = listOf("root", "root/notes")
        val result = ImagePath.walk(base, listOf("..", "..", "x.png"), ::childOf)
        assertThat(result).isNull()
    }

    @Test
    fun walkResolvesUnderRootAfterPoppingOnce() {
        val base = listOf("root", "root/notes")
        val result = ImagePath.walk(base, listOf("..", "img", "x.png"), ::childOf)
        assertThat(result).isEqualTo("root/img/x.png")
    }

    @Test
    fun walkOnEmptyBaseIsNull() {
        assertThat(ImagePath.walk<String>(emptyList(), listOf("x.png"), ::childOf)).isNull()
    }

    @Test
    fun walkMissingChildIsNull() {
        val base = listOf("root")
        assertThat(ImagePath.walk(base, listOf("missing.png"), ::childOf)).isNull()
    }

    @Test
    fun imageMimeIsCaseInsensitive() {
        assertThat(ImagePath.imageMime("A.PNG")).isEqualTo("image/png")
        assertThat(ImagePath.imageMime("a.jpg")).isEqualTo("image/jpeg")
        assertThat(ImagePath.imageMime("a.jpeg")).isEqualTo("image/jpeg")
        assertThat(ImagePath.imageMime("a.svg")).isEqualTo("image/svg+xml")
    }

    @Test
    fun imageMimeIsNullForNonImages() {
        assertThat(ImagePath.imageMime("notes.md")).isNull()
        assertThat(ImagePath.imageMime("noext")).isNull()
        assertThat(ImagePath.imageMime("trailing.")).isNull()
    }
}
