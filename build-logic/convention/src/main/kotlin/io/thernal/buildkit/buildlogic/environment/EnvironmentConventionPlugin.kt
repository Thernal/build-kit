package io.thernal.buildkit.buildlogic.environment

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import io.thernal.buildkit.buildlogic.ANDROID_APPLICATION_PLUGIN
import io.thernal.buildkit.buildlogic.ANDROID_LIBRARY_PLUGIN
import io.thernal.buildkit.buildlogic.defaultNamespace
import io.thernal.buildkit.buildlogic.flavor.activeFlavor
import io.thernal.buildkit.buildlogic.flavor.appFlavors
import io.thernal.buildkit.buildlogic.isAndroidModule
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** What the generated object is called and where it lives. */
abstract class EnvironmentExtension {
    /** Defaults to the module's namespace (`app.namespace` + module path). */
    abstract val packageName: Property<String>

    /** Defaults to `Environment`. */
    abstract val objectName: Property<String>
}

/**
 * Generates a Kotlin `object` from the repository-root `.env.<flavor>` of the invocation's
 * active flavor: `FLAVOR`, `IS_PRODUCTION`, and one `const val` per `.env` key. Shared code on every
 * platform reads the environment from it — there is no `BuildConfig` in a multiplatform module, and
 * iOS has none at all.
 *
 * The object lands in `commonMain` of a multiplatform module, or in the sources of every variant of
 * an Android library or application — the same object either way, so an Android-only application
 * reads its environment exactly as a multiplatform one does.
 *
 * Apply it to the one module that owns configuration; everything else asks that module.
 */
class EnvironmentConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val android = isAndroidModule()
        applyModuleConvention(android)
        val extension = extensions.create<EnvironmentExtension>("environment").apply {
            packageName.convention(defaultNamespace())
            objectName.convention("Environment")
        }
        val generate = registerGenerateTask(extension, sourceSet = if (android) "main" else "commonMain")
        if (android) addToAndroidVariants(generate) else addToCommonMain(generate)
    }
}

private fun Project.applyModuleConvention(android: Boolean) {
    when {
        !android -> pluginManager.apply("io.thernal.buildkit.kmp.library")
        !pluginManager.hasPlugin(ANDROID_APPLICATION_PLUGIN) -> pluginManager.apply("io.thernal.buildkit.android.library")
    }
}

private fun Project.registerGenerateTask(
    extension: EnvironmentExtension,
    sourceSet: String,
): TaskProvider<GenerateEnvironmentTask> {
    val flavors = appFlavors()
    val active = activeFlavor()
    return tasks.register<GenerateEnvironmentTask>("generateEnvironment") {
        group = "build"
        description = "Generates the ${flavors.all.joinToString("/")} environment object for the active flavor."
        flavor.set(active)
        production.set(active == flavors.production)
        fields.set(environmentFields(flavors).getValue(active))
        packageName.set(extension.packageName)
        objectName.set(extension.objectName)
        outputDirectory.set(layout.buildDirectory.dir("generated/environment/$sourceSet/kotlin"))
    }
}

private fun Project.addToAndroidVariants(generate: TaskProvider<GenerateEnvironmentTask>) {
    pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
        extensions.configure<LibraryAndroidComponentsExtension> { onVariants { it.addSources(generate) } }
    }
    pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN) {
        extensions.configure<ApplicationAndroidComponentsExtension> { onVariants { it.addSources(generate) } }
    }
}

private fun Variant.addSources(generate: TaskProvider<GenerateEnvironmentTask>) {
    sources.kotlin?.addGeneratedSourceDirectory(generate, GenerateEnvironmentTask::outputDirectory)
}

private fun Project.addToCommonMain(generate: TaskProvider<GenerateEnvironmentTask>) {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.named("commonMain") {
            kotlin.srcDir(generate.flatMap(GenerateEnvironmentTask::outputDirectory))
        }
    }
}
