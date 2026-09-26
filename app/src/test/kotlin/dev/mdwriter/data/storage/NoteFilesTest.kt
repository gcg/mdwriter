package dev.mdwriter.data.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NoteFilesTest {
    @Test
    fun supportedExtensions() {
        for (ext in listOf("md", "markdown", "mdown", "mkd", "txt")) {
            assertThat(NoteFiles.isSupported("Note.$ext")).isTrue()
        }
    }

    @Test
    fun uppercaseExtensionIsSupported() {
        assertThat(NoteFiles.isSupported("Note.MD")).isTrue()
        assertThat(NoteFiles.extensionOf("Note.MD")).isEqualTo("md")
    }

    @Test
    fun hiddenFileIsNotSupportedEvenWithMdExtension() {
        assertThat(NoteFiles.isHidden(".hidden.md")).isTrue()
        assertThat(NoteFiles.isSupported(".hidden.md")).isFalse()
    }

    @Test
    fun unsupportedExtension() {
        assertThat(NoteFiles.isSupported("notes.docx")).isFalse()
    }

    @Test
    fun dotfileWithNoExtensionIsNotAnExtension() {
        assertThat(NoteFiles.extensionOf(".md")).isEqualTo("")
        assertThat(NoteFiles.isHidden(".md")).isTrue()
    }

    @Test
    fun mimeForTable() {
        assertThat(NoteFiles.mimeFor("a.md")).isEqualTo("text/markdown")
        assertThat(NoteFiles.mimeFor("a.markdown")).isEqualTo("text/markdown")
        assertThat(NoteFiles.mimeFor("a.txt")).isEqualTo("text/plain")
        assertThat(NoteFiles.mimeFor("a.mdown")).isEqualTo("application/octet-stream")
        assertThat(NoteFiles.mimeFor("a.mkd")).isEqualTo("application/octet-stream")
    }

    @Test
    fun uniqueNameAddsSuffix() {
        assertThat(NoteFiles.uniqueName("Untitled.md", setOf("untitled.md", "Untitled 2.md")))
            .isEqualTo("Untitled 3.md")
    }

    @Test
    fun uniqueNameNotTakenReturnsAsIs() {
        assertThat(NoteFiles.uniqueName("Walk.md", setOf("Other.md"))).isEqualTo("Walk.md")
    }

    @Test
    fun uniqueNameNoExtension() {
        assertThat(NoteFiles.uniqueName("Drafts", setOf("drafts"))).isEqualTo("Drafts 2")
    }

    @Test
    fun baseNameStripsExtension() {
        assertThat(NoteFiles.baseName("Walk.md")).isEqualTo("Walk")
        assertThat(NoteFiles.baseName("Walk")).isEqualTo("Walk")
    }

    @Test
    fun decodeHeadStripsBom() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + "hello".toByteArray()
        assertThat(NoteFiles.decodeHead(bytes, bytes.size)).isEqualTo("hello")
    }

    @Test
    fun decodeHeadDropsTrailingCutMultiByteChar() {
        // "café" ends with a 2-byte UTF-8 char (0xC3 0xA9); cut off the last byte.
        val full = "café".toByteArray()
        val cut = full.copyOf(full.size - 1)
        assertThat(NoteFiles.decodeHead(cut, cut.size)).isEqualTo("caf")
    }
}
