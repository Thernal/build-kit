package io.thernal.buildkit.buildlogic.compose

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.android.build.api.dsl.LibraryExtension
import io.thernal.buildkit.buildlogic.ANDROID_APPLICATION_PLUGIN
import io.thernal.buildkit.buildlogic.ANDROID_LIBRARY_PLUGIN
import io.thernal.buildkit.buildlogic.COMPOSE_COMPILER_PLUGIN
import io.thernal.buildkit.buildlogic.isAndroidModule
import io.thernal.buildkit.buildlogic.libs
import io.thernal.buildkit.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Compose for a UI module, with the artifacts every one needs (runtime, foundation, ui) as
 * `implementation`: a module that exposes a `@Composable` or a `Modifier` still compiles, and each
 * consumer that calls it declares Compose through this same convention. Every Compose module also
 * gets the previews: the `@Preview` annotations, and the tooling that renders them in the IDE on the
 * module's own Android runtime classpath (a debug-only dependency on Android), so nothing ships it.
 *
 * The platform is the module's ([isAndroidModule]): Compose Multiplatform on top of the `kmp.library`
 * convention, or Jetpack Compose on an Android library, its artifacts from the AndroidX Compose BOM.
 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (isAndroidModule()) applyJetpackCompose() else applyComposeMultiplatform()
    }
}

private fun Project.applyComposeMultiplatform() {
    pluginManager.apply("io.thernal.buildkit.kmp.library")
    pluginManager.apply("org.jetbrains.compose")
    pluginManager.apply(COMPOSE_COMPILER_PLUGIN)

    val catalog = libs
    extensions.configure<KotlinMultiplatformExtension> {
        // The Android-KMP library plugin packages no Android resources by default, and Compose
        // Resources (strings, images, files under composeResources/) ship as Android assets through
        // that pipeline: without this the APK carries none, and `Res` throws
        // MissingResourceException at runtime on Android only.
        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
            androidResources.enable = true
        }
        sourceSets.named("commonMain") {
            dependencies {
                implementation(catalog.library("compose-runtime"))
                implementation(catalog.library("compose-foundation"))
                implementation(catalog.library("compose-ui"))
                implementation(catalog.library("compose-ui-tooling-preview"))
            }
        }
    }
    val tooling = catalog.library("compose-ui-tooling")
    configurations.matching { it.name == ANDROID_RUNTIME_CLASSPATH }.configureEach {
        dependencies.addLater(tooling)
    }
    configureComposeCompiler()
}

/**
 * An Android application gets Compose from the `android.application` convention itself; here it only
 * takes the previews.
 */
private fun Project.applyJetpackCompose() {
    if (pluginManager.hasPlugin(ANDROID_APPLICATION_PLUGIN)) {
        addJetpackPreviews()
        return
    }
    pluginManager.apply("io.thernal.buildkit.android.library")
    pluginManager.apply(COMPOSE_COMPILER_PLUGIN)

    val catalog = libs
    pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
        extensions.configure<LibraryExtension> { buildFeatures.compose = true }
    }
    dependencies {
        add("implementation", platform(catalog.library("androidx-compose-bom")))
        add("implementation", catalog.library("androidx-compose-runtime"))
        add("implementation", catalog.library("androidx-compose-foundation"))
        add("implementation", catalog.library("androidx-compose-ui"))
    }
    addJetpackPreviews()
    configureComposeCompiler()
}

private fun Project.addJetpackPreviews() {
    val catalog = libs
    dependencies {
        add("implementation", platform(catalog.library("androidx-compose-bom")))
        add("implementation", catalog.library("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", catalog.library("androidx-compose-ui-tooling"))
    }
}

/** The Android-KMP library target's runtime classpath: the IDE renders a module's previews from it. */
private const val ANDROID_RUNTIME_CLASSPATH = "androidRuntimeClasspath"
