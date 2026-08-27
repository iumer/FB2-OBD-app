package com.fb2.obd

import com.fb2.obd.data.AppCredentialDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppCredentialDefaultsTest {

    @Test
    fun fillBlank_onlyWhenSettingsEmpty() {
        val (oa, gh) = AppCredentialDefaults.fillBlankOnly(
            packagedOpenAi = "sk-packaged",
            packagedGithub = "ghp_packaged",
            currentOpenAi = "",
            currentGithub = "",
        )
        assertEquals("sk-packaged", oa)
        assertEquals("ghp_packaged", gh)
    }

    @Test
    fun fillBlank_neverOverwritesUserPaste() {
        val (oa, gh) = AppCredentialDefaults.fillBlankOnly(
            packagedOpenAi = "sk-packaged",
            packagedGithub = "ghp_packaged",
            currentOpenAi = "sk-user-pasted",
            currentGithub = "ghp_user_pasted",
        )
        assertNull(oa)
        assertNull(gh)
    }

    @Test
    fun fillBlank_emptyPackagedLeavesPrefsAlone() {
        val (oa, gh) = AppCredentialDefaults.fillBlankOnly(
            packagedOpenAi = "",
            packagedGithub = "",
            currentOpenAi = "",
            currentGithub = "ghp_user",
        )
        assertNull(oa)
        assertNull(gh)
    }
}
