package dev.mdwriter.data.document

import dev.mdwriter.data.library.DocRef
import dev.mdwriter.data.storage.userMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A version+text pair to persist. `version` is [dev.mdwriter.editor.EditorController.version]. */
data class Snapshot(
    val version: Long,
    val text: String,
)

/** The coordinator never sees documents directly (01 §6.3) — [EditorViewModel][dev.mdwriter.ui.editor.EditorViewModel]
 * implements this. [snapshot] hops to Main (it reads the live `EditText`); [persist] calls
 * [DocumentRepository.save]. */
interface AutosaveTarget {
    val ref: DocRef

    suspend fun snapshot(): Snapshot

    suspend fun persist(s: Snapshot): SaveResult
}

/**
 * Debounce policy only (01 §6.3): 1 s idle (2 s for a [DocRef.TreeDoc]), plus a 10 s max latency while typing
 * continuously. [flush] runs in [NonCancellable] so a finishing activity never drops the last save — callers pass
 * [scope] = `appContainer.applicationScope`, never `viewModelScope`.
 *
 * ALL mutable fields are touched only while running on [serial] (a `limitedParallelism(1)` view of [dispatcher]),
 * so `begin`/`onEdit`/`resume`/`end` (all fire-and-forget from the caller's point of view) never race the
 * background save loop.
 */
class AutosaveCoordinator(
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    private val maxLatencyMs: Long = 10_000,
    private val backoffMs: List<Long> = listOf(1_000, 2_000, 5_000, 10_000),
) {
    private val serial = dispatcher.limitedParallelism(1)
    private val saveLock = Mutex()

    private val _state = MutableStateFlow<SaveState>(SaveState.Clean)
    val state: StateFlow<SaveState> = _state.asStateFlow()

    private var target: AutosaveTarget? = null
    private var saved: Long = 0
    private var latest: Long = 0
    private var paused: Boolean = false
    private var attempt: Int = 0

    private var idleJob: Job? = null
    private var maxJob: Job? = null
    private var retryJob: Job? = null

    fun idleMsFor(ref: DocRef): Long = if (ref is DocRef.TreeDoc) 2_000L else 1_000L

    /** Adopts a freshly-opened (or newly bound) document. [dirty]: the loaded text differs from what's on disk
     * (a recovery copy was preferred) — schedule a save right away instead of waiting for the next edit. */
    fun begin(
        newTarget: AutosaveTarget,
        version: Long,
        dirty: Boolean,
    ) {
        scope.launch(serial) {
            cancelTimers()
            target = newTarget
            latest = version
            saved = if (dirty) Long.MIN_VALUE else version
            paused = false
            attempt = 0
            _state.value = if (dirty) SaveState.Dirty else SaveState.Clean
            if (dirty) scheduleIdle()
        }
    }

    /** Detaches the current target. Callers `flush()` first if a pending save must not be lost. */
    fun end() {
        scope.launch(serial) {
            cancelTimers()
            target = null
        }
    }

    fun onEdit(version: Long) {
        scope.launch(serial) {
            latest = version
            if (version != saved && _state.value !is SaveState.Error) {
                _state.value = SaveState.Dirty
            }
            scheduleIdle()
            if (maxJob == null) scheduleMax()
        }
    }

    /** Clears `paused` (set when the last save hit a [SaveResult.Conflict]) and schedules a save. */
    fun resume() {
        scope.launch(serial) {
            paused = false
            scheduleIdle()
        }
    }

    suspend fun flush(pre: Snapshot? = null): Unit =
        scope
            .async(serial) {
                cancelTimers()
                saveNow(pre)
            }.await()

    private fun scheduleIdle() {
        idleJob?.cancel()
        val delayMs = target?.let { idleMsFor(it.ref) } ?: 1_000L
        idleJob =
            scope.launch(serial) {
                delay(delayMs)
                saveNow(null)
            }
    }

    private fun scheduleMax() {
        maxJob =
            scope.launch(serial) {
                delay(maxLatencyMs)
                saveNow(null)
            }
    }

    private fun cancelIdle() {
        idleJob?.cancel()
        idleJob = null
    }

    private fun cancelMax() {
        maxJob?.cancel()
        maxJob = null
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun cancelTimers() {
        cancelIdle()
        cancelMax()
        cancelRetry()
    }

    private suspend fun saveNow(pre: Snapshot?): Unit =
        withContext(NonCancellable) {
            saveLock.withLock {
                val t = target ?: return@withLock
                if (paused) return@withLock
                if (pre == null && latest == saved) return@withLock // clean: don't even snapshot (O(n) copy)
                val snap = pre ?: t.snapshot()
                if (snap.version == saved) {
                    _state.value = SaveState.Clean
                    return@withLock
                }
                _state.value = SaveState.Saving
                when (val r = t.persist(snap)) {
                    is SaveResult.Saved -> {
                        saved = snap.version
                        attempt = 0
                        cancelRetry()
                        cancelMax()
                        _state.value =
                            if (latest == saved) {
                                SaveState.Clean
                            } else {
                                scheduleIdle()
                                SaveState.Dirty
                            }
                    }

                    is SaveResult.Conflict -> {
                        paused = true
                        _state.value = SaveState.Dirty // the ViewModel shows the conflict banner
                    }

                    is SaveResult.Failed -> {
                        _state.value = SaveState.Error(r.error.userMessage())
                        retryJob =
                            scope.launch(serial) {
                                delay(backoffMs[minOf(attempt++, backoffMs.lastIndex)])
                                saveNow(null)
                            }
                    }
                }
            }
        }
}
