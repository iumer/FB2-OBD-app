import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("app.cash.paparazzi")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun propOrEnv(key: String): String? =
    localProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.fb2.obd"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fb2.obd"
        minSdk = 26
        targetSdk = 34
        // Visible line: … → 0.1.41 Settings category regroup → 0.1.42 Security audit hardening.
        // versionCode is an Android install counter only (must rise so this
        // APK can replace prior builds). Not shown in UI.
        versionCode = 63
        versionName = "0.1.42"
    }

    // Many Android head-unit installers hang on v2-only APKs — keep v1 JAR signing.
    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
        }
        val storePath = propOrEnv("RELEASE_STORE_FILE")
        val storePass = propOrEnv("RELEASE_STORE_PASSWORD")
        val keyAlias = propOrEnv("RELEASE_KEY_ALIAS")
        val keyPass = propOrEnv("RELEASE_KEY_PASSWORD")
        if (!storePath.isNullOrBlank() &&
            !storePass.isNullOrBlank() &&
            !keyAlias.isNullOrBlank() &&
            !keyPass.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(storePath)
                storePassword = storePass
                this.keyAlias = keyAlias
                keyPassword = keyPass
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
        release {
            val releaseSigning = signingConfigs.findByName("release")
            // Prefer release keystore from local.properties / env; never commit it.
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Android Auto / Car App Library (template UI on the head unit)
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Fail the build if a secrets asset is present or APKs contain leaked tokens.
tasks.register("checkNoPackagedSecrets") {
    group = "verification"
    description = "Fail if assets/fb2-secrets.properties exists or APKs contain sk-/github_pat_/ghp_"
    doLast {
        val secretsAsset = file("src/main/assets/fb2-secrets.properties")
        if (secretsAsset.exists()) {
            throw GradleException(
                "Refusing to build: ${secretsAsset.path} must not be packaged. " +
                    "Keys belong only in Settings on the device.",
            )
        }
        val pattern = Regex("sk-[A-Za-z0-9_\\-]{8,}|github_pat_[A-Za-z0-9_]+|ghp_[A-Za-z0-9]{20,}")
        val apkRoots = listOf(
            file("build/outputs/apk"),
            rootProject.file("dist"),
        )
        val apks = apkRoots.flatMap { root ->
            if (root.exists()) root.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
            else emptyList()
        }
        apks.forEach { apk ->
            ZipFile(apk).use { zip: ZipFile ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    // Skip large binary-ish entries; secrets leak in strings/XML/dex text.
                    if (entry.size > 8_000_000L) continue
                    val bytes = zip.getInputStream(entry).readBytes()
                    // Prefer latin1 so binary regions still yield searchable ASCII tokens.
                    val text = bytes.toString(Charsets.ISO_8859_1)
                    val hit = pattern.find(text)
                    if (hit != null) {
                        throw GradleException(
                            "Secret-like string ${hit.value.take(12)}… found in ${apk.name} (${entry.name}). " +
                                "Remove packaged credentials before shipping.",
                        )
                    }
                }
            }
        }
        logger.lifecycle("checkNoPackagedSecrets: OK (${apks.size} APK(s) scanned, no secrets asset)")
    }
}

tasks.matching { it.name.startsWith("assemble") }.configureEach {
    finalizedBy("checkNoPackagedSecrets")
}
