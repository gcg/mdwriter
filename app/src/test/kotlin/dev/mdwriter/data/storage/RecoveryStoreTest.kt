package dev.mdwriter.data.storage

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocKey
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecoveryStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeReadRoundTrip() =
        runTest {
            val store = RecoveryStore(tmp.newFolder("recovery"))
            val key = DocKey("i:Drafts/Walk.md")
            val text = "hello café 😀\nsecond line"

            store.write(key, text)
            val copy = store.read(key)

            assertThat(copy).isNotNull()
            assertThat(copy!!.text).isEqualTo(text)
        }

    @Test
    fun fileForNameIs40HexCharsPlusMd() {
        val store = RecoveryStore(tmp.newFolder("recovery"))
        val file = store.fileFor(DocKey("i:Drafts/Walk.md"))
        assertThat(file.name).matches("[0-9a-f]{40}\\.md")
    }

    @Test
    fun deleteRemoves() =
        runTest {
            val store = RecoveryStore(tmp.newFolder("recovery"))
            val key = DocKey("i:Walk.md")
            store.write(key, "text")

            store.delete(key)

            assertThat(store.read(key)).isNull()
        }

    @Test
    fun newerThanAroundSetLastModified() =
        runTest {
            val store = RecoveryStore(tmp.newFolder("recovery"))
            val key = DocKey("i:Walk.md")
            store.write(key, "text")
            val file = store.fileFor(key)
            file.setLastModified(10_000)

            assertThat(store.newerThan(key, 5_000)).isTrue()
            assertThat(store.newerThan(key, 20_000)).isFalse()
        }

    @Test
    fun readMissingKeyReturnsNull() =
        runTest {
            val store = RecoveryStore(tmp.newFolder("recovery"))
            assertThat(store.read(DocKey("i:Nope.md"))).isNull()
        }
}
