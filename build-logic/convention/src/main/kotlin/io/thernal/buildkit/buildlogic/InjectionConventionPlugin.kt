package io.thernal.buildkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Enables the injection framework's code generation without adding architecture or project
 * dependencies. Metro is the current implementation; modules only ever name the capability.
 * Works on a multiplatform module, a plain Kotlin module and an Android application alike.
 */
class InjectionConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.zacsweers.metro")

        val runtime = libs.library("metro-runtime")

        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") {
                    dependencies { implementation(runtime) }
                }
            }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            dependencies { add("implementation", runtime) }
        }
        pluginManager.withPlugin("com.android.application") {
            dependencies { add("implementation", runtime) }
        }
    }
}
