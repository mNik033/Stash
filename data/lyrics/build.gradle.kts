plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stash.data.lyrics"

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
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:data"))
            implementation(project(":core:network"))
            implementation(project(":data:ytmusic"))
            implementation(project(":data:download"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            implementation(libs.okhttp.dnsoverhttps)
            implementation(libs.dagger)
        }
        androidMain.dependencies {
            implementation(libs.hilt.android)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.datastore.preferences)
            implementation(libs.work.runtime.ktx)
            implementation(libs.hilt.work)
            implementation("androidx.documentfile:documentfile:1.0.1")
        }
        desktopMain.dependencies {
            implementation(libs.dagger)
            implementation("org.json:json:20240303")
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
    testImplementation(project(":core:network"))
    testImplementation(project(":data:ytmusic"))
    testImplementation(project(":data:download"))

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation(libs.mockk)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
}
