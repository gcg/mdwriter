package dev.mdwriter.data.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashBinTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun flatJsonRoundTripsTrickyStrings() {
        val map =
            mapOf(
                "quote" to "a\"b",
                "backslash" to "a\\b",
                "newline" to "a\nb",
                "tab" to "a\tb",
                "nonAscii" to "café",
                "emoji" to "hi 😀 bye",
            )
        val written = FlatJson.write(map)
        assertThat(FlatJson.read(written)).isEqualTo(map)
    }

    @Test
    fun moveInGetRemove() {
        val root = tmp.newFolder("trash")
        val bin = TrashBin(root)
        val source = tmp.newFolder("src")
        val file = File(source, "Walk.md")
        file.writeText("hello")

        val id =
            bin.moveIn(
                file,
                mapOf("displayName" to "Walk.md", "deletedAt" to "1000", "source" to "internal"),
            )

        assertThat(file.exists()).isFalse()
        val entry = bin.get(id)
        assertThat(entry).isNotNull()
        assertThat(entry!!.displayName).isEqualTo("Walk.md")
        assertThat(entry.deletedAt).isEqualTo(1000L)
        assertThat(entry.payload.readText()).isEqualTo("hello")

        bin.remove(id)
        assertThat(bin.get(id)).isNull()
    }

    @Test
    fun copyIn() {
        val root = tmp.newFolder("trash")
        val bin = TrashBin(root)
        val id =
            bin.copyIn(
                "Note.md",
                "bytes".toByteArray(),
                mapOf(
                    "displayName" to "Note.md",
                    "deletedAt" to "5",
                    "source" to "tree",
                ),
            )
        val entry = bin.get(id)
        assertThat(entry!!.payload.readText()).isEqualTo("bytes")
    }

    @Test
    fun purgeOlderThanDeletesOnlyOldEntries() {
        val root = tmp.newFolder("trash")
        val bin = TrashBin(root)
        val srcOld = File(tmp.newFolder("old"), "Old.md").apply { writeText("old") }
        val srcNew = File(tmp.newFolder("new"), "New.md").apply { writeText("new") }
        val oldId = bin.moveIn(srcOld, mapOf("displayName" to "Old.md", "deletedAt" to "1000", "source" to "internal"))
        val newId = bin.moveIn(srcNew, mapOf("displayName" to "New.md", "deletedAt" to "9000", "source" to "internal"))

        val deleted = bin.purgeOlderThan(cutoffMillis = 5000)

        assertThat(deleted).isEqualTo(1)
        assertThat(bin.get(oldId)).isNull()
        assertThat(bin.get(newId)).isNotNull()
    }

    @Test
    fun dirWithoutMetaIsIgnoredByGetAndDeletedByPurge() {
        val root = tmp.newFolder("trash")
        val bin = TrashBin(root)
        val corrupt = File(root, "corrupt-id")
        corrupt.mkdirs()
        File(corrupt, "SomeFile.md").writeText("x") // no meta.json

        assertThat(bin.get("corrupt-id")).isNull()

        val deleted = bin.purgeOlderThan(cutoffMillis = Long.MAX_VALUE)

        assertThat(deleted).isEqualTo(1)
        assertThat(corrupt.exists()).isFalse()
    }
}
