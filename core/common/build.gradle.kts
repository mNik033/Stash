plugins {
    id("stash.kmp.library")
}

android {
    namespace = "com.stash.core.common"
    sourceSets["test"].resources.srcDir("src/commonTest/resources")
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
            implementation("junit:junit:4.13.2")
        }
    }
}
