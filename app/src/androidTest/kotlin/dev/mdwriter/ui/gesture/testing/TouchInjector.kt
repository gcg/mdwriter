package dev.mdwriter.ui.gesture.testing

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Injects real [MotionEvent]s through `UiAutomation.injectInputEvent` (T13, §J) — real `SystemClock.uptimeMillis()`
 * timestamps and real `Thread.sleep` between steps, so `EditText`'s own long-press timers (used for the
 * "cursor drag from anywhere"/selection heuristics [SwipeClassifier] has to coexist with) actually fire, unlike
 * `adb shell input` (which cannot give this level of timing/velocity control).
 *
 * Coordinates are screen px (from `View.getLocationOnScreen`); every helper stays within x 20-80% of the screen
 * per the task's own instruction, clear of the Back-gesture edges.
 */
object TouchInjector {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun inject(event: MotionEvent) {
        automation.injectInputEvent(event, true)
        event.recycle()
    }

    /** The short `MotionEvent.obtain(downTime, eventTime, action, x, y, metaState)` overload leaves every
     * pointer's `toolType` at its default, `TOOL_TYPE_UNKNOWN` — which Compose's `PointerType` maps to
     * [androidx.compose.ui.input.pointer.PointerType.Unknown], never [androidx.compose.ui.input.pointer.PointerType.Touch]
     * (a real bug found on-device: `editorSwipeNav`'s very first check, `down.type != PointerType.Touch`, silently
     * discarded every synthesized gesture). The full [MotionEvent.PointerProperties]-based overload is the only one
     * that lets a single-pointer event declare `TOOL_TYPE_FINGER` explicitly. */
    private fun obtain(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ): MotionEvent {
        val props =
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                },
            )
        val coords =
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1f
                    size = 1f
                },
            )
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            props,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
    }

    /** A single-finger swipe from [from] to [to] over [durationMs], in [steps] evenly-spaced moves. */
    fun swipe(
        from: Point,
        to: Point,
        durationMs: Long,
        steps: Int = 12,
    ) {
        val downTime = SystemClock.uptimeMillis()
        inject(obtain(downTime, downTime, MotionEvent.ACTION_DOWN, from.x.toFloat(), from.y.toFloat()))
        val stepMs = (durationMs / steps).coerceAtLeast(1)
        for (i in 1..steps) {
            Thread.sleep(stepMs)
            val t = SystemClock.uptimeMillis()
            val frac = i.toFloat() / steps
            val x = from.x + (to.x - from.x) * frac
            val y = from.y + (to.y - from.y) * frac
            inject(obtain(downTime, t, MotionEvent.ACTION_MOVE, x, y))
        }
        val upTime = SystemClock.uptimeMillis()
        inject(obtain(downTime, upTime, MotionEvent.ACTION_UP, to.x.toFloat(), to.y.toFloat()))
    }

    /** Holds at [at] for [holdMs] (long enough for a real long-press to be recognized), then drags [dx] px
     * horizontally over 300 ms — the "cursor drag from anywhere" gesture EditText itself recognizes. */
    fun longPressThenDrag(
        at: Point,
        dx: Int,
        holdMs: Long = 800,
    ) {
        val downTime = SystemClock.uptimeMillis()
        inject(obtain(downTime, downTime, MotionEvent.ACTION_DOWN, at.x.toFloat(), at.y.toFloat()))
        Thread.sleep(holdMs)
        val dragSteps = 10
        val dragMs = 300L
        val stepMs = dragMs / dragSteps
        for (i in 1..dragSteps) {
            Thread.sleep(stepMs)
            val t = SystemClock.uptimeMillis()
            val x = at.x + dx * (i.toFloat() / dragSteps)
            inject(obtain(downTime, t, MotionEvent.ACTION_MOVE, x, at.y.toFloat()))
        }
        val upTime = SystemClock.uptimeMillis()
        inject(obtain(downTime, upTime, MotionEvent.ACTION_UP, (at.x + dx).toFloat(), at.y.toFloat()))
    }

    /** A vertical drag of [dy] px with [driftX] px of horizontal drift, over [durationMs] — used to confirm a
     * mostly-vertical gesture is treated as a scroll, never a swipe. */
    fun verticalDrag(
        from: Point,
        dy: Int,
        driftX: Int,
        durationMs: Long,
        steps: Int = 16,
    ) {
        swipe(from, Point(from.x + driftX, from.y + dy), durationMs, steps)
    }

    /** Two-finger horizontal swipe, both fingers moving [dx] px together — `ACTION_POINTER_DOWN` with
     * `pointerCount = 2`, per the task's own §J sketch. */
    fun twoFingerSwipe(
        from: Point,
        dx: Int,
        secondFingerOffsetY: Int = 150,
        durationMs: Long = 200,
        steps: Int = 8,
    ) {
        val downTime = SystemClock.uptimeMillis()
        val props =
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                },
                MotionEvent.PointerProperties().apply {
                    id = 1
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                },
            )

        fun coords(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
        ) = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = x0
                y = y0
                pressure = 1f
                size = 1f
            },
            MotionEvent.PointerCoords().apply {
                x = x1
                y = y1
                pressure = 1f
                size = 1f
            },
        )
        val y0 = from.y.toFloat()
        val y1 = (from.y + secondFingerOffsetY).toFloat()

        fun multi(
            t: Long,
            action: Int,
            pointerCount: Int,
            x0: Float,
            x1: Float,
        ) = MotionEvent.obtain(
            downTime,
            t,
            action,
            pointerCount,
            props,
            coords(x0, y0, x1, y1),
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0,
        )
        inject(multi(downTime, MotionEvent.ACTION_DOWN, 1, from.x.toFloat(), from.x.toFloat()))
        val pointerDownAction = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        inject(multi(SystemClock.uptimeMillis(), pointerDownAction, 2, from.x.toFloat(), from.x.toFloat()))
        val stepMs = (durationMs / steps).coerceAtLeast(1)
        for (i in 1..steps) {
            Thread.sleep(stepMs)
            val t = SystemClock.uptimeMillis()
            val frac = i.toFloat() / steps
            val x = from.x + dx * frac
            inject(multi(t, MotionEvent.ACTION_MOVE, 2, x, x))
        }
        val pointerUpAction = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val endX = (from.x + dx).toFloat()
        inject(multi(SystemClock.uptimeMillis(), pointerUpAction, 2, endX, endX))
        inject(multi(SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1, endX, endX))
    }
}
