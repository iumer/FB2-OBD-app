package com.fb2.obd

import com.fb2.obd.obd.AiAnalysisPayloadBuilder
import com.fb2.obd.obd.HealthScore
import com.fb2.obd.obd.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAnalysisPayloadBuilderTest {

    @Test
    fun clampWindow_respectsBounds() {
        assertEquals(1, AiAnalysisPayloadBuilder.clampWindowMinutes(0))
        assertEquals(15, AiAnalysisPayloadBuilder.clampWindowMinutes(99))
        assertEquals(5, AiAnalysisPayloadBuilder.clampWindowMinutes(5))
    }

    @Test
    fun systemPrompt_mentionsFb2AndReadOnly() {
        val p = AiAnalysisPayloadBuilder.SYSTEM_PROMPT
        assertTrue(p.contains("Civic FB2"))
        assertTrue(p.contains("Pakistani") || p.contains("UG"))
        assertTrue(p.contains("read-only") || p.contains("READ ONLY") || p.contains("Remain read-only"))
        assertTrue(p.contains("AI VEHICLE ANALYSIS REPORT"))
        assertTrue(p.contains("ANALYZED VALUES"))
        assertTrue(p.contains("Do not invent") || p.contains("do not invent"))
        assertTrue(p.contains(".txt"))
        assertTrue(p.contains("===SCREEN_BRIEF==="))
        assertTrue(p.contains("===FULL_REPORT==="))
        assertTrue(p.contains("APP ZONE LABELS"))
        assertTrue(p.contains("MAF CRITICAL"))
        assertTrue(p.contains("Vehicle and session information"))
        assertTrue(p.contains("Key readings"))
        assertTrue(p.contains("WORDING AND HONDA-SPECIFIC GUIDANCE"))
        assertTrue(p.contains("ELD") || p.contains("electrical load detection"))
        assertTrue(p.contains("selected analysis window"))
    }

    @Test
    fun parseModelResponse_splitsBriefAndFull() {
        val raw = """
            ===SCREEN_BRIEF===
            Vehicle and session information
            Honda Civic FB2 — 5 min window
            Overall result
            Looks normal for this window.
            Summary
            Coolant stayed warm and stable.
            Key readings
            Coolant
            88–94 °C
            Normal
            Items to monitor
            None from this window.
            Unavailable data
            MAF column empty in this window.

            ===FULL_REPORT===
            AI VEHICLE ANALYSIS REPORT
            VEHICLE
            - Honda Civic FB2
            ANALYZED VALUES
            - Coolant min 88 max 94
        """.trimIndent()
        val parsed = AiAnalysisPayloadBuilder.parseModelResponse(raw)
        assertTrue(parsed.hadMarkers)
        assertTrue(parsed.screenBrief.contains("Vehicle and session information"))
        assertTrue(parsed.screenBrief.contains("Looks normal"))
        assertFalse(parsed.screenBrief.contains("AI VEHICLE ANALYSIS REPORT"))
        assertTrue(parsed.fullReport.startsWith("AI VEHICLE ANALYSIS REPORT"))
        assertTrue(parsed.fullReport.contains("ANALYZED VALUES"))
    }

    @Test
    fun parseModelResponse_fallbackWithoutMarkers() {
        val raw = "Plain reply without markers\nCoolant OK"
        val parsed = AiAnalysisPayloadBuilder.parseModelResponse(raw)
        assertFalse(parsed.hadMarkers)
        assertEquals(raw, parsed.screenBrief)
        assertEquals(raw, parsed.fullReport)
    }

    @Test
    fun truncateByTime_keepsOnlyRecentRows() {
        val now = 1_000_000_000L
        val snaps = (0 until 20).map { i ->
            val t = now - (20 - i) * 60_000L // 20 min of 1/min samples ending at now
            t to "$t,800,0,90,,,,,,,,,,,,,,,"
        }
        val log = AiAnalysisPayloadBuilder.truncateByTime(snaps, emptyList(), windowMinutes = 5, nowMs = now)
        assertTrue(log.rowCount <= 6) // ~5 minutes of 1/min + boundary
        assertTrue(log.rowCount >= 4)
        assertTrue(log.csvText.contains("# dashboard_snapshots"))
        assertTrue(log.limited) // older samples dropped
    }

    @Test
    fun truncateSavedCsv_parsesSections() {
        val t0 = 2_000_000_000L
        val csv = buildString {
            appendLine("# fb2_session_log")
            appendLine("# events")
            appendLine("time_ms,category,message")
            appendLine("${t0 - 60_000},ZONE,coolant GOOD → WARN")
            appendLine("$t0,ZONE,coolant WARN → GOOD")
            appendLine()
            appendLine("# dashboard_snapshots")
            appendLine("time_ms,rpm,speed_kmh,coolant1_c,coolant2_c,intake_c,ambient_c,load_pct,throttle_pct,timing,maf_gps,map_kpa,stft_pct,ltft_pct,ecu_v,gear,fuel_loop")
            appendLine("${t0 - 120_000},800,0,88,,,,,,,,,,,,,,CLOSED LOOP")
            appendLine("${t0 - 30_000},900,10,90,,,,,,,,,,,,,,CLOSED LOOP")
            appendLine("$t0,1000,20,91,,,,,,,,,,,,,,CLOSED LOOP")
        }
        val log = AiAnalysisPayloadBuilder.truncateSavedCsv(csv, windowMinutes = 1, nowMs = t0)
        assertEquals(2, log.rowCount) // last 1 minute: -30s and 0
        assertTrue(log.eventCount >= 1)
        assertEquals(t0 - 30_000, log.firstTimestampMs)
        assertEquals(t0, log.lastTimestampMs)
        assertEquals(30L, log.actualDurationSeconds)
    }

    @Test
    fun formatIsoUtc_usesYearFromEpoch() {
        // 2026-07-27 20:04:10 UTC ≈ 1785182650255 (from real FB2 log)
        val iso = AiAnalysisPayloadBuilder.formatIsoUtc(1_785_182_650_255L)
        assertTrue(iso.startsWith("2026-"))
        assertTrue(iso.contains("UTC"))
    }

    @Test
    fun buildUserMessage_includesAppComputedWindowMeta() {
        val truncated = AiAnalysisPayloadBuilder.TruncatedLog(
            csvText = "# dashboard_snapshots\n1000,800,0,90,,,,,,,,,,,,,,\n1060000,900,10,91,,,,,,,,,,,,,,",
            rowCount = 2,
            eventCount = 0,
            limited = true,
            windowMinutesUsed = 1,
            firstTimestampMs = 1_785_182_650_255L,
            lastTimestampMs = 1_785_182_710_255L,
            uniqueTimestampCount = 2,
            nearDuplicateRowCount = 0,
        )
        val payload = AiAnalysisPayloadBuilder.buildUserMessage(
            sourceLabel = "saved:FB2-log-test.csv",
            windowMinutes = 1,
            snapshotText = "rpm=900",
            healthText = "(none)",
            dtcText = "(none)",
            log = truncated,
        )
        assertTrue(payload.userMessage.contains("Requested lookback window: 1 minutes"))
        assertTrue(payload.userMessage.contains("Actual selected window duration (seconds): 60"))
        assertTrue(payload.userMessage.contains("Selected window start (UTC, app-computed): 2026-"))
        assertTrue(payload.userMessage.contains("Do not invent or re-convert"))
        assertTrue(payload.userMessage.contains("Honda ELD") || payload.userMessage.contains("ELD"))
        assertEquals(60L, payload.actualDurationSeconds)
    }

    @Test
    fun buildUserMessage_includesSnapshotHealthAndWindow() {
        val snap = VehicleSnapshot(rpm = 1800.0, coolantC = 88.0, batteryVolts = 14.1)
        val health = HealthScore(
            enginePct = 90,
            transmissionPct = null,
            engineNotes = listOf("Core engine parameters look OK"),
            engineDataOk = true,
        )
        val truncated = AiAnalysisPayloadBuilder.TruncatedLog(
            csvText = "# dashboard_snapshots\n100,1800,40,88,,,,,,,,,,,,,,",
            rowCount = 1,
            eventCount = 0,
            limited = false,
            windowMinutesUsed = 3,
        )
        val payload = AiAnalysisPayloadBuilder.buildUserMessage(
            sourceLabel = "live_window_3min",
            windowMinutes = 3,
            snapshotText = AiAnalysisPayloadBuilder.formatSnapshot(snap),
            healthText = AiAnalysisPayloadBuilder.formatHealth(health),
            dtcText = "(none)",
            log = truncated,
        )
        assertTrue(payload.userMessage.contains("live_window_3min"))
        assertTrue(payload.userMessage.contains("1800"))
        assertTrue(payload.userMessage.contains("Core engine"))
        assertTrue(payload.userMessage.contains("=== LOG WINDOW"))
        assertEquals(3, payload.windowMinutes)
        // thin window note
        assertTrue(payload.limited)
    }

    @Test
    fun buildUserMessage_demoNoteWhenIsDemo() {
        val truncated = AiAnalysisPayloadBuilder.TruncatedLog(
            csvText = "# dashboard_snapshots\n100,800,0,90,,,,,,,,,,,,,,",
            rowCount = 10,
            eventCount = 0,
            limited = false,
            windowMinutesUsed = 5,
        )
        val payload = AiAnalysisPayloadBuilder.buildUserMessage(
            sourceLabel = "demo_live_window_5min",
            windowMinutes = 5,
            snapshotText = "rpm=800",
            healthText = "(none)",
            dtcText = "(none)",
            log = truncated,
            isDemo = true,
        )
        assertTrue(payload.userMessage.contains("DEMO mode"))
        assertTrue(payload.userMessage.contains("simulated"))
    }

    @Test
    fun maxPayload_truncatesHugeCsv() {
        val now = 5_000_000_000L
        val hugeLine = "x".repeat(2_000)
        val snaps = (0 until 200).map { i ->
            val t = now - i * 1_000L
            t to "$t,$hugeLine"
        }.reversed()
        val log = AiAnalysisPayloadBuilder.truncateByTime(
            snaps,
            emptyList(),
            windowMinutes = 15,
            nowMs = now,
            maxRows = 150,
            maxChars = 5_000,
        )
        assertTrue(log.csvText.length <= 5_000 + 50)
        assertTrue(log.limited)
    }
}
