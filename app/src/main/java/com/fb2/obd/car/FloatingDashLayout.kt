package com.fb2.obd.car

/**
 * Size math for the floating Dash bubble — kept pure so HU fit can be unit-tested.
 *
 * Expanded ring must stay inside the short screen edge (typical 7" HU ≈ 480–600dp tall)
 * with a comfortable margin, while remaining glanceable while driving.
 */
object FloatingDashLayout {
    const val COLLAPSED_DP = 92
    const val CENTER_DP = 92
    const val SAT_DP = 100
    /** Hard cap — never grow past this even on tall phone screens. */
    const val EXPANDED_MAX_DP = 340
    /** Keep this many dp free on the short edge so the ring never fills the HU. */
    const val EDGE_MARGIN_DP = 72

    /**
     * Expanded window size in dp for a display whose short edge is [shortEdgeDp].
     */
    fun expandedDp(shortEdgeDp: Int): Int {
        val room = (shortEdgeDp - EDGE_MARGIN_DP).coerceAtLeast(COLLAPSED_DP * 3)
        return minOf(EXPANDED_MAX_DP, room)
    }

    /**
     * Ring radius (center → satellite center) so satellites stay inset inside [expandedDp].
     */
    fun radiusDp(expandedDp: Int, satDp: Int = SAT_DP): Int {
        // outer = radius + sat/2 ; must be ≤ expanded/2 − 10
        val maxRadius = expandedDp / 2 - satDp / 2 - 10
        // Scale minimum inset with window size so short-edge HUs still fit.
        val minRadius = (expandedDp / 5).coerceIn(56, 96)
        return maxRadius.coerceAtLeast(minRadius).coerceAtMost(maxRadius)
    }

    /** True when the expanded ring (with satellites) fits inside [expandedDp]. */
    fun fitsInside(expandedDp: Int, satDp: Int = SAT_DP, radiusDp: Int = radiusDp(expandedDp, satDp)): Boolean {
        val outer = radiusDp + satDp / 2
        return outer <= expandedDp / 2 - 4
    }
}
