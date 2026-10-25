plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stash.data.ytmusic"

    sourceSets {
        getByName("test") {
            resources.srcDir("src/commonTest/resources")
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:auth"))

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.okhttp)
            implementation(libs.dagger)
        }
        androidMain.dependencies {
            implementation(libs.hilt.android)
            implementation(libs.kotlinx.coroutines.android)
        }
        desktopMain.dependencies {
            implementation(libs.dagger)
        }
        commonTest.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:auth"))
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
            implementation(libs.truth)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            implementation("app.cash.turbine:turbine:1.1.0")
            implementation("org.mockito:mockito-core:5.11.0")
            implementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
            implementation("com.squareup.okhttp3:mockwebserver:4.12.0")
        }
    }
}

dependencies {
    kspAndroid(libs.hilt.android.compiler)
    kspDesktop(libs.dagger.compiler)
}
