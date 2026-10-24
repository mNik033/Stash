plugins {
    id("stash.kmp.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.stash.core.network"
    buildFeatures { buildConfig = true }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(libs.okhttp)
            implementation(libs.okhttp.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
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
            implementation(kotlin("test"))
            implementation("junit:junit:4.13.2")
            implementation(libs.okhttp)
        }
    }
}

dependencies {
    kspAndroid(libs.hilt.android.compiler)
    kspDesktop(libs.dagger.compiler)
}
