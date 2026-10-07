package io.thernal.buildkit.buildlogic.quality

import io.thernal.buildkit.buildlogic.fileListProperty
import io.thernal.buildkit.buildlogic.resolveAgainst
import java.io.File
import org.gradle.api.Project
import org.gradle.api.Task

/*
 * What the root Detekt tasks do when they run: the Gradle side of the file work in DetektReports.kt.
 */

private val SKIPPED_SOURCE_DIRECTORIES = setOf("build", ".git", ".gradle", "build-logic")
private val SKIPPED_REPORT_DIRECTORIES = setOf(".git", ".gradle", "build-logic", "src")

internal fun Task.clearTodos() = with(project) {
    // A report left by a task that no longer counts, or by an earlier scope, would be merged as if
    // this run had produced it.
    subprojects.forEach { it.layout.buildDirectory.dir(DETEKT_ANALYSIS_REPORTS).get().asFile.deleteRecursively() }
    val modified = kotlinFiles().count { file -> clearDetektTodos(file.readText())?.also(file::writeText) != null }
    logger.lifecycle("detekt: removed markers from $modified file(s)")
}

internal fun Task.mergeReports() = with(project) {
    val baseline = rootDir.resolve(DETEKT_BASELINE).apply { parentFile.mkdirs() }
    val canonical = { raw: String -> resolveAgainst(rootDir, raw).path }
    val scanned = canonicalPaths(DETEKT_CHANGED_FILES_PROPERTY)
    val stale = canonicalPaths(DETEKT_STALE_FILES_PROPERTY).orEmpty()

    val fresh = analysisReports()
        .flatMap { fileBlocks(it.readText(), canonical).entries }
        .filter { (path, _) -> scanned == null || path in scanned }
        .associate { it.key to it.value }
    if (fresh.isEmpty() && stale.isEmpty() && scanned != null) {
        logger.lifecycle("detekt: nothing scanned and nothing stale; $DETEKT_BASELINE unchanged")
        return@with
    }
    val previous = if (baseline.isFile) fileBlocks(baseline.readText(), canonical) else emptyMap()
    baseline.writeText(mergeBaseline(previous, fresh, scanned, stale))
    logger.lifecycle("detekt: $DETEKT_BASELINE updated from ${fresh.size} scanned file(s)")
}

internal fun Task.annotateSources() = with(project) {
    val scoped = canonicalPaths(DETEKT_CHANGED_FILES_PROPERTY)
    val byFile = linkedMapOf<File, MutableMap<Int, MutableSet<String>>>()
    analysisReports()
        .flatMap { report -> findings(report, rootDir).entries }
        .filter { (file, _) -> file.isFile && file.extension == "kt" && (scoped == null || file.canonicalPath in scoped) }
        .forEach { (file, lines) ->
            val target = byFile.getOrPut(file) { linkedMapOf() }
            lines.forEach { (line, labels) -> target.getOrPut(line) { linkedSetOf() }.addAll(labels) }
        }
    byFile.forEach { (file, lines) -> file.writeText(annotate(file.readText(), lines)) }
    logger.lifecycle("detekt: ${byFile.values.sumOf { it.values.sumOf(Set<String>::size) }} marker(s) in ${byFile.size} file(s)")
}

private fun Project.canonicalPaths(property: String): Set<String>? =
    fileListProperty(property)?.map(File::getPath)?.toSet()

private fun Project.kotlinFiles(): Sequence<File> =
    fileListProperty(DETEKT_CHANGED_FILES_PROPERTY)?.filter { it.isFile && it.extension == "kt" }
        ?: rootDir.walkTopDown()
            .onEnter { it.name !in SKIPPED_SOURCE_DIRECTORIES }
            .filter { it.isFile && it.extension == "kt" }

private fun Project.analysisReports(): Sequence<File> =
    rootDir.walkTopDown()
        .onEnter { it.name !in SKIPPED_REPORT_DIRECTORIES }
        .filter { it.isFile && it.extension == "xml" && it.parentFile.invariantSeparatorsPath.endsWith("build/$DETEKT_ANALYSIS_REPORTS") }
