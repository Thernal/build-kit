package io.thernal.buildkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Set to `true` by whoever wants compiler metrics for the stability report; off otherwise. */
internal const val STABILITY_REPORT_PROPERTY = "composeStabilityReport"

/** Classes the compiler treats as stable although it cannot prove it; optional. */
internal const val STABILITY_CONFIGURATION_FILE = "config/compose/stability.conf"

/**
 * Compose Multiplatform on top of [KmpLibraryConventionPlugin], with the Compose artifacts every
 * UI module needs. They are added as `implementation`: a module that exposes a `@Composable` or a
 * `Modifier` still compiles, and each consumer that calls it declares Compose through this same
 * convention.
 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.thernal.buildkit.kmp.library")
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val catalog = libs

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonMain") {
                dependencies {
                    implementation(catalog.library("compose-runtime"))
                    implementation(catalog.library("compose-foundation"))
                    implementation(catalog.library("compose-ui"))
                }
            }
        }

        configureComposeCompiler()
    }
}

/** Shared by every module that runs the Compose compiler, library or application. */
internal fun Project.configureComposeCompiler() {
    val stabilityConfiguration = rootProject.layout.projectDirectory.file(STABILITY_CONFIGURATION_FILE)
    val reportsEnabled = providers.gradleProperty(STABILITY_REPORT_PROPERTY).orNull == "true"

    extensions.configure<ComposeCompilerGradlePluginExtension> {
        if (stabilityConfiguration.asFile.isFile) {
            stabilityConfigurationFiles.add(stabilityConfiguration)
        }
        if (reportsEnabled) {
            metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
            reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        }
    }
}
