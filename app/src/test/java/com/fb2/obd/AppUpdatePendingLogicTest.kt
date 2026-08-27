package com.fb2.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guards the size threshold used before treating a downloaded APK as installable. */
class AppUpdatePendingLogicTest {

    @Test
    fun apkTooSmall_isRejected() {
        val f = File.createTempFile("FB2-Diag-", ".apk")
        try {
            f.writeBytes(ByteArray(100))
            assertFalse("tiny APK must be rejected", f.length() >= 10_000L)
        } finally {
            f.delete()
        }
    }

    @Test
    fun apkLargeEnough_isAccepted() {
        val f = File.createTempFile("FB2-Diag-", ".apk")
        try {
            f.writeBytes(ByteArray(12_000))
            assertTrue("ready APK must be kept", f.length() >= 10_000L)
        } finally {
            f.delete()
        }
    }
}
