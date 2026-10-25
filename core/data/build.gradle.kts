plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stash.core.data"

    sourceSets {
        getByName("test") {
            assets.srcDir("$projectDir/schemas")
            resources.srcDir("src/commonTest/resources")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:auth"))
            implementation(project(":data:spotify"))
            implementation(project(":data:ytmusic"))

            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okhttp)
            implementation(libs.dagger)
        }
        androidMain.dependencies {
            implementation(libs.room.ktx)
            implementation(libs.datastore.preferences)
            implementation(libs.hilt.android)
            implementation(libs.work.runtime.ktx)
            implementation(libs.hilt.work)
            implementation(libs.kotlinx.coroutines.android)
            implementation("androidx.documentfile:documentfile:1.0.1")
        }
        desktopMain.dependencies {
            implementation(libs.dagger)
            implementation(libs.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
            implementation(libs.truth)
            implementation(libs.sqlite.bundled)
        }
    }
}

dependencies {
    kspAndroid(libs.room.compiler)
    kspAndroid(libs.hilt.android.compiler)
    kspAndroid(libs.hilt.work.compiler)
    kspDesktop(libs.room.compiler)
    kspDesktop(libs.dagger.compiler)

    testImplementation(project(":core:model"))
    testImplementation(project(":core:common"))
    testImplementation(project(":core:auth"))
    testImplementation(project(":data:spotify"))
    testImplementation(project(":data:ytmusic"))
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("androidx.room:room-testing:2.7.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation(libs.work.testing)
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
