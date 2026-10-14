package com.fb2.obd

import com.fb2.obd.data.ConnectionState
import com.fb2.obd.obd.ConnectActionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectActionPolicyTest {

    @Test
    fun liveElm_showsDisconnect() {
        val a = ConnectActionPolicy.of(
            connection = ConnectionState.CONNECTED,
            sourceIsLive = true,
        )
        assertEquals("DISCONNECT", a.label)
        assertEquals(ConnectActionPolicy.Kind.DISCONNECT, a.kind)
        assertTrue(
            ConnectActionPolicy.isDisconnectAction(
                ConnectionState.CONNECTED,
                sourceIsLive = true,
            ),
        )
    }

    @Test
    fun demo_keepsConnect() {
        val a = ConnectActionPolicy.of(
            connection = ConnectionState.CONNECTED,
            sourceIsLive = false,
        )
        assertEquals("CONNECT", a.label)
        assertEquals(ConnectActionPolicy.Kind.CONNECT, a.kind)
        assertFalse(
            ConnectActionPolicy.isDisconnectAction(
                ConnectionState.CONNECTED,
                sourceIsLive = false,
            ),
        )
    }

    @Test
    fun retryWhileLive_isNotDisconnect() {
        val a = ConnectActionPolicy.of(
            connection = ConnectionState.CONNECTING,
            sourceIsLive = true,
            reconnecting = true,
        )
        assertEquals("RETRY…", a.label)
        assertEquals(ConnectActionPolicy.Kind.RETRY, a.kind)
        assertFalse(
            ConnectActionPolicy.isDisconnectAction(
                ConnectionState.CONNECTING,
                sourceIsLive = true,
                reconnecting = true,
            ),
        )
    }

    @Test
    fun error_isReconnect() {
        val a = ConnectActionPolicy.of(
            connection = ConnectionState.ERROR,
            sourceIsLive = true,
        )
        assertEquals("RECONNECT", a.label)
        assertEquals(ConnectActionPolicy.Kind.RECONNECT, a.kind)
    }

    @Test
    fun idle_isConnect() {
        val a = ConnectActionPolicy.of(
            connection = ConnectionState.DISCONNECTED,
            sourceIsLive = false,
        )
        assertEquals("CONNECT", a.label)
        assertEquals(ConnectActionPolicy.Kind.CONNECT, a.kind)
    }
}
