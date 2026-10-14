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
    fun emptyStaysEmpty() {
        assertEquals("", SecureCredentialStore.maskSecret("  "))
    }
}
