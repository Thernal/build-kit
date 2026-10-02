package io.thernal.buildkit.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * A plain Android library — the module an Android-only application (`app.platforms=android`) builds
 * everything from, where a multiplatform one would use [KmpLibraryConventionPlugin]. Namespace from
 * `app.namespace` and the module path, SDK levels and JVM from the catalog, test dependencies, Detekt.
 *
 * Android's resource, BuildConfig, resValue and shader pipelines are off: a library pays only for
 * what it uses, and a module that ships resources turns `androidResources` back on in its own
 * `android {}` block. Like a multiplatform module it has no product flavors — shared code is built
 * for one flavor per invocation ([activeFlavor]), and flavor-scoped dependencies go through
 * `nonProductionImplementation` and friends.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // No `org.jetbrains.kotlin.android`: since AGP 9 the Android plugin carries Kotlin support
        // itself, and applying the standalone plugin on top of it is an error.
        pluginManager.apply(ANDROID_LIBRARY_PLUGIN)
        QualityConventionPlugin().apply(target)

        val catalog = libs
        val jvm = catalog.version("jvm").toInt()

        extensions.configure<LibraryExtension> {
            namespace = defaultNamespace()
            compileSdk = catalog.version("android-compile-sdk").toInt()

            defaultConfig {
                minSdk = catalog.version("android-min-sdk").toInt()
            }

            buildFeatures {
                androidResources = false
                buildConfig = false
                resValues = false
                shaders = false
            }

            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(jvm)
                targetCompatibility = JavaVersion.toVersion(jvm)
            }
        }

        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(jvm))
        }

        dependencies {
            // kotlin-test's JUnit binding: Kotlin picks it on its own for a JVM module, not for one
            // compiled by the Android plugin.
            add("testImplementation", catalog.library("kotlin-test-junit"))
            add("testImplementation", catalog.library("kotlinx-coroutines-test"))
        }
    }
}
