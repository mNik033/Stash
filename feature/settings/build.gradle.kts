plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stash.feature.settings"

    testOptions {
        unitTests {
            // Return Kotlin defaults from stubbed Android SDK methods so
            // android.util.Log calls in production code don't throw in JVM tests.
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:ui"))
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:data"))
            implementation(project(":core:auth"))
            implementation(project(":core:media"))
            implementation(project(":data:download"))
            implementation(project(":data:lyrics"))
            implementation(project(":data:ytmusic"))

            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.dagger)
        }

        androidMain.dependencies {
            implementation(project(":data:cloud"))
            implementation(libs.hilt.android)
            implementation(libs.hilt.navigation.compose)
            // For the "Fix wrong-version downloads" backfill trigger in Settings,
            // which enqueues YtLibraryBackfillWorker via WorkManager.
            implementation(libs.work.runtime.ktx)
            // For LoudnessFirstRunStore — shares the app-wide DataStore<Preferences>
            // with LoudnessStore / LoudnessProgressStore in :core:data and :core:media.
            implementation(libs.datastore.preferences)
            // Link Stash on the web: the QR scanner. CameraX + ZXing core, no Google Play services (spec 2026-10-10 link-sync §1).
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.zxing.core)
        }

        desktopMain.dependencies {
            implementation(libs.dagger)
        }
    }
}

dependencies {
    kspAndroid(libs.hilt.android.compiler)

    testImplementation(project(":core:ui"))
    testImplementation(project(":core:model"))
    testImplementation(project(":core:common"))
    testImplementation(project(":core:data"))
    testImplementation(project(":core:auth"))
    testImplementation(project(":core:media"))
    testImplementation(project(":data:download"))
    testImplementation(project(":data:lyrics"))
    testImplementation(project(":data:ytmusic"))
    testImplementation(project(":data:cloud"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
}
