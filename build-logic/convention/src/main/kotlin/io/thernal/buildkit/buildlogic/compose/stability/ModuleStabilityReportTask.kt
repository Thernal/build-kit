package io.thernal.buildkit.buildlogic.compose.stability

import io.thernal.buildkit.buildlogic.REPORT_DIRECTORY
import io.thernal.buildkit.buildlogic.TASK_GROUP
import io.thernal.buildkit.buildlogic.compose.STABILITY_REPORT_PROPERTY
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/*
 * Compose stability, reported per module: each Compose module writes its own
 * `report/compose-stability/<module>.md`, so two branches that touch different modules never touch the
 * same report file, and a module's report is rebuilt (or taken from the build cache) only when its own
 * compiler output changed. The root task writes a small index over them ([StabilityIndexTask]).
 *
 * The compiler writes the metrics only in a build run with `-PcomposeStabilityReport=true`.
 */

internal const val STABILITY_REPORT_TASK = "composeStabilityReport"
internal const val STABILITY_REPORT_DIRECTORY = "compose-stability"
internal const val SUMMARY_FILE = "compose-stability/summary.properties"

/** Registers a Compose module's own report task; called by every convention that runs the Compose compiler. */
internal fun Project.registerModuleStabilityReport() {
    val metrics = layout.buildDirectory.dir("compose-metrics")
    val reports = layout.buildDirectory.dir("compose-reports")
    // Captured out here: inside the configuration block `path` is the task's, not the module's.
    val projectPath = path
    tasks.register<ModuleStabilityReportTask>(STABILITY_REPORT_TASK) {
        group = TASK_GROUP
        description = "Writes this module's Compose stability report (build with -P$STABILITY_REPORT_PROPERTY=true first)."
        modulePath.set(projectPath)
        metricsDirectory.set(metrics.map { it.takeIf { directory -> directory.asFile.isDirectory } })
        reportsDirectory.set(reports.map { it.takeIf { directory -> directory.asFile.isDirectory } })
        reportFile.set(rootProject.layout.projectDirectory.file("$REPORT_DIRECTORY/$STABILITY_REPORT_DIRECTORY/${stabilityReportName(projectPath)}"))
        summaryFile.set(layout.buildDirectory.file(SUMMARY_FILE))
    }
}

/** `:features:profile:impl` → `features-profile-impl.md`. */
internal fun stabilityReportName(modulePath: String): String = modulePath.removePrefix(":").replace(':', '-') + ".md"

@CacheableTask
abstract class ModuleStabilityReportTask : DefaultTask() {
    @get:Input abstract val modulePath: Property<String>

    @get:InputDirectory @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metricsDirectory: DirectoryProperty

    @get:InputDirectory @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val reportsDirectory: DirectoryProperty

    @get:OutputFile abstract val reportFile: RegularFileProperty
    @get:OutputFile abstract val summaryFile: RegularFileProperty

    @TaskAction
    fun writeReport() {
        val metrics = metricsDirectory.orNull?.asFile?.let(::preferredMetricsFile)
        val summary = summaryFile.get().asFile.apply { parentFile.mkdirs() }
        val report = reportFile.get().asFile.apply { parentFile.mkdirs() }
        if (metrics == null) {
            // Built without the flag (or cleaned since): the committed page is kept as it is, never
            // replaced by an empty one.
            logger.warn("${modulePath.get()}: no Compose compiler metrics; page kept. Build with -P$STABILITY_REPORT_PROPERTY=true")
            return
        }
        val reports = reportsDirectory.orNull?.asFile
        val stability = ModuleStability(
            path = modulePath.get(),
            fields = parseModuleMetrics(metrics.readText()),
            unstableClasses = reports?.let { unstableClasses(it) }.orEmpty(),
            nonSkippableComposables = reports?.let { nonSkippableComposables(it) }.orEmpty(),
        )
        report.writeText(renderModuleStability(stability))
        summary.writeText(SUMMARY_FIELDS.joinToString("") { field -> "$field=${stability.fields[field] ?: 0}\n" })
    }
}
