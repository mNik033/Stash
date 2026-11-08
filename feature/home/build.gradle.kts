plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stash.feature.home"

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
            implementation(project(":data:download"))
            implementation(project(":data:lyrics"))
            implementation(project(":data:ytmusic"))

            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            implementation(libs.lifecycle.runtime.compose)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.savedstate)
            implementation(libs.dagger)
        }

        androidMain.dependencies {
            implementation(libs.hilt.android)
            implementation(libs.hilt.navigation.compose)
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
    testImplementation(project(":data:download"))
    testImplementation(project(":data:lyrics"))
    testImplementation(project(":data:ytmusic"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("androidx.compose.material3:material3")
}
