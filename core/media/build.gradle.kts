plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stash.core.media"

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
            implementation(project(":core:auth"))
            implementation(project(":data:download"))
            implementation(project(":data:ytmusic"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.dagger)
        }
        androidMain.dependencies {
            implementation(project(":data:cloud"))

            // Media3
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.exoplayer.dash)
            implementation(libs.media3.session)
            implementation(libs.media3.datasource)
            implementation(libs.media3.datasource.okhttp)
            implementation(libs.media3.database)

            // Hilt
            implementation(libs.hilt.android)

            // Coroutines
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.guava)

            // DataStore
            implementation(libs.datastore.preferences)

            // DocumentFile
            implementation("androidx.documentfile:documentfile:1.0.1")
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
    kspDesktop(libs.dagger.compiler)

    testImplementation(project(":core:model"))
    testImplementation(project(":core:common"))
    testImplementation(project(":core:data"))
    testImplementation(project(":core:auth"))
    testImplementation(project(":data:download"))
    testImplementation(project(":data:cloud"))
    testImplementation(project(":data:ytmusic"))

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockk)
}
