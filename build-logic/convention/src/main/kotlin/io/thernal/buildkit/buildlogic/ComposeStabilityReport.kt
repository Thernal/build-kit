package io.thernal.buildkit.buildlogic

import java.io.File
import java.util.Locale
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.api.tasks.UntrackedTask

/*
 * Compose stability, reported per module: each Compose module writes its own
 * `report/compose-stability/<module>.md`, so two branches that touch different modules never touch the
 * same report file, and a module's report is rebuilt (or taken from the build cache) only when its own
 * compiler output changed. The root task writes a small index over them.
 *
 * The compiler writes the metrics only in a build run with `-PcomposeStabilityReport=true`.
 */

internal const val STABILITY_REPORT_TASK = "composeStabilityReport"
internal const val STABILITY_REPORT_DIRECTORY = "compose-stability"
private const val SUMMARY_FILE = "compose-stability/summary.properties"
private val SUMMARY_FIELDS = listOf(
    "totalComposables", "skippableComposables", "restartableComposables", "totalClasses", "effectivelyStableClasses",
)

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
        summary.writeText(
            Properties().apply { SUMMARY_FIELDS.forEach { setProperty(it, (stability.fields[it] ?: 0).toString()) } }
                .let { properties -> buildString { properties.forEach { (key, value) -> appendLine("$key=$value") } } },
        )
    }
}

/**
 * The index over the per-module reports: one row per module, the totals, links. It is the only file
 * every module's change touches, and it is small — the report merge driver keeps one side and the
 * post-merge hook regenerates it.
 */
@UntrackedTask(because = "Tiny, and its real inputs are summaries that may or may not exist")
abstract class StabilityIndexTask : DefaultTask() {
    @get:Input abstract val modulePaths: ListProperty<String>
    @get:Input abstract val summaryFiles: ListProperty<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun writeIndex() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        val modules = modulePaths.get().zip(summaryFiles.get())
            .mapNotNull { (path, summary) ->
                val file = File(summary)
                when {
                    file.isFile -> {
                        val properties = Properties().apply { file.inputStream().use(::load) }
                        path to SUMMARY_FIELDS.associateWith { properties.getProperty(it)?.toIntOrNull() }
                    }
                    // No metrics this run, but a committed page: listed, without figures.
                    directory.resolve(stabilityReportName(path)).isFile -> path to SUMMARY_FIELDS.associateWith { null }
                    else -> null
                }
            }
            .sortedBy { it.first }
        // Pages of modules that are gone, or no longer compile Compose, go with them.
        val current = modulePaths.get().map(::stabilityReportName).toSet() + INDEX_FILE
        directory.listFiles { file -> file.isFile && file.extension == "md" && file.name !in current }?.forEach(File::delete)
        directory.resolve(INDEX_FILE).writeText(renderStabilityIndex(modules))
        logger.lifecycle("Wrote $REPORT_DIRECTORY/$STABILITY_REPORT_DIRECTORY/ for ${modules.size} module(s).")
    }

    internal companion object {
        const val INDEX_FILE = "README.md"
    }
}

internal data class ModuleStability(
    val path: String,
    val fields: Map<String, Int>,
    val unstableClasses: List<String>,
    val nonSkippableComposables: List<String>,
)

/** A multiplatform module compiles Compose once per target; the Android compilation stands in. */
internal fun preferredMetricsFile(metricsDirectory: File): File? =
    metricsDirectory.walkTopDown()
        .filter { it.isFile && it.name.endsWith("-module.json") }
        .sortedWith(compareBy({ !it.invariantSeparatorsPath.contains("/android/") }, { it.invariantSeparatorsPath }))
        .firstOrNull()

internal fun parseModuleMetrics(text: String): Map<String, Int> =
    Regex(""""(\w+)"\s*:\s*(-?\d+)""").findAll(text).associate { it.groupValues[1] to it.groupValues[2].toInt() }

private fun reportLines(reportsDirectory: File, suffix: String): Sequence<String> =
    reportsDirectory.walkTopDown().filter { it.isFile && it.name.endsWith(suffix) }.sortedBy(File::getName)
        .flatMap { it.readLines().asSequence() }

internal fun unstableClasses(reportsDirectory: File): List<String> =
    reportLines(reportsDirectory, "-classes.txt")
        .filter { it.startsWith("unstable class ") }
        .map { it.removePrefix("unstable class ").substringBefore(" {").trim() }
        .distinct().toList()

