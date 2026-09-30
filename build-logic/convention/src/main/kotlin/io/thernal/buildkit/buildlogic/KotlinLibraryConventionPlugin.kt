package io.thernal.buildkit.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A plain Kotlin/JVM module — build tooling, code generators, anything no platform ever runs. Shared
 * application code belongs in a multiplatform module ([KmpLibraryConventionPlugin]) instead.
 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        QualityConventionPlugin().apply(target)

        val catalog = libs

        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(catalog.version("jvm").toInt())
        }

        dependencies {
            add("testImplementation", catalog.library("kotlin-test"))
        }
    }
}
