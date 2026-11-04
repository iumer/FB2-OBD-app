package com.fb2.obd

import com.fb2.obd.data.AiReportStore
import com.fb2.obd.obd.AppAttribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAttributionTest {

    @Test
    fun credit_is_iUmer_Farooq() {
        assertEquals("iUmer Farooq", AppAttribution.AUTHOR_NAME)
        assertEquals("Built by iUmer Farooq", AppAttribution.BUILT_BY)
        assertTrue(AppAttribution.PROJECT_URL.contains("iumer/FB2-OBD-app"))
        assertTrue(AppAttribution.COPYRIGHT.contains("iUmer Farooq"))
    }

    @Test
    fun buildConfig_matches_attribution() {
        assertEquals(AppAttribution.AUTHOR_NAME, BuildConfig.AUTHOR_NAME)
        assertEquals(AppAttribution.BUILT_BY, BuildConfig.BUILT_BY)
        assertEquals(AppAttribution.PROJECT_URL, BuildConfig.PROJECT_URL)
    }

    @Test
    fun aiReport_header_includes_builder() {
        val text = AiReportStore.buildFullReportText(
            body = "ok",
            sourceLabel = "live",
            windowMinutes = 5,
            model = "test",
            readingsAppendix = "",
            createdMs = 1L,
        )
        assertTrue(text.contains("# Built by iUmer Farooq"))
        assertTrue(text.contains("# https://github.com/iumer/FB2-OBD-app"))
        assertTrue(text.contains("# Copyright (c) 2026 iUmer Farooq"))
    }

    @Test
    fun license_file_names_author() {
        val license = java.io.File("../LICENSE").takeIf { it.isFile }
            ?: java.io.File("LICENSE").takeIf { it.isFile }
            ?: error("LICENSE not found from test cwd=${System.getProperty("user.dir")}")
        val body = license.readText()
        assertTrue(body.contains("Copyright (c) 2026 iUmer Farooq"))
        assertTrue(body.contains("MIT License"))
    }
}
