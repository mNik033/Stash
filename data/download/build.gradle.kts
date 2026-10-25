import java.util.Properties

plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// ── Build-time config reader ───────────────────────────────────────────────
val qbdlxProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun qbdlxProp(key: String, env: String) =
    qbdlxProps.getProperty(key) ?: System.getenv(env).orEmpty()

// ── Stash Lossless Relay runtime config ────────────────────────────────────
val losslessConfigUrl = qbdlxProp("lossless.configUrl", "LOSSLESS_CONFIG_URL")
val losslessConfigPubKey = qbdlxProp("lossless.configPubKey", "LOSSLESS_CONFIG_PUBKEY")

android {
    namespace = "com.stash.data.download"

    defaultConfig {
        buildConfigField("String", "LOSSLESS_CONFIG_URL", "\"$losslessConfigUrl\"")
        buildConfigField("String", "LOSSLESS_CONFIG_PUBKEY", "\"$losslessConfigPubKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:data"))
            implementation(project(":core:auth"))
            implementation(project(":core:network"))
            implementation(project(":data:ytmusic"))
            implementation(project(":data:spotify"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            implementation(libs.dagger)
        }
        androidMain.dependencies {
            implementation(project(":data:cloud"))
            implementation(libs.hilt.android)
            implementation(libs.youtubedl.android)
            implementation(libs.youtubedl.ffmpeg)
            implementation(libs.youtubedl.aria2c)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.datastore.preferences)
            implementation(libs.work.runtime.ktx)
            implementation(libs.hilt.work)
            implementation("androidx.documentfile:documentfile:1.0.1")
            implementation(libs.media3.datasource)
            implementation(libs.media3.database)
        }
        desktopMain.dependencies {
            implementation(libs.dagger)
        }
        commonTest.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
            implementation(libs.truth)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

dependencies {
    kspAndroid(libs.hilt.android.compiler)
    kspAndroid(libs.hilt.work.compiler)
    kspDesktop(libs.dagger.compiler)

    testImplementation(project(":core:model"))
    testImplementation(project(":core:common"))
    testImplementation(project(":core:data"))
    testImplementation(project(":core:auth"))
    testImplementation(project(":core:network"))
    testImplementation(project(":data:ytmusic"))
    testImplementation(project(":data:spotify"))
    testImplementation(project(":data:cloud"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(libs.mockk)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
}
