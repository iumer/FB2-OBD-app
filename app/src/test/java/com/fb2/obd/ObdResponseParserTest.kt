package com.fb2.obd

import com.fb2.obd.obd.ObdPid
import com.fb2.obd.obd.ObdResponseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdResponseParserTest {

    @Test
    fun rpm_decodesFromSpacedFrame() {
        // 41 0C 1A F8 -> ((0x1A*256)+0xF8)/4 = (6912+... ) actually (0x1AF8=6904)/4 = 1726
        val value = ObdResponseParser.parse(ObdPid.ENGINE_RPM, "41 0C 1A F8")
        assertEquals(1726.0, value!!, 0.001)
    }

    @Test
    fun rpm_decodesFromCompactFrameWithPrompt() {
        val value = ObdResponseParser.parse(ObdPid.ENGINE_RPM, "410C1AF8\r\r>")
        assertEquals(1726.0, value!!, 0.001)
    }

    @Test
    fun speed_decodes() {
        val value = ObdResponseParser.parse(ObdPid.SPEED, "41 0D 40")
        assertEquals(64.0, value!!, 0.001)
    }

    @Test
    fun coolant_appliesMinus40Offset() {
        val value = ObdResponseParser.parse(ObdPid.COOLANT_TEMP, "41 05 7B") // 0x7B=123 -> 83C
        assertEquals(83.0, value!!, 0.001)
    }

    @Test
    fun coolant2_decodesSensor2FromPid67() {
        // 41 67 [support=03, bit1 set] [B=7B->83C sensor1] [C=78->80C sensor2].
        val value = ObdResponseParser.parse(ObdPid.COOLANT_TEMP_2, "41 67 03 7B 78")
        assertEquals(80.0, value!!, 0.001)
    }

    @Test
    fun coolant2_nullWhenSensor2NotSupported() {
        // Support byte 0x01 -> only sensor 1 supported; sensor 2 must be null even
        // if a byte is present.
        assertNull(ObdResponseParser.parse(ObdPid.COOLANT_TEMP_2, "41 67 01 7B 78"))
    }

    @Test
    fun coolant2_nullWhenSensor2Absent() {
        assertNull(ObdResponseParser.parse(ObdPid.COOLANT_TEMP_2, "41 67 01 7B"))
    }

    @Test
    fun multiEcuFrame_slicedToExactDataLength() {
        // Two modules answer 0x67; exact-length slicing must use only the first
        // frame's 3 data bytes, not append the second module's bytes.
        val value = ObdResponseParser.parse(ObdPid.COOLANT_TEMP_2, "41 67 03 7B 78 41 67 03 7C 79")
        assertEquals(80.0, value!!, 0.001)
    }

    @Test
    fun engineLoad_scalesToPercent() {
        val value = ObdResponseParser.parse(ObdPid.ENGINE_LOAD, "41 04 FF")
        assertEquals(100.0, value!!, 0.001)
    }

    @Test
    fun controlModuleVoltage_decodesMilliVolts() {
        // 0x37 0x70 = 14192 mV -> 14.192 V
        val value = ObdResponseParser.parse(ObdPid.CONTROL_MODULE_VOLTAGE, "41 42 37 70")
        assertEquals(14.192, value!!, 0.001)
    }

    @Test
    fun shortTermFuelTrim_centersAt128() {
        val value = ObdResponseParser.parse(ObdPid.STFT_B1, "41 06 80") // 0x80=128 -> 0%
        assertEquals(0.0, value!!, 0.001)
    }

    @Test
    fun ignoresEcho_andSearching() {
        val value = ObdResponseParser.parse(ObdPid.ENGINE_RPM, "010C\rSEARCHING...\r41 0C 1A F8\r>")
        assertEquals(1726.0, value!!, 0.001)
    }

    @Test
    fun noData_returnsNull() {
        assertNull(ObdResponseParser.parse(ObdPid.ENGINE_RPM, "NO DATA\r>"))
    }

    @Test
    fun atVoltage_parses() {
        assertEquals(12.5, ObdResponseParser.parseAtVoltage("12.5V\r>")!!, 0.001)
    }

    @Test
    fun transmissionGearRatio_decodesFromPidA4() {
        // 41 A4 [support=02, bit1 set] [gearbits=00] [C=04][D=27] -> (256*4+39)/1000 = 1.063
        val ratio = ObdResponseParser.parse(ObdPid.TRANSMISSION_GEAR_RATIO, "41 A4 02 00 04 27")
        assertEquals(1.063, ratio!!, 0.001)
    }

    @Test
    fun transmissionGearRatio_nullWhenUnsupported() {
        assertNull(ObdResponseParser.parse(ObdPid.TRANSMISSION_GEAR_RATIO, "41 A4 00 00 04 27"))
    }

    @Test
    fun responseHeader_isModePlus0x40() {
        assertTrue(ObdPid.ENGINE_RPM.responseHeader == "410C")
        assertTrue(ObdPid.CONTROL_MODULE_VOLTAGE.responseHeader == "4142")
    }
}
