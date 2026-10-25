plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stash.feature.sync"

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

            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.dagger)
        }

        androidMain.dependencies {
            implementation(libs.hilt.android)
            implementation(libs.hilt.navigation.compose)
            // FailedMatchesViewModel classifies PreviewPlayer's asynchronous
            // PlaybackException events before its one-shot yt-dlp retry.
            implementation(libs.media3.exoplayer)
            // For the "Fix wrong-version downloads" trigger (enqueues
            // YtLibraryBackfillWorker via WorkManager).
            implementation(libs.work.runtime.ktx)
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
    testImplementation(project(":core:media"))
    testImplementation(project(":core:auth"))
    testImplementation(project(":data:download"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation(libs.mockk)
}
