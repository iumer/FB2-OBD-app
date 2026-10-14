package com.fb2.obd

import com.fb2.obd.data.GoogleDriveApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveApiTest {

    @Test
    fun sanitize_stripsBearerAndYa29() {
        val raw = """HTTP 401 {"error":"invalid","Authorization":"Bearer ya29.a0AfH6SMB_secret_token_here"}"""
        val out = GoogleDriveApi.sanitize(raw)
        assertFalse(out.contains("ya29.a0Af"))
        assertTrue(out.contains("Bearer …") || out.contains("ya29…"))
    }

    @Test
    fun escapeQuery_escapesQuotes() {
        assertEquals("O\\'Reilly", GoogleDriveApi.escapeQuery("O'Reilly"))
    }

    @Test
    fun mimeForName() {
        assertEquals("text/csv", GoogleDriveApi.mimeForName("a.csv"))
        assertEquals("application/gzip", GoogleDriveApi.mimeForName("a.csv.gz"))
        assertEquals("text/plain", GoogleDriveApi.mimeForName("report.txt"))
    }

    @Test
    fun defaultFolderName() {
        assertEquals("FB2 Diag Logs", GoogleDriveApi.DEFAULT_FOLDER_NAME)
    }
}
