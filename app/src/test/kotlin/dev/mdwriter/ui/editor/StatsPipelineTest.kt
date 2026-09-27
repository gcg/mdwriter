package dev.mdwriter.ui.editor

import com.google.common.truth.Truth.assertThat
import dev.mdwriter.editor.StatsInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeStatsSource : StatsSource {
    val triggerFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    override val triggers: Flow<Unit> = triggerFlow
    override var version: Long = 0
    override var selection: Pair<Int, Int> = 0 to 0
    var text: String = ""
    var snapshotCalls = 0

    /** Simulates "a version bump landed while compute was running": the value handed back to the pipeline is the
     * one seen at the moment [snapshot] was called, but [version] itself moves on right after — exactly what the
     * pipeline's own `input.version != src.version` staleness check must catch. */
    var bumpVersionOnNextSnapshot = false

    override fun snapshot(): StatsInput {
        snapshotCalls++
        val input = StatsInput(text, emptyList(), selection.first, selection.second, version)
        if (bumpVersionOnNextSnapshot) {
            bumpVersionOnNextSnapshot = false
            version++
        }
        return input
    }
}

/** Task T15, Reference §D. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsPipelineTest {
    @Test
    fun nothingEmittedBefore400ms() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val src = FakeStatsSource().apply { text = "one two three" }
            val results = mutableListOf<DisplayStats>()
            val job = launch(dispatcher) { StatsPipeline(src, compute = dispatcher).flow().collect { results.add(it) } }
            advanceTimeBy(399)
            runCurrent()
            assertThat(results).isEmpty()
            job.cancel()
        }

    @Test
    fun threeTriggers100msApartProduceExactlyOneEmission() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val src = FakeStatsSource().apply { text = "one two three" }
            val results = mutableListOf<DisplayStats>()
            val job = launch(dispatcher) { StatsPipeline(src, compute = dispatcher).flow().collect { results.add(it) } }
            runCurrent() // let the initial (onStart) trigger start its own delay(400)

            src.triggerFlow.tryEmit(Unit)
            advanceTimeBy(100)
            runCurrent()
            src.triggerFlow.tryEmit(Unit)
            advanceTimeBy(100)
            runCurrent()
            src.triggerFlow.tryEmit(Unit)
            advanceTimeBy(500)
            runCurrent()

            assertThat(results).hasSize(1)
            job.cancel()
        }

    @Test
    fun versionBumpDuringComputeDropsThatResult() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val src =
                FakeStatsSource().apply {
                    text = "one two three"
                    bumpVersionOnNextSnapshot = true
                }
            val results = mutableListOf<DisplayStats>()
            val job = launch(dispatcher) { StatsPipeline(src, compute = dispatcher).flow().collect { results.add(it) } }
            advanceTimeBy(400)
            runCurrent()
            assertThat(results).isEmpty()
            job.cancel()
        }

    @Test
    fun movingCollapsedCaretSameVersionSkipsSnapshot() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val src =
                FakeStatsSource().apply {
                    text = "one two three"
                    version = 1
                }
            val results = mutableListOf<DisplayStats>()
            val job = launch(dispatcher) { StatsPipeline(src, compute = dispatcher).flow().collect { results.add(it) } }
            advanceTimeBy(400)
            runCurrent()
            assertThat(src.snapshotCalls).isEqualTo(1)
            assertThat(results).hasSize(1)

            src.selection = 3 to 3 // still a collapsed caret, doc unchanged
            src.triggerFlow.tryEmit(Unit)
            advanceTimeBy(400)
            runCurrent()

            assertThat(src.snapshotCalls).isEqualTo(1) // no new snapshot() call
            assertThat(results).hasSize(2) // the cached result was re-sent
            job.cancel()
        }

    @Test
    fun selectionGivesIsSelectionTrueWithSelectionOnlyStats() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val src =
                FakeStatsSource().apply {
                    text = "one two three"
                    selection = 4 to 7
                } // "two"
            val results = mutableListOf<DisplayStats>()
            val job = launch(dispatcher) { StatsPipeline(src, compute = dispatcher).flow().collect { results.add(it) } }
            advanceTimeBy(400)
            runCurrent()
            assertThat(results).hasSize(1)
            assertThat(results[0].isSelection).isTrue()
            assertThat(results[0].stats.words).isEqualTo(1)
            job.cancel()
        }
}
