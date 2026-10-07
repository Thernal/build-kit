package io.thernal.buildkit.buildlogic.modules.graph

import io.thernal.buildkit.buildlogic.belowModulesRoot
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleLayout
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency

private val DEPENDENCY_BUCKETS = listOf("implementation", "api", "compileOnly", "runtimeOnly")

/**
 * Every module's declared project dependencies, snapshot while the build is configured — when every
 * project has been evaluated — so `graph` itself stays configuration-cache compatible.
 */
internal fun collectModules(root: Project, layout: ModuleLayout): List<ModuleNode> = root.subprojects
    .filter { it.buildFile.isFile }
    .map { subproject ->
        val relative = subproject.path.removePrefix(":").split(':').belowModulesRoot(layout.root)
        ModuleNode(
            path = subproject.path,
            area = relative.firstOrNull().orEmpty(),
            sourceDirectory = subproject.file("src").absolutePath,
            dependencies = productionProjectDependencies(subproject),
        )
    }

private fun productionProjectDependencies(project: Project): List<String> =
    project.configurations.asSequence()
        .filter { isProductionDependencyConfiguration(it.name) }
        .flatMap { it.dependencies.asSequence() }
        .filterIsInstance<ProjectDependency>()
        .map(ProjectDependency::getPath)
        .distinct()
        .sorted()
        .toList()

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
