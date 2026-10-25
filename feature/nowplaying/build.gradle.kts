plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stash.feature.nowplaying"

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
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
            implementation(project(":core:media"))
            implementation(project(":core:auth"))
            implementation(project(":data:lyrics"))
            implementation(project(":data:ytmusic"))

            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.coil.compose)
            implementation(libs.dagger)
        }

        androidMain.dependencies {
            implementation(libs.hilt.android)
            implementation(libs.hilt.navigation.compose)
            implementation(libs.work.runtime.ktx)
            implementation(libs.lifecycle.process)
            implementation(libs.core.ktx)
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
    testImplementation(project(":data:lyrics"))
    testImplementation(project(":data:ytmusic"))

    testImplementation(platform(libs.compose.bom))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}

