package dev.mdwriter.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker

/**
 * Horizontal swipe navigation for the editor (01 §4.8, T13): a start->end swipe opens the library, end->start
 * opens the preview (T16). Detects in [PointerEventPass.Initial] and consumes ONLY on commit (never earlier —
 * consuming earlier would break taps, cursor drags and scrolling; platform factcheck A22). [enabled]/[accepts]/
 * [onArmedDown]/[onSwipe] are read via `pointerInput(Unit)`'s single captured lambda, so every call site MUST pass
 * values wrapped in `rememberUpdatedState` — otherwise this modifier only ever sees the state from the first
 * composition. [rtl] is a plain value (not a lambda): `PointerInputScope` has no `layoutDirection` accessor (only
 * `Density`), so the caller reads `LocalLayoutDirection.current` itself and passes it in; `pointerInput(Unit)`
 * never restarts, so this only ever needs the layout direction in effect when the gesture detector is first
 * installed — a phone/tablet's locale-driven RTL setting doesn't change without an activity recreation anyway.
 *
 * A mouse/stylus pointer never starts a gesture here (only [PointerType.Touch]). When a gesture commits, every
 * remaining pointer event is consumed until pointer-up, so the interop View underneath receives `ACTION_CANCEL`
 * (never a stray tap/drag once we have taken over).
 */
fun Modifier.editorSwipeNav(
    enabled: () -> Boolean,
    accepts: (SwipeDir) -> Boolean = { true },
    onArmedDown: () -> Unit,
    onSwipe: (SwipeDir) -> Unit,
    rtl: Boolean = false,
): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch || !enabled()) return@awaitEachGesture // mouse/stylus: never
            onArmedDown()
            val c = SwipeClassifier(viewConfiguration.touchSlop, viewConfiguration.longPressTimeoutMillis, density, rtl)
            c.down(down.position.x, down.position.y, down.uptimeMillis)
            val vt = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                vt.addPosition(ch.uptimeMillis, ch.position)
                if (!ch.pressed) {
                    val v = vt.calculateVelocity()
                    val d = c.up(ch.position.x, ch.position.y, v.x, v.y)
                    if (d is SwipeDecision.Commit && accepts(d.dir)) {
                        ch.consume() // View gets CANCEL
                        onSwipe(d.dir)
                    }
                    return@awaitEachGesture
                }
                when (
                    val d = c.move(ch.position.x, ch.position.y, ch.uptimeMillis, ev.changes.count { it.pressed })
                ) {
                    SwipeDecision.Abort -> {
                        return@awaitEachGesture
                    }

                    SwipeDecision.Undecided -> {
                        // keep waiting for the next pointer event
                    }

                    is SwipeDecision.Commit -> {
                        if (!accepts(d.dir)) return@awaitEachGesture
                        ev.changes.forEach { it.consume() } // interop -> ACTION_CANCEL to the EditText
                        onSwipe(d.dir)
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            e.changes.forEach { it.consume() }
                        } while (e.changes.any { it.pressed })
                        return@awaitEachGesture
                    }
                }
            }
        }
    }
