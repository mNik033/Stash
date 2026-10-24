plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

kotlin {
    jvm("desktop") {
        mainRun {
            mainClass.set("com.stash.desktop.MainKt")
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.jna)
                implementation(libs.jna.platform)
                implementation(libs.dagger)
                implementation(libs.room.runtime)
                implementation(libs.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspDesktop", libs.dagger.compiler)
    add("kspDesktop", libs.room.compiler)
}

compose.desktop {
    application {
        mainClass = "com.stash.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "Stash"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "com.stash.desktop"
            }
        }
    }
}
