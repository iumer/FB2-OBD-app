package com.fb2.obd

import com.fb2.obd.data.LogUploadErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUploadErrorsTest {

    @Test
    fun http403_mentionsPatWrite() {
        val msg = LogUploadErrors.friendly("HTTP 403 Resource not accessible by personal access token")
        assertTrue(msg.contains("Contents: Write", ignoreCase = true) || msg.contains("token", ignoreCase = true))
    }

    @Test
    fun http401_isAuth() {
        val msg = LogUploadErrors.friendly("HTTP 401 Bad credentials")
        assertTrue(msg.contains("token", ignoreCase = true))
    }

    @Test
    fun summarize_includesFirstFailureDetail() {
        val msg = LogUploadErrors.summarize(
            uploaded = 0,
            already = 0,
            failed = 1,
            firstFailure = "FB2-log-x.csv: HTTP 403 Resource not accessible",
            empty = false,
        )
        assertTrue(msg.contains("Failed 1"))
        assertTrue(msg.contains("403"))
    }

    @Test
    fun noInternet() {
        assertEquals("No internet — HU Wi‑Fi often needs a real data path, not just a connected icon.", LogUploadErrors.NO_INTERNET)
    }

    @Test
    fun timeout_mentionsSlowHu() {
        val msg = LogUploadErrors.friendly("timeout: Read timed out")
        assertTrue(msg.contains("timed out", ignoreCase = true))
    }
}
