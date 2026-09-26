package dev.mdwriter.data.storage

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class InternalStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 1_000_000L

    private fun newStore(): InternalStore {
        val root = tmp.newFolder("library")
        val trashRoot = tmp.newFolder(".trash")
        return InternalStore(root = root, trash = TrashBin(trashRoot), clock = { now })
    }

    @Test
    fun createThreeUntitledGivesCollisionNames() =
        runTest {
            val store = newStore()
            val a = store.create(FolderRef.INTERNAL_ROOT, "Untitled.md")
            val b = store.create(FolderRef.INTERNAL_ROOT, "Untitled.md")
            val c = store.create(FolderRef.INTERNAL_ROOT, "Untitled.md")
            assertThat((a as DocRef.InternalFile).relPath).isEqualTo("Untitled.md")
            assertThat((b as DocRef.InternalFile).relPath).isEqualTo("Untitled 2.md")
            assertThat((c as DocRef.InternalFile).relPath).isEqualTo("Untitled 3.md")
        }

    @Test
    fun listHidesDotfilesTempFilesAndUnsupported() =
        runTest {
            val store = newStore()
            store.create(FolderRef.INTERNAL_ROOT, "Note.md")
            File(store.root, ".Note.md.123.tmp").writeText("x")
            File(store.root, ".obsidian").mkdir()
            File(store.root, "x.docx").writeText("x")

            val entries = store.list(FolderRef.INTERNAL_ROOT)

            assertThat(entries.map { it.name }).containsExactly("Note.md")
        }

    @Test
    fun listReturnsFoldersFirstThenNewestFirst() =
        runTest {
            val store = newStore()
            val fileA = store.create(FolderRef.INTERNAL_ROOT, "A.md") as DocRef.InternalFile
            File(store.root, fileA.relPath).setLastModified(1000)
            val fileB = store.create(FolderRef.INTERNAL_ROOT, "B.md") as DocRef.InternalFile
            File(store.root, fileB.relPath).setLastModified(2000)
            store.createFolder(FolderRef.INTERNAL_ROOT, "ZFolder")
            store.createFolder(FolderRef.INTERNAL_ROOT, "AFolder")

            val names = store.list(FolderRef.INTERNAL_ROOT).map { it.name }

            assertThat(names).containsExactly("AFolder", "ZFolder", "B.md", "A.md").inOrder()
        }

    @Test
    fun excerptReadsOnlyFirst2Kb() =
        runTest {
            val store = newStore()
            // First file: short title line, body ("Body line") well within the first 2 KB.
            val short = File(store.root, "Short.md")
            short.writeText("# Title\n\nBody line\n" + "x".repeat(2 * 1024 * 1024))
            // Second file: the title line alone is longer than 4096 bytes, so the body starts after byte 4096.
            val long = File(store.root, "Long.md")
            long.writeText("# " + "T".repeat(4096) + "\n\nBody line\n")

            val entries = store.list(FolderRef.INTERNAL_ROOT).associateBy { it.name }

            assertThat(entries.getValue("Short.md").excerpt).isEqualTo("Body line")
            assertThat(entries.getValue("Long.md").excerpt).isNull()
        }

    @Test
    fun nestedFoldersCreateListMove() =
        runTest {
            val store = newStore()
            val drafts = store.createFolder(FolderRef.INTERNAL_ROOT, "Drafts")
            val doc = store.create(drafts, "Walk.md") as DocRef.InternalFile
            assertThat(doc.relPath).isEqualTo("Drafts/Walk.md")
            assertThat(store.list(drafts).map { it.name }).containsExactly("Walk.md")

            val moved = store.move(doc, FolderRef.INTERNAL_ROOT) as DocRef.InternalFile

            assertThat(moved.relPath).isEqualTo("Walk.md")
            assertThat(store.list(drafts)).isEmpty()
            assertThat(store.list(FolderRef.INTERNAL_ROOT).map { it.name }).contains("Walk.md")
        }

    @Test
    fun renameReturnsNewRefAndOldIsGone() =
        runTest {
            val store = newStore()
            val doc = store.create(FolderRef.INTERNAL_ROOT, "Walk.md") as DocRef.InternalFile

            val renamed = store.rename(doc, "Hike.md") as DocRef.InternalFile

            assertThat(renamed.relPath).isEqualTo("Hike.md")
            assertThat(File(store.root, doc.relPath).exists()).isFalse()
            assertThat(File(store.root, renamed.relPath).exists()).isTrue()
        }

    @Test
    fun renameCaseOnly() =
        runTest {
            val store = newStore()
            val doc = store.create(FolderRef.INTERNAL_ROOT, "walk.md") as DocRef.InternalFile

            val renamed = store.rename(doc, "Walk.md") as DocRef.InternalFile

            assertThat(renamed.relPath).isEqualTo("Walk.md")
            assertThat(File(store.root, "Walk.md").exists()).isTrue()
        }

    @Test
    fun renameCollisionGetsSuffix() =
        runTest {
            val store = newStore()
            store.create(FolderRef.INTERNAL_ROOT, "Hike.md")
            val doc = store.create(FolderRef.INTERNAL_ROOT, "Walk.md") as DocRef.InternalFile

            val renamed = store.rename(doc, "Hike.md") as DocRef.InternalFile

            assertThat(renamed.relPath).isEqualTo("Hike 2.md")
        }

    @Test
    fun trashMovesFileAndWritesMeta() =
        runTest {
            val store = newStore()
            val doc = store.create(FolderRef.INTERNAL_ROOT, "Walk.md") as DocRef.InternalFile

            val token = store.trash(doc)

            assertThat(File(store.root, doc.relPath).exists()).isFalse()
            val entry = TrashBin(File(tmp.root, ".trash")).get(token.trashId)
            assertThat(entry!!.meta["originalRelPath"]).isEqualTo("Walk.md")
            assertThat(entry.meta["deletedAt"]).isEqualTo(now.toString())
        }

    @Test
    fun restorePutsFileBackEvenIfFolderWasDeleted() =
        runTest {
            val store = newStore()
            val drafts = store.createFolder(FolderRef.INTERNAL_ROOT, "Drafts")
            val doc = store.create(drafts, "Walk.md") as DocRef.InternalFile
            val token = store.trash(doc)
            File(store.root, "Drafts").delete()

            val restored = store.restore(token) as DocRef.InternalFile

            assertThat(restored.relPath).isEqualTo("Drafts/Walk.md")
            assertThat(File(store.root, restored.relPath).exists()).isTrue()
        }

    @Test
    fun restoreWithNameTakenUsesUniqueName() =
        runTest {
            val store = newStore()
            val doc = store.create(FolderRef.INTERNAL_ROOT, "Walk.md") as DocRef.InternalFile
            val token = store.trash(doc)
            store.create(FolderRef.INTERNAL_ROOT, "Walk.md") // a new file now occupies the original name

            val restored = store.restore(token) as DocRef.InternalFile

            assertThat(restored.relPath).isEqualTo("Walk 2.md")
        }

    @Test
    fun purgeRemovesEntriesOlderThan30Days() =
        runTest {
            val store = newStore()
            val oldDoc = store.create(FolderRef.INTERNAL_ROOT, "Old.md") as DocRef.InternalFile
            val oldToken = store.trash(oldDoc)
            now += 31L * 24 * 60 * 60 * 1000
            val newDoc = store.create(FolderRef.INTERNAL_ROOT, "New.md") as DocRef.InternalFile
            val newToken = store.trash(newDoc)

            val deleted = store.purgeTrash()

            assertThat(deleted).isEqualTo(1)
            val trashBin = TrashBin(File(tmp.root, ".trash"))
            assertThat(trashBin.get(oldToken.trashId)).isNull()
            assertThat(trashBin.get(newToken.trashId)).isNotNull()
        }

    @Test
    fun pathTraversalRejected() =
        runTest {
            val store = newStore()
            for (bad in listOf("../x.md", "/etc/x", "a//b.md")) {
                try {
                    store.read(DocRef.InternalFile(bad))
                    throw AssertionError("expected IllegalArgumentException for $bad")
                } catch (_: IllegalArgumentException) {
                    // expected
                }
            }
        }

    @Test
    fun readMissingThrowsNotFound() =
        runTest {
            val store = newStore()
            val error =
                try {
                    store.read(DocRef.InternalFile("Nope.md"))
                    null
                } catch (e: StorageException) {
                    e
                }
            assertThat(error).isNotNull()
            assertThat(error!!.error).isEqualTo(StorageError.NotFound)
        }

    @Test
    fun readTooLargeThrows() =
        runTest {
            val store = newStore()
            val file = File(store.root, "Huge.md")
            RandomAccessFile(file, "rw").use { it.setLength(17L * 1024 * 1024) }

            val error =
                try {
                    store.read(DocRef.InternalFile("Huge.md"))
                    null
                } catch (e: StorageException) {
                    e
                }

            assertThat(error).isNotNull()
            assertThat(error!!.error).isInstanceOf(StorageError.TooLarge::class.java)
        }

    @Test
    fun changesEmitsAfterCreateWriteRenameTrash() =
        runTest {
            val store = newStore()
            store.changes(FolderRef.INTERNAL_ROOT).test {
                val doc = store.create(FolderRef.INTERNAL_ROOT, "Walk.md") as DocRef.InternalFile
                awaitItem()
                store.write(doc, "hello".toByteArray())
                awaitItem()
                val renamed = store.rename(doc, "Hike.md")
                awaitItem()
                store.trash(renamed)
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun newestDocumentIsRecursive() =
        runTest {
            val store = newStore()
            val a = store.create(FolderRef.INTERNAL_ROOT, "A.md") as DocRef.InternalFile
            File(store.root, a.relPath).setLastModified(1000)
            val drafts = store.createFolder(FolderRef.INTERNAL_ROOT, "Drafts")
            val nested = store.create(drafts, "Nested.md") as DocRef.InternalFile
            File(store.root, nested.relPath).setLastModified(5000)

            val newest = store.newestDocument()

            assertThat(newest).isEqualTo(DocRef.InternalFile("Drafts/Nested.md"))
        }
}
