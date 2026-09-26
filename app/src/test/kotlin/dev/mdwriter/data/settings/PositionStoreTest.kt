package dev.mdwriter.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PositionStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var scopeJob: Job
    private var clock = 0L
    private lateinit var positions: PositionStore

    @Before
    fun setUp() {
        scopeJob = Job()
        val store =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.Unconfined + scopeJob),
                produceFile = { tmp.newFile("positions.preferences_pb") },
            )
        positions = PositionStore(store, clock = { clock })
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
    }

    @Test
    fun putGet() =
        runTest {
            val key = DocKey("i:Foo.md")
            assertThat(positions.get(key)).isNull()
            positions.put(key, Position(5, 100))
            assertThat(positions.get(key)).isEqualTo(Position(5, 100))
        }

    @Test
    fun moveAndRemove() =
        runTest {
            val from = DocKey("i:Old.md")
            val to = DocKey("i:New.md")
            positions.put(from, Position(3, 30))
            positions.move(from, to)
            assertThat(positions.get(from)).isNull()
            assertThat(positions.get(to)).isEqualTo(Position(3, 30))
            positions.remove(to)
            assertThat(positions.get(to)).isNull()
        }

    @Test
    fun evictsLeastRecentlyUsedBeyond200() =
        runTest {
            repeat(200) { i ->
                clock = i.toLong()
                positions.put(DocKey("i:File$i.md"), Position(i, i))
            }
            // Exactly 200 entries: the least-recently-used one (File0, usedAt=0) must still be present.
            assertThat(positions.get(DocKey("i:File0.md"))).isNotNull()

            clock = 1000
            positions.put(DocKey("i:File200.md"), Position(200, 200))

            // 201st entry: the smallest usedAt (File0) is evicted; everything else survives.
            assertThat(positions.get(DocKey("i:File0.md"))).isNull()
            assertThat(positions.get(DocKey("i:File200.md"))).isNotNull()
            assertThat(positions.get(DocKey("i:File1.md"))).isNotNull()
        }
}
