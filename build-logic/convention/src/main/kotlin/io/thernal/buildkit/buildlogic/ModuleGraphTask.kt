package io.thernal.buildkit.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the module report: one Markdown file per view plus an index that links them.
 *
 * ```bash
 * ./gradlew graph
 * ```
 *
 * The dependency declarations are collected while the build is configured, so the task itself only
 * reads Kotlin sources and writes the report.
 */
@DisableCachingByDefault(because = "Counts declarations across every module on each run")
abstract class ModuleGraphTask : DefaultTask() {

    @get:Input
    abstract val modules: ListProperty<ModuleNode>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun writeReport() {
        val nodes = modules.get().sortedBy(ModuleNode::path)
        val pages = ModuleGraph.render(modules = nodes, task = name)
        val directory = outputDirectory.get().asFile

        directory.mkdirs()
        directory.listFiles { file -> file.isFile && file.extension == "md" }
            // The stability report shares the directory and is written by its own task.
            ?.filterNot { file -> file.name in pages.keys || file.name == STABILITY_REPORT_FILE }
            ?.forEach { stale -> stale.delete() }
        pages.forEach { (name, content) -> directory.resolve(name).writeText(content) }

        logger.lifecycle("Wrote ${pages.size} pages for ${nodes.size} modules to ${directory.name}/${ModuleGraph.INDEX}")
    }
}
