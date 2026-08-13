package com.fb2.obd

import com.fb2.obd.ui.dash.ThemeGestureLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Double-tap / triple-tap / hold contract used by Classic + OptA/B/C.
 * Regression for user bug: gestures must keep working on every theme.
 */
class ThemeGestureLogicTest {

    @Test
    fun singleTap_doesNothingYet() {
        val r = ThemeGestureLogic.onTap(0, lastTapMs = 0L, nowMs = 1_000L, hasRemap = true)
        assertEquals(1, r.taps)
        assertEquals(ThemeGestureLogic.TapAction.NONE, r.action)
    }

    @Test
    fun doubleTap_schedulesRemap() {
        val first = ThemeGestureLogic.onTap(0, 0L, 1_000L, hasRemap = true)
        val second = ThemeGestureLogic.onTap(first.taps, 1_000L, 1_200L, hasRemap = true)
        assertEquals(2, second.taps)
        assertEquals(ThemeGestureLogic.TapAction.SCHEDULE_REMAP, second.action)
        assertEquals(ThemeGestureLogic.REMAP_CONFIRM_DELAY_MS, second.confirmRemapAfterMs)
        assertTrue(ThemeGestureLogic.confirmRemap(2))
        assertFalse(ThemeGestureLogic.confirmRemap(3))
    }

    @Test
    fun tripleTap_deepSearch_resetsTaps() {
        val t1 = ThemeGestureLogic.onTap(0, 0L, 1_000L, hasRemap = true)
        val t2 = ThemeGestureLogic.onTap(t1.taps, 1_000L, 1_150L, hasRemap = true)
        val t3 = ThemeGestureLogic.onTap(t2.taps, 1_150L, 1_300L, hasRemap = true)
        assertEquals(ThemeGestureLogic.TapAction.DEEP_SEARCH, t3.action)
        assertEquals(0, t3.taps)
    }

    @Test
    fun slowSecondTap_startsNewSequence() {
        val t1 = ThemeGestureLogic.onTap(0, 0L, 1_000L, hasRemap = true)
        val t2 = ThemeGestureLogic.onTap(
            t1.taps,
            lastTapMs = 1_000L,
            nowMs = 1_000L + ThemeGestureLogic.TAP_WINDOW_MS + 1,
            hasRemap = true,
        )
        assertEquals(1, t2.taps)
        assertEquals(ThemeGestureLogic.TapAction.NONE, t2.action)
    }

    @Test
    fun doubleTap_withoutRemap_staysIdle() {
        val t1 = ThemeGestureLogic.onTap(0, 0L, 1_000L, hasRemap = false)
        val t2 = ThemeGestureLogic.onTap(t1.taps, 1_000L, 1_200L, hasRemap = false)
        assertEquals(2, t2.taps)
        assertEquals(ThemeGestureLogic.TapAction.NONE, t2.action)
    }

    @Test
    fun hold_prefersDeepSearchOverThresholds() {
        assertEquals(
            ThemeGestureLogic.HoldAction.DEEP_SEARCH,
            ThemeGestureLogic.onHold(hasDeepSearch = true, hasEditThresholds = true),
        )
        assertEquals(
            ThemeGestureLogic.HoldAction.EDIT_THRESHOLDS,
            ThemeGestureLogic.onHold(hasDeepSearch = false, hasEditThresholds = true),
        )
        assertEquals(
            ThemeGestureLogic.HoldAction.NONE,
            ThemeGestureLogic.onHold(hasDeepSearch = false, hasEditThresholds = false),
        )
    }
}