/** Restartable composables the compiler could not make skippable — the ones that recompose needlessly. */
internal fun nonSkippableComposables(reportsDirectory: File): List<String> =
    reportLines(reportsDirectory, "-composables.txt")
        .filter { it.startsWith("restartable ") && !it.split(' ').contains("skippable") }
        .map { it.substringAfter(" fun ").substringBefore('(').trim() }
        .distinct().toList()

private fun percent(part: Int, total: Int): String =
    if (total == 0) "n/a" else String.format(Locale.ROOT, "%.1f%%", part * 100.0 / total)

internal fun renderModuleStability(module: ModuleStability): String = buildString {
    fun field(name: String) = module.fields[name] ?: 0
    appendLine("# `${module.path}` — Compose stability")
    appendLine()
    appendLine("[← All modules]($INDEX_LINK) · generated by `./gradlew ${module.path}:$STABILITY_REPORT_TASK`. Do not edit by hand.")
    appendLine()
    appendLine("| | |")
    appendLine("|---|---|")
    appendLine("| Composables | ${field("totalComposables")} |")
    appendLine("| Skippable | ${field("skippableComposables")} (${percent(field("skippableComposables"), field("totalComposables"))}) |")
    appendLine("| Restartable | ${field("restartableComposables")} (${percent(field("restartableComposables"), field("totalComposables"))}) |")
    appendLine("| Classes | ${field("totalClasses")} |")
    appendLine("| Effectively stable | ${field("effectivelyStableClasses")} (${percent(field("effectivelyStableClasses"), field("totalClasses"))}) |")
    appendLine()
    appendLine("## Restartable but not skippable")
    appendLine()
    if (module.nonSkippableComposables.isEmpty()) {
        appendLine("None: every restartable composable here skips when its inputs are unchanged.")
    } else {
        appendLine("These recompose with their parent even when nothing they read changed — usually an unstable parameter.")
        appendLine()
        module.nonSkippableComposables.forEach { appendLine("- `$it`") }
    }
    appendLine()
    appendLine("## Unstable classes")
    appendLine()
    if (module.unstableClasses.isEmpty()) {
        appendLine("None.")
    } else {
        appendLine("Make them immutable, mark them `@Immutable`/`@Stable`, or list them in `$STABILITY_CONFIGURATION_FILE`.")
        appendLine()
        module.unstableClasses.forEach { appendLine("- `$it`") }
    }
}

internal fun renderStabilityIndex(modules: List<Pair<String, Map<String, Int?>>>): String = buildString {
    fun sum(field: String) = modules.sumOf { it.second[field] ?: 0 }
    appendLine("# Compose Stability")
    appendLine()
    appendLine("Generated by `./gradlew $STABILITY_REPORT_TASK` from Compose compiler metrics (`-P$STABILITY_REPORT_PROPERTY=true`). Do not edit by hand.")
    appendLine()
    appendLine("A skippable composable recomposes only when its inputs change. Each module's page lists the")
    appendLine("composables that cannot skip and the unstable classes behind them. A row of dashes is a page")
    appendLine("kept from an earlier run: that module was not built with metrics this time.")
    appendLine()
    appendLine("| | |")
    appendLine("|---|---|")
    appendLine("| Modules with Compose code | ${modules.size} |")
    appendLine("| Composables | ${sum("totalComposables")} |")
    appendLine("| Skippable | ${percent(sum("skippableComposables"), sum("totalComposables"))} |")
    appendLine("| Effectively stable classes | ${percent(sum("effectivelyStableClasses"), sum("totalClasses"))} |")
    appendLine()
    appendLine("| Module | Composables | Skippable | Classes | Stable |")
    appendLine("|---|---:|---:|---:|---:|")
    modules.forEach { (path, fields) ->
        val composables = fields["totalComposables"]
        val classes = fields["totalClasses"]
        if (composables == null || classes == null) {
            appendLine("| [`$path`](${stabilityReportName(path)}) | — | — | — | — |")
            return@forEach
        }
        appendLine(
            "| [`$path`](${stabilityReportName(path)}) | $composables | ${percent(fields["skippableComposables"] ?: 0, composables)} " +
                "| $classes | ${percent(fields["effectivelyStableClasses"] ?: 0, classes)} |",
        )
    }
}

private const val INDEX_LINK = "README.md"
