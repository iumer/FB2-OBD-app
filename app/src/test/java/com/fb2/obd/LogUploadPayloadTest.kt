package com.fb2.obd

import com.fb2.obd.data.LogUploadPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUploadPayloadTest {

    @Test
    fun smallFile_staysUncompressed() {
        val (name, bytes) = LogUploadPayload.prepare("FB2-log.csv", ByteArray(100) { 'x'.code.toByte() })
        assertEquals("FB2-log.csv", name)
        assertEquals(100, bytes.size)
    }

    @Test
    fun largeCsv_isGzipped() {
        val raw = ByteArray(80_000) { i -> ('A'.code + (i % 26)).toByte() }
        val (name, bytes) = LogUploadPayload.prepare("FB2-log-demo.csv", raw)
        assertEquals("FB2-log-demo.csv.gz", name)
        assertTrue(bytes.size < raw.size)
        assertTrue(bytes.size < 8_000)
    }
}
