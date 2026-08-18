package com.fb2.obd.ui.dash

/**
 * Continuous wheel math for OptB Orbit side pickers.
 * Drag offset is in px; when it crosses half an item the center index wraps
 * and offset is folded — same idea as a NumberPicker, not 22 dp snap-jumps.
 */
object OrbitWheelPhysics {
    data class State(val center: Int, val offsetPx: Float)

    fun applyDrag(
        center: Int,
        offsetPx: Float,
        deltaPx: Float,
        itemHeightPx: Float,
        count: Int,
    ): State {
        if (count <= 0 || itemHeightPx <= 1f) return State(center.coerceAtLeast(0), offsetPx)
        var c = center.floorMod(count)
        var o = offsetPx + deltaPx
        val half = itemHeightPx / 2f
        while (o > half) {
            c = (c - 1).floorMod(count)
            o -= itemHeightPx
        }
        while (o < -half) {
            c = (c + 1).floorMod(count)
            o += itemHeightPx
        }
        return State(c, o)
    }

    /** Convert release velocity (px/s) into extra travel so a flick keeps rolling. */
    fun flingDeltaPx(velocityYPxPerSec: Float, itemHeightPx: Float): Float {
        if (itemHeightPx <= 1f) return 0f
        return (velocityYPxPerSec * 0.18f).coerceIn(-itemHeightPx * 5f, itemHeightPx * 5f)
    }

    fun Int.floorMod(m: Int): Int {
        if (m <= 0) return 0
        val r = this % m
        return if (r < 0) r + m else r
    }
}
