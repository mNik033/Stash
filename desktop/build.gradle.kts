plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
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
                implementation(project(":core:model"))
                implementation(project(":core:common"))
                implementation(project(":core:network"))
                implementation(project(":core:auth"))
                implementation(project(":core:data"))
                implementation(project(":core:media"))
                implementation(project(":core:ui"))

                implementation(project(":data:spotify"))
                implementation(project(":data:ytmusic"))
                implementation(project(":data:lyrics"))
                implementation(project(":data:download"))

                implementation(project(":feature:nowplaying"))
                implementation(project(":feature:library"))
                implementation(project(":feature:search"))
                implementation(project(":feature:sync"))
                implementation(project(":feature:settings"))

                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                implementation(libs.coil.compose)
                implementation(libs.coil.network.okhttp)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.viewmodel.savedstate)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.datastore.preferences)
                implementation(libs.okhttp)

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
            description = "Stash — Lossless music player & library manager"
            vendor = "Stash"

            macOS {
                bundleID = "com.stash.desktop"
                dockName = "Stash"
            }
            windows {
                menuGroup = "Stash"
                upgradeUuid = "6b6e4e0c-512d-4560-b6c8-5089fa2d9f7a"
            }
            linux {
                shortcut = true
            }
        }
    }
}
