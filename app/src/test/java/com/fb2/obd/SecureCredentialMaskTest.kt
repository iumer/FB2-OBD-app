package com.fb2.obd

import com.fb2.obd.data.SecureCredentialStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureCredentialMaskTest {

    @Test
    fun maskOpenAiKey() {
        val masked = SecureCredentialStore.maskSecret("sk-abcdefghijklmnopqrstuvwxyz012345")
        assertTrue(masked.startsWith("sk-"))
        assertTrue(masked.contains("…"))
        assertTrue(masked.endsWith("2345"))
        assertFalse(masked.contains("abcdefghijklmnop"))
    }

    @Test
    fun maskGithubPat() {
        val masked = SecureCredentialStore.maskSecret("github_pat_11AAAAAAA_abcdefghijklmnopqrstuv")
        assertTrue(masked.startsWith("github_pat_"))
        assertTrue(masked.contains("…"))
        assertFalse(masked.contains("11AAAAAAA"))
    }

    @Test
    fun maskGhp() {
        val masked = SecureCredentialStore.maskSecret("ghp_abcdefghijklmnopqrstuvwx1234")
        assertTrue(masked.startsWith("ghp_"))
        assertTrue(masked.contains("…"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", SecureCredentialStore.maskSecret("  "))
    }

    @Test
    fun defaultLogsRepoIsPrivateName() {
        assertEquals("iumer", SecureCredentialStore.DEFAULT_OWNER)
        assertEquals("FB2-OBD-logs", SecureCredentialStore.DEFAULT_REPO)
    }
}
