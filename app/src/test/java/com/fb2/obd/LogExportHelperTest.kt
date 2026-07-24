package com.fb2.obd

import com.fb2.obd.data.LogExportHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogExportHelperTest {

    @Test
    fun bluetoothAndNearbyAreNotUsefulShareTargets() {
        assertFalse(LogExportHelper.isUsefulSharePackage("com.android.bluetooth"))
        assertFalse(LogExportHelper.isUsefulSharePackage("com.android.bluetooth.opp"))
        assertFalse(LogExportHelper.isUsefulSharePackage("com.google.android.gms.nearby.sharing"))
        assertFalse(LogExportHelper.isUsefulSharePackage("com.samsung.android.app.sharelive"))
        assertFalse(LogExportHelper.isUsefulSharePackage("com.android.intentresolver"))
    }

    @Test
    fun realAppsRemainUsefulShareTargets() {
        assertTrue(LogExportHelper.isUsefulSharePackage("com.google.android.apps.docs"))
        assertTrue(LogExportHelper.isUsefulSharePackage("com.whatsapp"))
        assertTrue(LogExportHelper.isUsefulSharePackage("com.google.android.gm"))
        assertTrue(LogExportHelper.isUsefulSharePackage("com.android.documentsui"))
    }
}
