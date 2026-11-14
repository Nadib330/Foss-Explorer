/*
 * Foss Explorer: "Swipe between pages using a touchpad".
 * Own code, GPL-3.0.
 */
package com.rpooi3.explorer.accessibility

import android.content.SharedPreferences
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Two fingers moved sideways on a touchpad go back or forward in the history
 * of the tab, if the page itself has nothing to scroll in that direction.
 *
 * Android reports such a swipe in one of two ways: as scroll events of a
 * mouse (up to Android 13), or as a touch gesture classified as a two-finger
 * swipe (Android 14 and later). Both are counted here.
 */
class TouchpadSwipe {
    /** Where a swipe leads. */
    enum class Direction { BACK, FORWARD }

    private var travelled = 0f
    private var fired = false
    private var lastEvent = 0L
    private var lastX = 0f
    private var lastY = 0f

    /**
     * Takes in one event. Returns the direction as soon as the swipe is long
     * enough, once per swipe, else null.
     *
     * @param canScroll whether the page can scroll horizontally by the given sign
     */
    fun onEvent(event: MotionEvent, canScroll: (Int) -> Boolean): Direction? {
        val step = horizontalStep(event) ?: return null
        // A pause ends a swipe; the next one starts from zero.
        if (event.eventTime - lastEvent > GAP_MS || event.actionMasked == MotionEvent.ACTION_DOWN) reset()
        lastEvent = event.eventTime
        if (step == 0f) return null
        if (canScroll(if (step > 0) 1 else -1)) {
            reset()
            return null
        }
        if (fired) return null
        travelled += step
        if (abs(travelled) < THRESHOLD) return null
        fired = true
        // Content follows the fingers: fingers moving right reveal what lies to the left, the page before.
        return if (travelled < 0) Direction.BACK else Direction.FORWARD
    }

    private fun reset() {
        travelled = 0f
        fired = false
    }

    /**
     * How far the page would scroll to the right for this event, in scroll
     * units; null if the event is not part of a two-finger swipe on a touchpad.
     */
    private fun horizontalStep(event: MotionEvent): Float? {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) || event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return null
        }
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            return if (abs(horizontal) > abs(vertical)) horizontal else 0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            event.classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE
        ) {
            // The gesture arrives like a finger dragging the page: the pointer moves with the fingers.
            val x = event.x
            val y = event.y
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastX = x
                lastY = y
                return 0f
            }
            if (event.actionMasked != MotionEvent.ACTION_MOVE) return null
            val dx = x - lastX
            val dy = y - lastY
            lastX = x
            lastY = y
            // Dragging right scrolls to the left; a scroll unit is about 64 pixels.
            return if (abs(dx) > abs(dy)) -dx / 64f else 0f
        }
        return null
    }

    companion object {
        const val KEY = "touchpad_overscroll_history_navigation"

        /** Scroll units a swipe has to cover. */
        const val THRESHOLD = 4f
        private const val GAP_MS = 400L

        @JvmStatic
        fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY, true)
    }
}
