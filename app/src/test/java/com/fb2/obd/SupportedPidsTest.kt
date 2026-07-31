package com.fb2.obd

import com.fb2.obd.obd.SupportedPids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedPidsTest {

    @Test
    fun firstBit_mapsToBasePlusOne() {
        val set = SupportedPids.fromBitmask(0x00, intArrayOf(0x80, 0x00, 0x00, 0x00))
        assertEquals(setOf(0x01), set)
    }

    @Test
    fun lastBit_mapsToBasePlus0x20() {
        val set = SupportedPids.fromBitmask(0x00, intArrayOf(0x00, 0x00, 0x00, 0x01))
        assertEquals(setOf(0x20), set)
    }

    @Test
    fun coolant2Pid_67_detectedInBlock60() {
        // bit1 of byte0 in the $61-$80 block == PID 0x67.
        val set = SupportedPids.fromBitmask(0x60, intArrayOf(0x02, 0x00, 0x00, 0x00))
        assertTrue(0x67 in set)
    }

    @Test
    fun shortResponse_returnsEmpty() {
        assertTrue(SupportedPids.fromBitmask(0x00, intArrayOf(0x80)).isEmpty())
    }

    @Test
    fun unsetBits_notReported() {
        val set = SupportedPids.fromBitmask(0x00, intArrayOf(0x00, 0x00, 0x00, 0x00))
        assertFalse(0x01 in set)
        assertTrue(set.isEmpty())
    }
}
