package com.fb2.obd

import com.fb2.obd.data.LastElmStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LastElmStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun saveConnected_thenUserDisconnect_keepsAddressButBlocksReconnect() {
        val store = LastElmStore(File(tmp.root, "last_elm.json"))
        store.saveConnected("AA:BB:CC:DD:EE:FF", "OBDII")
        val live = store.load()
        assertEquals("AA:BB:CC:DD:EE:FF", live.address)
        assertEquals("OBDII", live.name)
        assertFalse(live.userDisconnected)

        store.markUserDisconnected()
        val after = store.load()
        assertEquals("AA:BB:CC:DD:EE:FF", after.address)
        assertTrue(after.userDisconnected)
    }

    @Test
    fun missingFile_isUserDisconnected() {
        val store = LastElmStore(File(tmp.root, "missing.json"))
        val st = store.load()
        assertNull(st.address)
        assertTrue(st.userDisconnected)
    }
}
