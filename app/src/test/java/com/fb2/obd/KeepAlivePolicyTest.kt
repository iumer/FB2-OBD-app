package com.fb2.obd

import com.fb2.obd.obd.KeepAlivePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAlivePolicyTest {

    @Test
    fun reconnectsAfterDeath_whenLastElmAndNotUserDisconnect() {
        assertTrue(KeepAlivePolicy.shouldReconnectAfterDeath("AA:BB:CC:DD:EE:FF", userDisconnected = false))
        assertFalse(KeepAlivePolicy.shouldReconnectAfterDeath("AA:BB:CC:DD:EE:FF", userDisconnected = true))
        assertFalse(KeepAlivePolicy.shouldReconnectAfterDeath(null, userDisconnected = false))
        assertFalse(KeepAlivePolicy.shouldReconnectAfterDeath("  ", userDisconnected = false))
    }

    @Test
    fun batteryPrompt_onlyWhenLiveAndNotIgnoring() {
        assertTrue(KeepAlivePolicy.shouldPromptBatteryExemption(ignoringOptimizations = false, liveElmConnected = true))
        assertFalse(KeepAlivePolicy.shouldPromptBatteryExemption(ignoringOptimizations = true, liveElmConnected = true))
        assertFalse(KeepAlivePolicy.shouldPromptBatteryExemption(ignoringOptimizations = false, liveElmConnected = false))
    }

    @Test
    fun batteryRow_saysAllowedOnceExemptionGranted() {
        assertEquals("ALLOW", KeepAlivePolicy.batteryExemptionActionLabel(allowed = false))
        assertEquals("ALLOWED", KeepAlivePolicy.batteryExemptionActionLabel(allowed = true))
    }

    @Test
    fun fgsStaysUp_whileLiveOrLogging() {
        assertTrue(KeepAlivePolicy.shouldKeepForegroundService(liveElmConnected = true, valueLogging = false))
        assertTrue(KeepAlivePolicy.shouldKeepForegroundService(liveElmConnected = false, valueLogging = true))
        assertFalse(KeepAlivePolicy.shouldKeepForegroundService(liveElmConnected = false, valueLogging = false))
    }
}
