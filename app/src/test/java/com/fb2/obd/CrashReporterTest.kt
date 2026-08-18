package com.fb2.obd

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.fb2.obd.data.CrashReporter
import com.fb2.obd.data.ObdLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The crash reporter exists because the app has died on the user's phone where
 * no logcat is available. If it silently failed to write, we would still be
 * guessing, so these tests cover the write path itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Fb2App::class)
class CrashReporterTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        CrashReporter.clearReports(app)
    }

    @Test
    fun report_containsStackTraceDeviceAndVersion() {
        val boom = IllegalStateException("connect exploded")
        val text = CrashReporter.buildReport("0.1.34", 34, "main", boom)

        assertTrue("must name the exception", text.contains("java.lang.IllegalStateException"))
        assertTrue("must keep the message", text.contains("connect exploded"))
        assertTrue("must include a stack trace", text.contains("=== stack trace ==="))
        assertTrue("must record the app version", text.contains("0.1.34"))
        assertTrue("must record the thread", text.contains("main"))
        assertTrue("must record Android level", text.contains("API 34"))
    }

    /** The ELM traffic before the crash is the whole point of the report. */
    @Test
    fun report_includesRecentElmLog() {
        ObdLogger.clearDebug()
        ObdLogger.logDebug(ObdLogger.Dir.TX, "010C")
        ObdLogger.logDebug(ObdLogger.Dir.RX, "41 0C 1A F8")

        val text = CrashReporter.buildReport("0.1.34", 34, "main", RuntimeException("x"))

        assertTrue("must attach the ELM log section", text.contains("=== recent ELM / app log ==="))
        assertTrue("must contain the last request", text.contains("010C"))
        assertTrue("must contain the last response", text.contains("41 0C 1A F8"))
    }

    @Test
    fun write_persistsReportThatCanBeReadBack() {
        val file = CrashReporter.write(app, "0.1.34", 34, "main", RuntimeException("disk"))

        assertTrue("report file must exist", file.exists())
        assertTrue("report must not be empty", file.length() > 0)
        assertTrue(file.name.startsWith("FB2-crash-"))
        assertTrue(file.readText().contains("disk"))

        val latest = CrashReporter.latestReport(app)
        assertNotNull(latest)
        assertEquals(file.absolutePath, latest!!.absolutePath)
    }

    @Test
    fun installedHandler_writesReportForUncaughtException() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        try {
            CrashReporter.install(app, "0.1.34", 34)
            val handler = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull("handler must be installed", handler)

            // Deliver directly instead of killing the test JVM with a real throw.
            handler!!.uncaughtException(Thread.currentThread(), IllegalArgumentException("boom"))

            val latest = CrashReporter.latestReport(app)
            assertNotNull("uncaught exception must produce a report", latest)
            assertTrue(latest!!.readText().contains("boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }

    @Test
    fun clearReports_removesEverything() {
        CrashReporter.write(app, "0.1.34", 34, "main", RuntimeException("a"))
        assertTrue(CrashReporter.listReports(app).isNotEmpty())

        CrashReporter.clearReports(app)
        assertTrue(CrashReporter.listReports(app).isEmpty())
    }
}
