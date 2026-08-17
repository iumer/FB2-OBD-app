package com.fb2.obd

import com.fb2.obd.data.DashTileOverrideStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File

class DashTileOverrideStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTrip_persistsRemaps() {
        val file = File(tmp.root, "overrides.json")
        val store = DashTileOverrideStore(file)
        store.save(mapOf("Coolant 1" to "01A4", "MAF" to "010B"))
        val loaded = store.load()
        assertEquals("01A4", loaded["Coolant 1"])
        assertEquals("010B", loaded["MAF"])
        assertTrue(file.exists())
    }

    @Test
    fun missingFile_returnsEmpty() {
        val store = DashTileOverrideStore(File(tmp.root, "missing.json"))
        assertTrue(store.load().isEmpty())
    }
}
