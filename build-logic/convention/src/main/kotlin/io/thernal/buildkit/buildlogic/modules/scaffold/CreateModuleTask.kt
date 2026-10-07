package io.thernal.buildkit.buildlogic.modules.scaffold

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
        val segments = parseOrUsage(layout) { ModuleScaffold.parseSpec(tokens.first(), layout) }
        val kinds = parseOrUsage(layout) { tokens.drop(1).map(ModuleKind::from).distinct() }

        val capabilityDirectory = segments.fold(repositoryDirectory.get().asFile, File::resolve)
        val existing = ModuleKind.entries.filter { capabilityDirectory.resolve(it.directoryName).resolve(BUILD_FILE).isFile }
        val siblings = (existing + kinds).toSet()
        val created = kinds.mapNotNull { kind -> createModule(capabilityDirectory, segments, kind, siblings, layout.root) }
        reportCreated(created)
    }

    /** Writes one module; returns its project path, or null when it already exists. */
    private fun createModule(
        capabilityDirectory: File,
        segments: List<String>,
        kind: ModuleKind,
        siblings: Set<ModuleKind>,
        rootSegments: List<String>,
    ): String? {
        val moduleSegments = segments + kind.directoryName
        val projectPath = ModuleScaffold.projectPath(moduleSegments)
        val moduleDirectory = capabilityDirectory.resolve(kind.directoryName)
        if (moduleDirectory.resolve(BUILD_FILE).isFile) {
            logger.lifecycle("Skipped $projectPath: it already exists.")
            return null
        }
        val prefix = namespacePrefix.get()
        val androidOnly = androidOnly.get()
        moduleDirectory.mkdirs()
        moduleDirectory.resolve(BUILD_FILE).writeText(ModuleScaffold.buildFile(moduleSegments, kind, siblings, androidOnly))
        (if (androidOnly) ANDROID_SOURCE_SETS else SOURCE_SETS).forEach { sourceSet ->
            moduleDirectory.resolve(ModuleScaffold.sourceDirectory(prefix, moduleSegments, sourceSet, rootSegments))
                .apply { mkdirs() }
                .resolve(".gitkeep").writeText("")
        }
        if (kind == ModuleKind.WIRING) {
            moduleDirectory.resolve(ModuleScaffold.sourceDirectory(prefix, moduleSegments, ModuleScaffold.mainSourceSet(androidOnly), rootSegments))
                .resolve("${ModuleScaffold.bindingContainerName(segments)}.kt")
                .writeText(ModuleScaffold.bindingContainer(prefix, moduleSegments, rootSegments))
        }
        return projectPath
    }

    private fun reportCreated(created: List<String>) {
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
            |They are plain ${if (androidOnly.get()) "Android libraries" else "multiplatform modules"}: add Compose (libs.plugins.buildkit.compose) or anything
            |else in the build file when the module needs it, and declare dependencies with
            |implementation(...) — api(...) is rejected.
            """.trimMargin(),
        )
    }

    private fun <T> parseOrUsage(layout: ModuleLayout, parse: () -> T): T =
        runCatching(parse).getOrElse { throw GradleException("${it.message}\n\n${usage(layout)}") }

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
