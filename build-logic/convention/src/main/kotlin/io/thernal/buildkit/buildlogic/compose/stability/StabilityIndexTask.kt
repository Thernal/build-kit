package io.thernal.buildkit.buildlogic.compose.stability

import io.thernal.buildkit.buildlogic.REPORT_DIRECTORY
import java.io.File
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

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
            .mapNotNull { (path, summary) -> moduleFigures(path, File(summary), directory) }
            .sortedBy { it.first }
        // Pages of modules that are gone, or no longer compile Compose, go with them.
        val current = modulePaths.get().map(::stabilityReportName).toSet() + INDEX_FILE
        directory.listFiles { file -> file.isFile && file.extension == "md" && file.name !in current }?.forEach(File::delete)
        directory.resolve(INDEX_FILE).writeText(renderStabilityIndex(modules))
        logger.lifecycle("Wrote $REPORT_DIRECTORY/$STABILITY_REPORT_DIRECTORY/ for ${modules.size} module(s).")
    }

    private fun moduleFigures(path: String, summary: File, directory: File): Pair<String, Map<String, Int?>>? = when {
        summary.isFile -> {
            val properties = Properties().apply { summary.inputStream().use(::load) }
            path to SUMMARY_FIELDS.associateWith { properties.getProperty(it)?.toIntOrNull() }
        }
        // No metrics this run, but a committed page: listed, without figures.
        directory.resolve(stabilityReportName(path)).isFile -> path to SUMMARY_FIELDS.associateWith { null }
        else -> null
    }

    internal companion object {
        const val INDEX_FILE = "README.md"
    }
}
