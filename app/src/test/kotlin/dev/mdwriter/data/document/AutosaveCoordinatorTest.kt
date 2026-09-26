package dev.mdwriter.data.document

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.storage.FileStat
import dev.mdwriter.data.storage.StorageError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeTarget(
    override val ref: DocRef,
) : AutosaveTarget {
    var version: Long = 0
    var text: String = ""
    var persistDelayMs: Long = 0
    var resultForNext: () -> SaveResult = { SaveResult.Saved(FileStat(1L, 1L)) }
    val writes = mutableListOf<Snapshot>()
    var snapshotCalls = 0

    override suspend fun snapshot(): Snapshot {
        snapshotCalls++
        return Snapshot(version, text)
    }

    override suspend fun persist(s: Snapshot): SaveResult {
        if (persistDelayMs > 0) delay(persistDelayMs)
        writes.add(s)
        return resultForNext()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutosaveCoordinatorTest {
    @Test
    fun savesAfter1sIdle() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            coordinator.begin(target, version = 0, dirty = false)
            runCurrent()
            target.version = 1
            target.text = "hello"
            coordinator.onEdit(1)
            runCurrent()

            advanceTimeBy(999)
            runCurrent()
            assertThat(target.writes).isEmpty()

            advanceTimeBy(2)
            runCurrent()
            assertThat(target.writes).hasSize(1)
        }

    @Test
    fun treeDocUses2sIdle() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.TreeDoc("tree", "doc"))
            coordinator.begin(target, version = 0, dirty = false)
            runCurrent()
            target.version = 1
            target.text = "hello"
            coordinator.onEdit(1)
            runCurrent()

            advanceTimeBy(1_999)
            runCurrent()
            assertThat(target.writes).isEmpty()

            advanceTimeBy(2)
            runCurrent()
            assertThat(target.writes).hasSize(1)
        }

    @Test
    fun continuousTypingSavesWithin10s() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            coordinator.begin(target, version = 0, dirty = false)
            runCurrent()

            var version = 0L

            fun edit() {
                version++
                target.version = version
                target.text = "v$version"
                coordinator.onEdit(version)
                runCurrent()
            }
            edit() // t=0: starts both the idle (1s) and max-latency (10s) timers.
            var elapsed = 0L
            while (elapsed < 10_001) {
                advanceTimeBy(500)
                runCurrent()
                elapsed += 500
                edit() // never a full second of quiet, so only the max-latency timer can fire.
            }
            assertThat(target.writes).isNotEmpty()
        }

    @Test
    fun flushSavesImmediatelyAndAwaits() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            coordinator.begin(target, version = 0, dirty = false)
            runCurrent()
            target.version = 1
            target.text = "hi"
            coordinator.onEdit(1)
            runCurrent()

            coordinator.flush()

            assertThat(target.writes).hasSize(1)
            assertThat(target.writes.first().text).isEqualTo("hi")
        }

    @Test
    fun unchangedVersionNeverWrites() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            coordinator.begin(target, version = 5, dirty = false)
            runCurrent()

            coordinator.flush()

            assertThat(target.snapshotCalls).isEqualTo(0)
            assertThat(target.writes).isEmpty()
        }

    @Test
    fun failureRetriesWithBackoff() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            var calls = 0
            target.resultForNext = {
                calls++
                if (calls <=
                    5
                ) {
                    SaveResult.Failed(StorageError.ProviderFailure(RuntimeException("x")))
                } else {
                    SaveResult.Saved(FileStat(1L, 1L))
                }
            }
            target.version = 1
            target.text = "hi"
            // dirty=true schedules ONLY the idle timer (no max-latency timer to interfere with the retry chain).
            coordinator.begin(target, version = 1, dirty = true)
            runCurrent()

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(calls).isEqualTo(1) // idle -> attempt #1 (fails) -> retry at +1s

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(calls).isEqualTo(2) // +1s

            advanceTimeBy(2_000)
            runCurrent()
            assertThat(calls).isEqualTo(3) // +2s

            advanceTimeBy(5_000)
            runCurrent()
            assertThat(calls).isEqualTo(4) // +5s

            advanceTimeBy(10_000)
            runCurrent()
            assertThat(calls).isEqualTo(5) // +10s

            advanceTimeBy(10_000)
            runCurrent()
            assertThat(calls).isEqualTo(6) // +10s (backoff caps here) -> succeeds
            assertThat(target.writes).hasSize(6)
        }

    @Test
    fun conflictPausesUntilResume() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            target.resultForNext = { SaveResult.Conflict(FileStat(2L, 2L)) }
            target.version = 1
            target.text = "hi"
            coordinator.begin(target, version = 1, dirty = true)
            runCurrent()

            advanceTimeBy(1_000)
            runCurrent()
            assertThat(target.writes).hasSize(1)
            assertThat(coordinator.state.value).isEqualTo(SaveState.Dirty)

            // Further edits don't trigger another PERSISTED save while paused.
            target.version = 2
            target.text = "hi2"
            coordinator.onEdit(2)
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            assertThat(target.writes).hasSize(1)

            target.resultForNext = { SaveResult.Saved(FileStat(3L, 3L)) }
            coordinator.resume()
            runCurrent()
            advanceTimeBy(1_000)
            runCurrent()
            assertThat(target.writes.size).isAtLeast(2)
        }

    @Test
    fun flushSurvivesCallerCancellation() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val coordinator = AutosaveCoordinator(backgroundScope, dispatcher)
            val target = FakeTarget(DocRef.InternalFile("a.md"))
            target.persistDelayMs = 100
            target.resultForNext = { SaveResult.Saved(FileStat(1L, 1L)) }
            coordinator.begin(target, version = 0, dirty = false)
            runCurrent()
            target.version = 1
            target.text = "hi"
            coordinator.onEdit(1)
            runCurrent()

            val caller = launch { coordinator.flush() }
            runCurrent() // caller enters flush() and suspends inside persist()'s artificial delay
            caller.cancel() // cancel only the awaiting caller; the save itself runs under `backgroundScope`

            // Drain in small steps rather than a single advanceUntilIdle(): the LimitedDispatcher wrapping the
            // coordinator's `serial` dispatcher re-posts its own worker through the test scheduler, which
            // advanceUntilIdle() alone does not reliably chase to completion in the same call.
            repeat(10) {
                advanceTimeBy(50)
                runCurrent()
            }

            assertThat(target.writes).hasSize(1)
        }
}
