package com.fb2.obd

import com.fb2.obd.data.AiReportStore
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReportStoreTest {

    @Test
    fun fullReportText_includesFindingsAndReadingsAppendix() {
        val text = AiReportStore.buildFullReportText(
            body = "AI VEHICLE ANALYSIS REPORT\n\nOVERALL ASSESSMENT\n- Looks OK",
            sourceLabel = "live_window_5min",
            windowMinutes = 5,
            model = "gpt-4o-mini",
            readingsAppendix = """
                --- Latest snapshot ---
                rpm=1800.0
                coolant1_c=88.0

                --- Time-window CSV (5 min, 12 rows) ---
                # dashboard_snapshots
                time_ms,rpm,speed_kmh,coolant1_c
                1,1800,40,88
            """.trimIndent(),
            createdMs = 1_700_000_000_000L,
        )
        assertTrue(text.contains("===== AI FINDINGS ====="))
        assertTrue(text.contains("OVERALL ASSESSMENT"))
        assertTrue(text.contains("===== READINGS SENT TO AI (audit table) ====="))
        assertTrue(text.contains("rpm=1800.0"))
        assertTrue(text.contains("# dashboard_snapshots"))
        assertTrue(text.contains("source=live_window_5min"))
    }
}
