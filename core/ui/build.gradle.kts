plugins {
    id("stash.kmp.library")
    id("stash.kmp.compose")
}

android {
    namespace = "com.stash.core.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:common"))
            implementation(libs.coil.compose)
            implementation(libs.coil.network.okhttp)
            implementation(libs.haze)
            implementation(libs.lifecycle.runtime.compose)
            implementation("androidx.collection:collection:1.4.5")
        }

        androidMain.dependencies {
            implementation(libs.core.ktx)
            implementation(libs.activity.compose)
        }

        desktopMain.dependencies {
        }
    }
}

dependencies {
    testImplementation(platform(libs.compose.bom))
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.truth)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation(libs.mockk)
    testImplementation(libs.compose.runtime)
    testImplementation("androidx.compose.runtime:runtime-saveable")
}
