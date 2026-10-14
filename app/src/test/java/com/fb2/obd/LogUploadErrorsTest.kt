package com.fb2.obd

import com.fb2.obd.data.LogUploadErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUploadErrorsTest {

    @Test
    fun http403_mentionsDrivePermission() {
        val msg = LogUploadErrors.friendly("HTTP 403 insufficientPermissions")
        assertTrue(msg.contains("Drive", ignoreCase = true) || msg.contains("permission", ignoreCase = true))
    }

    @Test
    fun http401_asksResignIn() {
        val msg = LogUploadErrors.friendly("HTTP 401 invalid_grant")
        assertTrue(msg.contains("Sign", ignoreCase = true))
    }

    @Test
    fun summarize_includesFirstFailureDetail() {
        val msg = LogUploadErrors.summarize(
            uploaded = 0,
            already = 0,
            failed = 1,
            firstFailure = "FB2-log-x.csv: HTTP 403 insufficientPermissions",
            empty = false,
        )
        assertTrue(msg.contains("Failed 1"))
        assertTrue(msg.contains("403") || msg.contains("Drive") || msg.contains("permission"))
    }

    @Test
    fun noInternet() {
        assertEquals(
            "No internet — HU Wi‑Fi often needs a real data path, not just a connected icon.",
            LogUploadErrors.NO_INTERNET,
        )
    }

    @Test
    fun timeout_mentionsSlowHu() {
        val msg = LogUploadErrors.friendly("timeout: Read timed out")
        assertTrue(msg.contains("timed out", ignoreCase = true))
    }

    @Test
    fun noAccount_pointsAtSettings() {
        assertTrue(LogUploadErrors.NO_ACCOUNT.contains("Google", ignoreCase = true))
        assertTrue(LogUploadErrors.NO_FOLDER.contains("folder", ignoreCase = true))
    }
}
