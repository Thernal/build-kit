package io.thernal.buildkit.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.register

/** Gradle property listing the dependencies `api(...)` may still name, comma-separated. */
internal const val API_ALLOWED_PROPERTY = "app.api.allowed"

/**
 * Repository-level rules, applied once to the root project. Today: no module may declare an
 * `api(...)` dependency.
 *
 * `api` puts a dependency on every consumer's compile classpath, so an ABI change there recompiles
 * the whole downstream graph and a consumer's build file no longer says what it uses. Both cost more
 * than the duplicated declaration `implementation` asks for. Kits follow the same rule, so an
 * installed kit never trips it.
 *
 * The one thing only `api` can do is Kotlin/Native `export(...)`: a module exported into the iOS
 * framework, so Swift sees its types (Firebase's Swift bridges), has to be an `api` dependency of
 * the framework module. Such dependencies are listed by name in `app.api.allowed` — a project path
 * (`:core:firebase`) or a `group:name` coordinate — and nothing else passes.
 */
class ModulesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (this != rootProject) throw GradleException("io.thernal.buildkit.modules must be applied to the root project.")
        val allowed = parseAllowedApi(providers.gradleProperty(API_ALLOWED_PROPERTY).orNull)
        subprojects.forEach { subproject -> subproject.afterEvaluate { rejectApiDependencies(this, allowed) } }

        val moduleLayout = ModuleLayout.parse(
            providers.gradleProperty(MODULES_ROOT_PROPERTY).orNull,
            providers.gradleProperty(MODULES_AREAS_PROPERTY).orNull,
        )

        tasks.register<CreateModuleTask>("create") {
            group = TASK_GROUP
            description = "Creates api/impl/wiring modules, for example: ./gradlew create profile api impl wiring"
            arguments.convention(
                providers.systemProperty(CREATE_ARGUMENTS_PROPERTY).filter(String::isNotBlank)
                    .orElse(providers.gradleProperty(CREATE_ARGUMENTS_PROPERTY)),
            )
            namespacePrefix.set(providers.gradleProperty(NAMESPACE_PROPERTY))
            modulesRoot.set(moduleLayout.root.joinToString("/"))
            modulesAreas.set(moduleLayout.areas.joinToString(","))
            repositoryDirectory.set(layout.projectDirectory)
        }

        installReportMergeDriver(rootDir)
        configureDependencyAnalysis()

        tasks.register<ModuleGraphTask>("graph") {
            group = TASK_GROUP
            description = "Writes the module report under $REPORT_DIRECTORY/."
            outputDirectory.set(layout.projectDirectory.dir(REPORT_DIRECTORY))
            modules.set(providers.provider { collectModules(this@with, moduleLayout) })
        }

        tasks.register<ComposeStabilityReportTask>("composeStabilityReport") {
            group = TASK_GROUP
            description = "Aggregates Compose compiler metrics (-P$STABILITY_REPORT_PROPERTY=true) into $REPORT_DIRECTORY/$STABILITY_REPORT_FILE."
            repositoryDirectory.set(layout.projectDirectory)
            outputDirectory.set(layout.projectDirectory.dir(REPORT_DIRECTORY))
        }
        Unit
    }
}

internal const val TASK_GROUP = "modules"
internal const val REPORT_DIRECTORY = "report"

/**
 * Every module's declared project dependencies, snapshot while the build is configured — when every
 * project has been evaluated — so `graph` itself stays configuration-cache compatible.
 */
private fun collectModules(root: Project, layout: ModuleLayout): List<ModuleNode> = root.subprojects
    .filter { it.buildFile.isFile }
    .map { subproject ->
        val segments = subproject.path.removePrefix(":").split(':')
        val relative = if (segments.take(layout.root.size) == layout.root) segments.drop(layout.root.size) else segments
        ModuleNode(
            path = subproject.path,
            area = relative.firstOrNull().orEmpty(),
            sourceDirectory = subproject.file("src").absolutePath,
            dependencies = subproject.configurations
                .filter { isProductionDependencyConfiguration(it.name) }
                .flatMap { it.dependencies }
                .filterIsInstance<ProjectDependency>()
                .map(ProjectDependency::getPath)
                .distinct()
                .sorted(),
        )
    }

/**
 * The buckets production code is compiled from: `implementation`, `api`, `compileOnly`, `runtimeOnly`
 * and their per-variant and per-source-set forms (`commonMainImplementation`, `debugApi`), minus
 * every test form (`commonTestImplementation`, `testImplementation`, `androidHostTestImplementation`).
 */
internal fun isProductionDependencyConfiguration(name: String): Boolean {
    val bucket = DEPENDENCY_BUCKETS.firstOrNull { name == it || name.endsWith(it.replaceFirstChar(Char::uppercaseChar)) }
        ?: return false
    val scope = name.removeSuffix(bucket.replaceFirstChar(Char::uppercaseChar)).removeSuffix(bucket)
    return !scope.contains("test", ignoreCase = true)
}

private val DEPENDENCY_BUCKETS = listOf("implementation", "api", "compileOnly", "runtimeOnly")

internal fun parseAllowedApi(value: String?): Set<String> =
    value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()

/**
 * The `api` buckets of a module, in every shape Gradle and Kotlin create them: `api` and
 * `<variant>Api` for Android and Java, `<sourceSet>Api` for multiplatform (`commonMainApi`,
 * `iosMainApi`).
 */
internal fun isApiConfiguration(name: String): Boolean = name == "api" || name.endsWith("Api")

private fun rejectApiDependencies(project: Project, allowed: Set<String>) {
    val declared = project.configurations
        .filter { isApiConfiguration(it.name) }
        .flatMap { configuration ->
            configuration.dependencies
                .map { dependency ->
                    when (dependency) {
                        is ProjectDependency -> dependency.path
                        else -> dependency.group?.let { "$it:${dependency.name}" } ?: dependency.name
                    }
                }
                .filterNot { it in allowed }
                .map { "${configuration.name}($it)" }
        }
        .distinct()
        .sorted()
    if (declared.isEmpty()) return

    throw GradleException(
        buildString {
            appendLine("${project.path} declares api(...) dependencies: ${declared.joinToString()}.")
            appendLine("`api(...)` is not used in this repository. Use implementation(...) and declare the")
            appendLine("dependency in every module that uses its types. A module exported into the iOS framework")
            append("is the one exception: add it to $API_ALLOWED_PROPERTY in gradle.properties.")
        },
    )
}
