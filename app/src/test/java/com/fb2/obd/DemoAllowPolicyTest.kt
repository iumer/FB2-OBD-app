package com.fb2.obd

import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.DemoAllowPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAllowPolicyTest {

    @Test
    fun stopWhileDemo_disconnects() {
        assertEquals(
            DemoAllowPolicy.Next.DISCONNECT,
            DemoAllowPolicy.next(
                enabled = false,
                sourceIsLive = false,
                connection = ConnectionState.CONNECTED,
            ),
        )
    }

    @Test
    fun stopWhileIdle_stillDisconnects() {
        assertEquals(
            DemoAllowPolicy.Next.DISCONNECT,
            DemoAllowPolicy.next(
                enabled = false,
                sourceIsLive = false,
                connection = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun stopWhileLiveElm_leavesAdapterRunning() {
        assertEquals(
            DemoAllowPolicy.Next.NONE,
            DemoAllowPolicy.next(
                enabled = false,
                sourceIsLive = true,
                connection = ConnectionState.CONNECTED,
            ),
        )
    }

    @Test
    fun enableWhileDisconnected_startsDemo() {
        assertEquals(
            DemoAllowPolicy.Next.START_DEMO,
            DemoAllowPolicy.next(
                enabled = true,
                sourceIsLive = false,
                connection = ConnectionState.DISCONNECTED,
            ),
        )
    }

    @Test
    fun enableWhileAlreadyDemo_noRestart() {
        assertEquals(
            DemoAllowPolicy.Next.NONE,
            DemoAllowPolicy.next(
                enabled = true,
                sourceIsLive = false,
                connection = ConnectionState.CONNECTED,
            ),
        )
    }

    @Test
    fun isDemoRunning_onlyWhenConnectedAndNotLive() {
        assertTrue(
            DemoAllowPolicy.isDemoRunning(
                sourceIsLive = false,
                connection = ConnectionState.CONNECTED,
            ),
        )
        assertFalse(
            DemoAllowPolicy.isDemoRunning(
                sourceIsLive = true,
                connection = ConnectionState.CONNECTED,
            ),
        )
        assertFalse(
            DemoAllowPolicy.isDemoRunning(
                sourceIsLive = false,
                connection = ConnectionState.DISCONNECTED,
            ),
        )
    }
}
