package io.thernal.buildkit.buildlogic.libraries

import io.thernal.buildkit.buildlogic.KOTLIN_JVM_PLUGIN
import io.thernal.buildkit.buildlogic.intVersion
import io.thernal.buildkit.buildlogic.libs
import io.thernal.buildkit.buildlogic.library
import io.thernal.buildkit.buildlogic.quality.QualityConventionPlugin
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
        pluginManager.apply(KOTLIN_JVM_PLUGIN)
        QualityConventionPlugin().apply(target)

        val catalog = libs
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(catalog.intVersion("jvm"))
        }
        dependencies {
            add("testImplementation", catalog.library("kotlin-test"))
        }
    }
}
