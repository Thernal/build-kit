package io.thernal.buildkit.buildlogic

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
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
 * [activeFlavor]: `FLAVOR`, `IS_PRODUCTION`, and one `const val` per `.env` key. Shared code on every
 * platform reads the environment from it — there is no `BuildConfig` in a multiplatform module, and
 * iOS has none at all.
 *
 * The object lands in `commonMain` of a multiplatform module, or in the sources of every variant of
 * an Android library ([isAndroidModule]) — the same object either way, so an Android-only application
 * reads its environment exactly as a multiplatform one does.
 *
 * Apply it to the one module that owns configuration; everything else asks that module.
 */
class EnvironmentConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val android = isAndroidModule()
        if (android) {
            if (!pluginManager.hasPlugin(ANDROID_APPLICATION_PLUGIN)) pluginManager.apply("io.thernal.buildkit.android.library")
        } else {
            pluginManager.apply("io.thernal.buildkit.kmp.library")
        }

        val extension = extensions.create<EnvironmentExtension>("environment")
        extension.packageName.convention(defaultNamespace())
        extension.objectName.convention("Environment")

        val flavors = appFlavors()
        val flavor = activeFlavor()
        val fields = environmentFields(flavors).getValue(flavor)

        val generate = tasks.register<GenerateEnvironmentTask>("generateEnvironment") {
            group = "build"
            description = "Generates the ${flavors.all.joinToString("/")} environment object for the active flavor."
            this.flavor.set(flavor)
            production.set(flavor == flavors.production)
            this.fields.set(fields)
            packageName.set(extension.packageName)
            objectName.set(extension.objectName)
            val sourceSet = if (android) "main" else "commonMain"
            outputDirectory.set(layout.buildDirectory.dir("generated/environment/$sourceSet/kotlin"))
        }

        if (android) {
            pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
                extensions.configure<LibraryAndroidComponentsExtension> {
                    onVariants { variant ->
                        variant.sources.kotlin?.addGeneratedSourceDirectory(generate, GenerateEnvironmentTask::outputDirectory)
                    }
                }
            }
            pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN) {
                extensions.configure<ApplicationAndroidComponentsExtension> {
                    onVariants { variant ->
                        variant.sources.kotlin?.addGeneratedSourceDirectory(generate, GenerateEnvironmentTask::outputDirectory)
                    }
                }
            }
        } else {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") {
                    kotlin.srcDir(generate.flatMap(GenerateEnvironmentTask::outputDirectory))
                }
            }
        }
    }
}

@CacheableTask
abstract class GenerateEnvironmentTask : DefaultTask() {
    @get:Input abstract val flavor: Property<String>
    @get:Input abstract val production: Property<Boolean>
    @get:Input abstract val fields: MapProperty<String, String>
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val objectName: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = packageName.get()
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        val file = directory.resolve(packageName.replace('.', '/')).resolve("${objectName.get()}.kt")
        file.parentFile.mkdirs()
        file.writeText(environmentSource(packageName, objectName.get(), flavor.get(), production.get(), fields.get()))
    }
}
