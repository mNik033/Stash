import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val compose = extensions.getByType<ComposeExtension>()
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    implementation(compose.dependencies.runtime)
                    implementation(compose.dependencies.foundation)
                    implementation(compose.dependencies.material3)
                    implementation(compose.dependencies.ui)
                    implementation(compose.dependencies.components.resources)
                    implementation(compose.dependencies.materialIconsExtended)
                }
            }
        }
    }
}
