plugins {
    id("stash.kmp.library")
}

android {
    namespace = "com.stash.core.common"
    sourceSets["test"].resources.srcDir("src/commonTest/resources")
    testOptions {
        // BatterySaverTest drives the failure paths, which log through android.util.Log.
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            implementation("junit:junit:4.13.2")
        }
    }
}
