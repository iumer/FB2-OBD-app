package com.fb2.obd

import com.fb2.obd.data.DashExtraPidStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DashExtraPidStoreTest {

    @Test
    fun loadSaveRoundTrip() {
        val file = File.createTempFile("dash_extra", ".json")
        file.deleteOnExit()
        val store = DashExtraPidStore(file)
        store.save(listOf("0114", "0115", "0114"))
        assertEquals(listOf("0114", "0115"), store.load())
    }

    @Test
    fun loadMissingReturnsEmpty() {
        val file = File.createTempFile("dash_extra_missing", ".json")
        file.delete()
        assertEquals(emptyList<String>(), DashExtraPidStore(file).load())
    }
}
