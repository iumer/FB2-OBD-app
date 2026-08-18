package com.fb2.obd

import com.fb2.obd.car.FloatingDashLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingDashLayoutTest {

    @Test
    fun expanded_capsAtMaxOnTallScreens() {
        assertEquals(FloatingDashLayout.EXPANDED_MAX_DP, FloatingDashLayout.expandedDp(720))
        assertEquals(FloatingDashLayout.EXPANDED_MAX_DP, FloatingDashLayout.expandedDp(1080))
    }

    @Test
    fun expanded_shrinksOnShortHuHeight() {
        // Dense 1280×720 @2x → ~360dp short edge; ring must leave EDGE_MARGIN.
        val short = 360
        val expanded = FloatingDashLayout.expandedDp(short)
        assertTrue(expanded <= short - FloatingDashLayout.EDGE_MARGIN_DP)
        assertTrue(expanded < FloatingDashLayout.EXPANDED_MAX_DP)
        assertTrue(FloatingDashLayout.fitsInside(expanded))
    }

    @Test
    fun expanded_fitsCommonSevenInchHu() {
        // 1024×600 mdpi → 600dp short edge; uses max 400 with margin.
        val expanded = FloatingDashLayout.expandedDp(600)
        assertEquals(FloatingDashLayout.EXPANDED_MAX_DP, expanded)
        assertTrue(FloatingDashLayout.fitsInside(expanded))
        val radius = FloatingDashLayout.radiusDp(expanded)
        val outer = radius + FloatingDashLayout.SAT_DP / 2
        assertTrue(outer <= expanded / 2 - 4)
    }

    @Test
    fun collapsedAndSatsStayTouchFriendly() {
        assertTrue(FloatingDashLayout.COLLAPSED_DP >= 88)
        assertTrue(FloatingDashLayout.SAT_DP >= 96)
        assertTrue(FloatingDashLayout.SAT_DP < FloatingDashLayout.EXPANDED_MAX_DP / 3)
    }
}
