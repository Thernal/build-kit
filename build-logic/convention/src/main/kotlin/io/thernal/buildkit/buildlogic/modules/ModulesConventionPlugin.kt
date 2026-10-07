package io.thernal.buildkit.buildlogic.modules

import io.thernal.buildkit.buildlogic.MODULES_ROOT_PROPERTY
import io.thernal.buildkit.buildlogic.NAMESPACE_PROPERTY
import io.thernal.buildkit.buildlogic.REPORT_DIRECTORY
import io.thernal.buildkit.buildlogic.TASK_GROUP
import io.thernal.buildkit.buildlogic.compose.stability.STABILITY_REPORT_DIRECTORY
import io.thernal.buildkit.buildlogic.compose.stability.STABILITY_REPORT_TASK
import io.thernal.buildkit.buildlogic.compose.stability.SUMMARY_FILE
import io.thernal.buildkit.buildlogic.compose.stability.StabilityIndexTask
import io.thernal.buildkit.buildlogic.git.installReportMergeDriver
import io.thernal.buildkit.buildlogic.isAndroidOnly
import io.thernal.buildkit.buildlogic.modules.graph.ModuleGraphTask
import io.thernal.buildkit.buildlogic.modules.graph.collectModules
import io.thernal.buildkit.buildlogic.modules.scaffold.CREATE_ARGUMENTS_PROPERTY
import io.thernal.buildkit.buildlogic.modules.scaffold.CreateModuleTask
import io.thernal.buildkit.buildlogic.modules.scaffold.MODULES_AREAS_PROPERTY
import io.thernal.buildkit.buildlogic.modules.scaffold.ModuleLayout
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

/**
 * Repository-level rules and tooling, applied once to the root project: the `api(...)` ban
 * ([rejectApiDependencies]), `create`, `graph`, the Compose stability index, and dependency analysis.
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
        registerCreateTask(moduleLayout)
        registerGraphTask(moduleLayout)
        registerStabilityIndexTask()
        installReportMergeDriver(rootDir)
        configureDependencyAnalysis()
    }
}

private fun Project.registerCreateTask(moduleLayout: ModuleLayout) {
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
        androidOnly.set(isAndroidOnly())
        repositoryDirectory.set(layout.projectDirectory)
    }
}

private fun Project.registerGraphTask(moduleLayout: ModuleLayout) {
    val root = this
    tasks.register<ModuleGraphTask>("graph") {
        group = TASK_GROUP
        description = "Writes the module report under $REPORT_DIRECTORY/."
        outputDirectory.set(layout.projectDirectory.dir(REPORT_DIRECTORY))
        modules.set(providers.provider { collectModules(root, moduleLayout) })
    }
}

/**
 * Same name as the module tasks, so `./gradlew composeStabilityReport` runs every module's and then
 * this index over them.
 */
private fun Project.registerStabilityIndexTask() {
    val composeModules = provider { subprojects.filter { it.tasks.findByName(STABILITY_REPORT_TASK) != null } }
    tasks.register<StabilityIndexTask>(STABILITY_REPORT_TASK) {
        group = TASK_GROUP
        description = "Writes $REPORT_DIRECTORY/$STABILITY_REPORT_DIRECTORY/ — one page per Compose module and an index."
        dependsOn(composeModules.map { modules -> modules.map { it.tasks.named(STABILITY_REPORT_TASK) } })
        modulePaths.set(composeModules.map { modules -> modules.map(Project::getPath) })
        summaryFiles.set(
            composeModules.map { modules -> modules.map { it.layout.buildDirectory.file(SUMMARY_FILE).get().asFile.path } },
        )
        outputDirectory.set(layout.projectDirectory.dir("$REPORT_DIRECTORY/$STABILITY_REPORT_DIRECTORY"))
    }
}
