package dev.mdwriter.ui.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Acceptance 4 (second half). */
class PreviewSyncTest {
    private val html =
        "<h1 id=\"a\">A</h1><p>x</p><h2 id=\"b\">B</h2><h3 id=\"c-c\">C c</h3><h2 id=\"d\">D</h2>"

    @Test
    fun anchorIdsAreInDocumentOrder() {
        assertThat(PreviewSync.anchorIds(html)).containsExactly("a", "b", "c-c", "d").inOrder()
    }

    @Test
    fun thirdHeadingIdIsReturnedForIndex2() {
        assertThat(PreviewSync.anchorFor(html, 2)).isEqualTo("c-c")
    }

    @Test
    fun negativeIndexIsNull() {
        assertThat(PreviewSync.anchorFor(html, -1)).isNull()
    }

    @Test
    fun outOfRangeIndexIsNull() {
        assertThat(PreviewSync.anchorFor(html, 40)).isNull()
    }
}
