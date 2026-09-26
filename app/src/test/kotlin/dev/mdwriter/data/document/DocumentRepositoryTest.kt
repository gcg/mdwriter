package dev.mdwriter.data.document

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.library.FolderRef
import dev.mdwriter.data.library.LibraryRepository
import dev.mdwriter.data.library.key
import dev.mdwriter.data.settings.SettingsRepository
import dev.mdwriter.data.storage.InternalStore
import dev.mdwriter.data.storage.RecoveryStore
import dev.mdwriter.data.storage.StorageError
import dev.mdwriter.data.storage.StorageException
import dev.mdwriter.data.storage.StorageLimits
import dev.mdwriter.data.storage.TrashBin
import dev.mdwriter.testing.FakeDocumentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocumentRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopeJobs = mutableListOf<Job>()
    private var settingsCounter = 0

    @After
    fun tearDown() {
        scopeJobs.forEach { it.cancel() }
    }

    private fun fakeSettings(): SettingsRepository {
        val job = Job()
        scopeJobs += job
        val store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.Unconfined + job),
                produceFile = { tmp.newFile("settings-${settingsCounter++}.preferences_pb") },
            )
        return SettingsRepository(store)
    }

    private data class Fixture(
        val repo: DocumentRepository,
        val store: InternalStore,
        val recovery: RecoveryStore,
    )

    private fun newInternalRepo(): Fixture {
        val root = tmp.newFolder("library-$settingsCounter")
        val trashRoot = tmp.newFolder("trash-$settingsCounter")
        val store = InternalStore(root = root, trash = TrashBin(trashRoot))
        val recovery = RecoveryStore(tmp.newFolder("recovery-${settingsCounter++}"))
        val library = LibraryRepository(store, fakeSettings())
        val repo = DocumentRepository(library, recovery, Dispatchers.Unconfined)
        return Fixture(repo, store, recovery)
    }

    @Test
    fun crlfBomRoundTripByteForByte() =
        runTest {
            val (repo, store, _) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Doc.md") as DocRef.InternalFile
            val original = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Line1\r\nLine2\r\n".toByteArray()
            store.write(ref, original)

            val loaded = repo.load(ref)
            val result = repo.save(ref, loaded.text, loaded.baseline, loaded.format)

            assertThat(result).isInstanceOf(SaveResult.Saved::class.java)
            assertThat(store.read(ref)).isEqualTo(original)
        }

    @Test
    fun over1MbFlagsLarge() =
        runTest {
            val (repo, store, _) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Big.md") as DocRef.InternalFile
            val bytes = ByteArray(StorageLimits.LARGE_BYTES.toInt() + 10) { 'a'.code.toByte() }
            store.write(ref, bytes)

            val loaded = repo.load(ref)

            assertThat(loaded.large).isTrue()
            assertThat(loaded.readOnly).isFalse()
        }

    @Test
    fun over5MbIsReadOnly() =
        runTest {
            val (repo, store, _) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Huge.md") as DocRef.InternalFile
            val bytes = ByteArray(StorageLimits.READ_ONLY_BYTES.toInt() + 10) { 'a'.code.toByte() }
            store.write(ref, bytes)

            val loaded = repo.load(ref)

            assertThat(loaded.readOnly).isTrue()
        }

    @Test
    fun binaryIsRefused() =
        runTest {
            val (repo, store, _) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Bin.md") as DocRef.InternalFile
            store.write(ref, byteArrayOf(1, 2, 0, 3, 4))

            val ex =
                try {
                    repo.load(ref)
                    null
                } catch (e: StorageException) {
                    e
                }

            assertThat(ex).isNotNull()
            assertThat(ex!!.error).isEqualTo(StorageError.Encoding)
        }

    @Test
    fun newerRecoveryWins() =
        runTest {
            val (repo, store, recovery) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Note.md") as DocRef.InternalFile
            store.write(ref, "disk text".toByteArray())
            recovery.write(ref.key(), "recovered text")
            File(store.root, ref.relPath).setLastModified(1_000_000)
            recovery.fileFor(ref.key()).setLastModified(2_000_000)

            val loaded = repo.load(ref)

            assertThat(loaded.recovered).isTrue()
            assertThat(loaded.text).isEqualTo("recovered text")
            assertThat(loaded.diskTextIfConflict).isNull()
        }

    @Test
    fun olderDifferentRecoveryRaisesConflict() =
        runTest {
            val (repo, store, recovery) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Note.md") as DocRef.InternalFile
            store.write(ref, "disk text".toByteArray())
            recovery.write(ref.key(), "recovered text")
            File(store.root, ref.relPath).setLastModified(2_000_000)
            recovery.fileFor(ref.key()).setLastModified(1_000_000)

            val loaded = repo.load(ref)

            assertThat(loaded.recovered).isTrue()
            assertThat(loaded.text).isEqualTo("recovered text")
            assertThat(loaded.diskTextIfConflict).isEqualTo("disk text")
        }

    @Test
    fun externalChangeDetectedOnSave() =
        runTest {
            val (repo, store, _) = newInternalRepo()
            val ref = store.create(FolderRef.INTERNAL_ROOT, "Note.md") as DocRef.InternalFile
            store.write(ref, "v1".toByteArray())
            val loaded = repo.load(ref)

            store.write(ref, "v1-external-and-longer".toByteArray())
            File(store.root, ref.relPath).setLastModified((loaded.baseline.lastModified ?: 0L) + 5_000)

            val result = repo.save(ref, "my edits", loaded.baseline, loaded.format)

            assertThat(result).isInstanceOf(SaveResult.Conflict::class.java)
        }

    @Test
    fun nullLastModifiedUsesSizeThenHash() =
        runTest {
            val store = FakeDocumentStore()
            store.nullLastModified = true
            val library = LibraryRepository(store, fakeSettings())
            val recovery = RecoveryStore(tmp.newFolder("recovery-null"))
            val repo = DocumentRepository(library, recovery, Dispatchers.Unconfined)
            val ref = DocRef.InternalFile("Foo.md")
            store.write(ref, "hello".toByteArray())

            val loaded = repo.load(ref)
            assertThat(loaded.baseline.lastModified).isNull()

            // Same content, same size on disk as at load time -> no false conflict even with a null mtime.
            val saved = repo.save(ref, "hello world!!", loaded.baseline, loaded.format)
            check(saved is SaveResult.Saved)

            // Something else rewrites the SAME number of bytes without going through write() (bypassing the
            // known-hash bookkeeping) -> the size check alone can't see it, but the content hash must.
            val sameLengthDifferentContent = "HELLO WORLD!!".toByteArray()
            assertThat(sameLengthDifferentContent.size).isEqualTo("hello world!!".toByteArray().size)
            store.externalWrite(ref, sameLengthDifferentContent)

            val result = repo.save(ref, "some new text", saved.newBaseline, loaded.format)

            assertThat(result).isInstanceOf(SaveResult.Conflict::class.java)
        }

    @Test
    fun recoveryWrittenBeforeWriteAndDeletedAfter() =
        runTest {
            val store = FakeDocumentStore()
            val library = LibraryRepository(store, fakeSettings())
            val recovery = RecoveryStore(tmp.newFolder("recovery-fail"))
            val repo = DocumentRepository(library, recovery, Dispatchers.Unconfined)
            val ref = DocRef.InternalFile("Note.md")
            store.write(ref, "v1".toByteArray())
            val loaded = repo.load(ref)

            store.failNextWrites(1)
            val failed = repo.save(ref, "v2", loaded.baseline, loaded.format)
            assertThat(failed).isInstanceOf(SaveResult.Failed::class.java)
            assertThat(recovery.read(ref.key())).isNotNull()

            val succeeded = repo.save(ref, "v2", loaded.baseline, loaded.format)
            assertThat(succeeded).isInstanceOf(SaveResult.Saved::class.java)
            assertThat(recovery.read(ref.key())).isNull()
        }
}
