package io.thernal.buildkit.buildlogic.injection

import io.thernal.buildkit.buildlogic.ANDROID_APPLICATION_PLUGIN
import io.thernal.buildkit.buildlogic.ANDROID_LIBRARY_PLUGIN
import io.thernal.buildkit.buildlogic.KOTLIN_JVM_PLUGIN
import io.thernal.buildkit.buildlogic.KOTLIN_MULTIPLATFORM_PLUGIN
import io.thernal.buildkit.buildlogic.libs
import io.thernal.buildkit.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Enables the injection framework's code generation without adding architecture or project
 * dependencies. Metro is the current implementation; modules only ever name the capability.
 * Works on a multiplatform module, a plain Kotlin module, an Android library and an Android
 * application alike.
 */
class InjectionConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.zacsweers.metro")

        val runtime = libs.library("metro-runtime")
        pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN) {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") {
                    dependencies { implementation(runtime) }
                }
            }
        }
        listOf(KOTLIN_JVM_PLUGIN, ANDROID_APPLICATION_PLUGIN, ANDROID_LIBRARY_PLUGIN).forEach { plugin ->
            pluginManager.withPlugin(plugin) {
                dependencies { add("implementation", runtime) }
            }
        }
    }
}
