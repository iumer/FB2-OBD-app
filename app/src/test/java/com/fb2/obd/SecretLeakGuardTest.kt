package com.fb2.obd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Build-time / unit guard: never ship packaged credentials or APKs that
 * contain OpenAI / GitHub token prefixes.
 */
class SecretLeakGuardTest {

    private val repoRoot: File = findRepoRoot()

    private fun findRepoRoot(): File {
        var dir = File(checkNotNull(System.getProperty("user.dir")))
        while (true) {
            if (File(dir, "gradlew").exists()) return dir
            dir = dir.parentFile ?: error("repo root not found")
        }
    }

    @Test
    fun assetsMustNotContainFb2SecretsProperties() {
        val asset = File(repoRoot, "app/src/main/assets/fb2-secrets.properties")
        assertFalse(
            "assets/fb2-secrets.properties must not exist — keys only from Settings",
            asset.exists(),
        )
        val example = File(repoRoot, "app/src/main/assets/fb2-secrets.properties.example")
        assertFalse(
            "fb2-secrets.properties.example must be removed (no packaged credential mechanism)",
            example.exists(),
        )
    }

    @Test
    fun appCredentialDefaultsSourceMustBeGone() {
        val src = File(repoRoot, "app/src/main/java/com/fb2/obd/data/AppCredentialDefaults.kt")
        assertFalse("AppCredentialDefaults.kt must be deleted", src.exists())
    }

    @Test
    fun builtApksMustNotContainSecretPrefixes() {
        val pattern = Regex("sk-[A-Za-z0-9_\\-]{8,}|github_pat_[A-Za-z0-9_]+|ghp_[A-Za-z0-9]{20,}")
        val roots = listOf(
            File(repoRoot, "app/build/outputs/apk"),
            File(repoRoot, "dist"),
        )
        val apks = roots.flatMap { root ->
            if (!root.exists()) emptyList()
            else root.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        }
        // When no APK has been built yet, this test is a no-op; checkNoPackagedSecrets
        // Gradle task covers post-assemble. Presence of APKs must still be clean.
        for (apk in apks) {
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.size > 8_000_000L) continue
                    val text = zip.getInputStream(entry).readBytes().toString(Charsets.ISO_8859_1)
                    val hit = pattern.find(text)
                    assertTrue(
                        "Secret-like string in ${apk.name}/${entry.name}: ${hit?.value?.take(12)}…",
                        hit == null,
                    )
                }
            }
        }
    }
}
