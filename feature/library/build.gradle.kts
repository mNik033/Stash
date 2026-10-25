plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stash.feature.library"

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
            implementation(project(":data:download"))
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
            implementation(libs.activity.compose)
            implementation(libs.datastore.preferences)
            implementation(libs.work.runtime.ktx)
            implementation(libs.core.ktx)
        }

        desktopMain.dependencies {
            implementation(libs.dagger)
            implementation(libs.datastore.preferences)
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
    testImplementation(project(":data:ytmusic"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("com.google.truth:truth:1.4.4")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.mockk:mockk:1.13.8")
}
