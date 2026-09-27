package dev.mdwriter.data.export

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipInputStream

class ExportAllNotesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun zipEntries(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(bytes.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                entries += entry.name to zin.readBytes()
                entry = zin.nextEntry
            }
        }
        return entries
    }

    @Test
    fun keepsFolderStructure() {
        val root = tmp.newFolder("library")
        File(root, "Drafts").mkdirs()
        File(root, "Drafts/b.md").writeText("body")
        File(root, "a.md").writeText("root file")

        val out = ByteArrayOutputStream()
        ExportAllNotes.writeZip(root, out)
        val names = zipEntries(out.toByteArray()).map { it.first }
        assertThat(names).contains("mdwriter-notes/Drafts/b.md")
        assertThat(names).contains("mdwriter-notes/a.md")
    }

    @Test
    fun skipsHidden() {
        val root = tmp.newFolder("library")
        File(root, "a.md").writeText("visible")
        File(root, ".x.tmp").writeText("hidden temp file")
        File(root, ".hiddenFolder").mkdirs()
        File(root, ".hiddenFolder/c.md").writeText("hidden folder contents")

        val out = ByteArrayOutputStream()
        val summary = ExportAllNotes.writeZip(root, out)
        val names = zipEntries(out.toByteArray()).map { it.first }
        assertThat(names).containsExactly("mdwriter-notes/a.md")
        assertThat(summary.files).isEqualTo(1)
    }

    @Test
    fun emptyFolderEntry() {
        val root = tmp.newFolder("library")
        File(root, "Drafts").mkdirs()

        val out = ByteArrayOutputStream()
        ExportAllNotes.writeZip(root, out)
        val names = zipEntries(out.toByteArray()).map { it.first }
        assertThat(names).contains("mdwriter-notes/Drafts/")
    }

    @Test
    fun bytesIdentical() {
        val root = tmp.newFolder("library")
        // BOM + CRLF, a genuine round-trip-sensitive file.
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "line1\r\nline2\r\n".toByteArray()
        File(root, "a.md").writeBytes(bytes)

        val out = ByteArrayOutputStream()
        ExportAllNotes.writeZip(root, out)
        val entry = zipEntries(out.toByteArray()).single { it.first == "mdwriter-notes/a.md" }
        assertThat(entry.second).isEqualTo(bytes)
    }

    @Test
    fun progressReachesTotal() {
        val root = tmp.newFolder("library")
        File(root, "a.md").writeText("a")
        File(root, "b.md").writeText("b")
        File(root, "c.md").writeText("c")

        var lastDone = 0
        var lastTotal = 0
        ExportAllNotes.writeZip(root, ByteArrayOutputStream()) { done, total ->
            lastDone = done
            lastTotal = total
        }
        assertThat(lastTotal).isEqualTo(3)
        assertThat(lastDone).isEqualTo(lastTotal)
    }

    @Test
    fun suggestedNameIsIsoDate() {
        assertThat(ExportAllNotes.suggestedName(LocalDate.of(2026, 9, 27))).isEqualTo("mdwriter-notes-2026-09-27.zip")
    }
}
