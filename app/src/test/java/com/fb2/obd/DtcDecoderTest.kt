package com.fb2.obd

import com.fb2.obd.obd.DtcDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcDecoderTest {

    @Test
    fun decodePair_buildsCode() {
        assertEquals("P0133", DtcDecoder.decodePair(0x01, 0x33))
        assertEquals("P0420", DtcDecoder.decodePair(0x04, 0x20))
        assertEquals("U0100", DtcDecoder.decodePair(0xC1, 0x00))
        assertEquals("C1234", DtcDecoder.decodePair(0x52, 0x34))
    }

    @Test
    fun decode_canFrameWithCountByte() {
        // 43 02 (count) 01 33 04 20
        val codes = DtcDecoder.decode("43 02 01 33 04 20", 0x43).map { it.code }
        assertEquals(listOf("P0133", "P0420"), codes)
    }

    @Test
    fun decode_withoutCountByte() {
        val codes = DtcDecoder.decode("43 01 33 04 20", 0x43).map { it.code }
        assertEquals(listOf("P0133", "P0420"), codes)
    }

    @Test
    fun decode_noCodes_returnsEmpty() {
        assertTrue(DtcDecoder.decode("43 00", 0x43).isEmpty())
        assertTrue(DtcDecoder.decode("NO DATA", 0x43).isEmpty())
    }

    @Test
    fun decode_pending_usesMode47() {
        val codes = DtcDecoder.decode("47 01 01 33", 0x47).map { it.code }
        assertEquals(listOf("P0133"), codes)
    }

    @Test
    fun decode_attachesDescription() {
        val dtc = DtcDecoder.decode("43 01 33", 0x43).first()
        assertTrue(dtc.description.contains("O2 sensor"))
    }
}
