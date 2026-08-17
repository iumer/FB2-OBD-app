package com.fb2.obd.ui

/**
 * Collapsing chrome math for Classic Dash: TopBar + RPM/Speed hero hide when
 * the user scrolls content up, and return when they scroll back to the top.
 * Pure floats so JVM tests can lock the consume/collapse rules.
 *
 * [collapsedPx] is 0 when fully shown and [headerPx] when fully hidden.
 * Returned [consumedY] uses the same sign as [deltaY].
 */
object ChromeCollapse {

    data class Step(val collapsedPx: Float, val consumedY: Float)

    fun onPreScroll(deltaY: Float, collapsedPx: Float, headerPx: Float): Step {
        if (headerPx <= 0f || deltaY >= 0f) return Step(collapsedPx, 0f)
        val next = (collapsedPx - deltaY).coerceIn(0f, headerPx)
        val consumed = collapsedPx - next // negative when collapsing
        return Step(next, consumed)
    }

    fun onPostScroll(availableY: Float, collapsedPx: Float, headerPx: Float): Step {
        if (headerPx <= 0f || availableY <= 0f) return Step(collapsedPx, 0f)
        val next = (collapsedPx - availableY).coerceIn(0f, headerPx)
        val consumed = collapsedPx - next // positive when expanding
        return Step(next, consumed)
    }
}
