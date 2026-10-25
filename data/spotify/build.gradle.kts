plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stash.data.spotify"

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
            implementation(libs.mockk)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.okhttp)
        }
    }
}

dependencies {
    kspAndroid(libs.hilt.android.compiler)
    kspDesktop(libs.dagger.compiler)
}
