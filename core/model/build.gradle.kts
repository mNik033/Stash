plugins {
    id("stash.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stash.core.model"
    sourceSets["test"].resources.srcDir("src/commonTest/resources")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.truth)
            implementation("junit:junit:4.13.2")
        }
    }
}
