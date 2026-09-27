package dev.mdwriter.ui.editor

import dev.mdwriter.ui.theme.WriterMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Pure show/hide state machine for the two floating chrome glyphs (02 §5, T13). An edit hides them immediately;
 * they come back on: an idle pause with the IME hidden ([idleShowMs]), the IME hiding, a scroll-up past a
 * threshold, or a tap in the top strip. No Android/Compose types — virtual-time tested with `runTest`.
 */
class ChromeVisibility(
    private val scope: CoroutineScope,
    private val idleShowMs: Long = WriterMotion.CHROME_IDLE_SHOW_DELAY_MS,
) {
    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible

    private var imeVisible = false
    private var upPx = 0f
    private var idle: Job? = null

    /** Every text change (`controller.edits`). */
    fun onEdit() {
        _visible.value = false
        upPx = 0f
        idle?.cancel()
        if (!imeVisible) {
            idle =
                scope.launch {
                    delay(idleShowMs)
                    show()
                } // hardware keyboard pause
        }
    }

    fun onImeVisibility(v: Boolean) {
        val was = imeVisible
        imeVisible = v
        if (v) {
            idle?.cancel()
        } else if (was) {
            show()
        }
    }

    fun onScroll(
        dyPx: Float,
        thresholdPx: Float,
    ) {
        if (dyPx < 0) {
            upPx -= dyPx
            if (upPx >= thresholdPx) show()
        } else {
            upPx = 0f
        }
    }

    fun onTopTap() = show()

    private fun show() {
        idle?.cancel()
        upPx = 0f
        _visible.value = true
    }
}
