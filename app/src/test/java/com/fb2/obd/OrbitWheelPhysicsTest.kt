package com.fb2.obd

import com.fb2.obd.ui.dash.OrbitWheelPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.absoluteValue

class OrbitWheelPhysicsTest {

    @Test
    fun dragPastHalfItem_advancesCenterAndFoldsOffset() {
        val start = OrbitWheelPhysics.applyDrag(
            center = 2,
            offsetPx = 0f,
            deltaPx = 60f,
            itemHeightPx = 100f,
            count = 8,
        )
        assertEquals(1, start.center)
        assertEquals(-40f, start.offsetPx, 0.01f)
    }

    @Test
    fun smallDrag_doesNotJumpIndex() {
        val next = OrbitWheelPhysics.applyDrag(2, 0f, 20f, 100f, 8)
        assertEquals(2, next.center)
        assertEquals(20f, next.offsetPx, 0.01f)
    }

    @Test
    fun dragWrapsAround() {
        val next = OrbitWheelPhysics.applyDrag(0, 0f, 80f, 100f, 5)
        assertEquals(4, next.center)
    }

    @Test
    fun flingDelta_scalesWithVelocityAndCaps() {
        assertEquals(0f, OrbitWheelPhysics.flingDeltaPx(0f, 80f), 0.01f)
        val mild = OrbitWheelPhysics.flingDeltaPx(400f, 80f)
        val hard = OrbitWheelPhysics.flingDeltaPx(4000f, 80f)
        assertTrue(hard.absoluteValue > mild.absoluteValue)
        assertEquals(80f * 5f, hard, 0.01f)
    }

    @Test
    fun successivePixelDrags_matchSingleJump() {
        var state = OrbitWheelPhysics.State(3, 0f)
        repeat(60) {
            state = OrbitWheelPhysics.applyDrag(state.center, state.offsetPx, 1f, 100f, 8)
        }
        val once = OrbitWheelPhysics.applyDrag(3, 0f, 60f, 100f, 8)
        assertEquals(once.center, state.center)
        assertEquals(once.offsetPx, state.offsetPx, 0.01f)
    }
}
