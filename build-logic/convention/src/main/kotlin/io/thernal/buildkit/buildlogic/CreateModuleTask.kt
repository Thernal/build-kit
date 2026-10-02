package io.thernal.buildkit.buildlogic

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault

/** System or Gradle property carrying the `create` task's positional arguments (see settings.gradle.kts). */
const val CREATE_ARGUMENTS_PROPERTY: String = "app.create.arguments"

/**
 * Scaffolds `api`, `impl` and `wiring` modules for a capability.
 *
 * ```
 * ./gradlew create profile api impl wiring     # features/profile/{api,impl,wiring}
 * ./gradlew create core/network api impl       # core/network/{api,impl}
 * ```
 */
@DisableCachingByDefault(because = "Scaffolding writes new source directories on demand")
abstract class CreateModuleTask : DefaultTask() {
    @get:Input
    @get:Optional
    @get:Option(option = "arguments", description = "Module path followed by module kinds, for example \"profile api impl\".")
    abstract val arguments: Property<String>

    @get:Input abstract val namespacePrefix: Property<String>
    @get:Input abstract val modulesRoot: Property<String>
    @get:Input abstract val modulesAreas: Property<String>

    /** `true` in an Android-only application (`app.platforms=android`): Android libraries, `src/main`. */
    @get:Input abstract val androidOnly: Property<Boolean>
    @get:Internal abstract val repositoryDirectory: DirectoryProperty

    @TaskAction
    fun createModules() {
        val layout = ModuleLayout.parse(modulesRoot.get(), modulesAreas.get())
        val tokens = arguments.orNull.orEmpty().split(' ', '\t', '\n', ',').map(String::trim).filter(String::isNotEmpty)
        if (tokens.size < 2) throw GradleException(usage(layout))

        val segments = runCatching { ModuleScaffold.parseSpec(tokens.first(), layout) }
            .getOrElse { throw GradleException("${it.message}\n\n${usage(layout)}") }
        val kinds = runCatching { tokens.drop(1).map(ModuleKind::from).distinct() }
            .getOrElse { throw GradleException("${it.message}\n\n${usage(layout)}") }

        val capabilityDirectory = segments.fold(repositoryDirectory.get().asFile, File::resolve)
        val existing = ModuleKind.entries.filter { capabilityDirectory.resolve(it.directoryName).resolve(BUILD_FILE).isFile }
        val siblings = (existing + kinds).toSet()
        val prefix = namespacePrefix.get()
        val androidOnly = androidOnly.get()
        val sourceSets = if (androidOnly) ANDROID_SOURCE_SETS else SOURCE_SETS
        val mainSourceSet = ModuleScaffold.mainSourceSet(androidOnly)

        val created = kinds.mapNotNull { kind ->
            val moduleSegments = segments + kind.directoryName
            val moduleDirectory = capabilityDirectory.resolve(kind.directoryName)
            if (moduleDirectory.resolve(BUILD_FILE).isFile) {
                logger.lifecycle("Skipped ${ModuleScaffold.projectPath(moduleSegments)}: it already exists.")
                return@mapNotNull null
            }
            moduleDirectory.mkdirs()
            moduleDirectory.resolve(BUILD_FILE).writeText(ModuleScaffold.buildFile(moduleSegments, kind, siblings, androidOnly))
            sourceSets.forEach { sourceSet ->
                moduleDirectory.resolve(ModuleScaffold.sourceDirectory(prefix, moduleSegments, sourceSet))
                    .apply { mkdirs() }
                    .resolve(".gitkeep").writeText("")
            }
            if (kind == ModuleKind.WIRING) {
                moduleDirectory.resolve(ModuleScaffold.sourceDirectory(prefix, moduleSegments, mainSourceSet))
                    .resolve("${ModuleScaffold.bindingContainerName(segments)}.kt")
                    .writeText(ModuleScaffold.bindingContainer(prefix, moduleSegments))
            }
            ModuleScaffold.projectPath(moduleSegments)
        }

        if (created.isEmpty()) {
            logger.lifecycle("Nothing to create; every requested module already exists.")
            return
        }
        logger.lifecycle("Created ${created.size} module(s):")
        created.forEach { logger.lifecycle("  $it") }
        logger.lifecycle(
            """
            |
            |Modules are discovered on the next configuration; check with ./gradlew projects.
            |They are plain ${if (androidOnly) "Android libraries" else "multiplatform modules"}: add Compose (libs.plugins.buildkit.compose) or anything
            |else in the build file when the module needs it, and declare dependencies with
            |implementation(...) — api(...) is rejected.
            """.trimMargin(),
        )
    }

    private fun usage(layout: ModuleLayout): String = """
        |Usage: ./gradlew create <module-path> <kind> [<kind>...]
        |
        |  <module-path>  A bare name goes under '${layout.defaultArea}/'; a path starts with an area
        |                 (${layout.areas.joinToString()}).
        |  <kind>         One or more of: ${ModuleKind.entries.joinToString { it.directoryName }}
        |
        |Examples:
        |  ./gradlew create profile api impl wiring   -> ${layout.defaultArea}/profile/{api,impl,wiring}
        |  ./gradlew create core/network api impl     -> core/network/{api,impl}
    """.trimMargin()

    private companion object {
        const val BUILD_FILE = "build.gradle.kts"
        val SOURCE_SETS = listOf("commonMain", "commonTest")
        val ANDROID_SOURCE_SETS = listOf("main", "test")
    }
}
