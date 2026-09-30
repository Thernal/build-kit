package io.thernal.buildkit.buildlogic

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/** Comma-separated absolute paths of deleted or renamed-away files, whose entries are dropped. */
internal const val DETEKT_STALE_FILES_PROPERTY = "detektStaleReportFiles"

/**
 * The root half of the Detekt workflow, applied once to the root project:
 *
 * - `detektClearTodos` removes `// TODO: Detekt [...]` markers (scoped by `-PdetektChangedFiles`);
 * - every module's `detektAnalysis` writes its checkstyle reports;
 * - `detektMergeDelta` folds them into `.misc/detekt/detekt-report.xml`, the persisted report;
 * - `detektAnnotate` writes the findings back onto their lines as markers;
 * - `detektFull` runs all four over the whole repository.
 *
 * `.githooks/pre-commit` runs the scoped version on the changed Kotlin files and blocks a commit
 * whose findings are not in the persisted report yet — having merged them, so the same commit passes
 * the second time, with its findings marked in the source. Applying this plugin also points Git at
 * `.githooks/`, so the hook works after the first Gradle sync, with nothing to set up by hand.
 */
class DetektPipelinePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (this != rootProject) throw GradleException("io.thernal.buildkit.detekt must be applied to the root project.")
        restoreExecutableBits(rootDir)
        installGitHooks(rootDir, isCi = providers.environmentVariable("CI").isPresent)

        val analyses = provider { subprojects.mapNotNull { it.tasks.findByName(DETEKT_ANALYSIS_TASK) } }

        val clear = tasks.register("detektClearTodos") {
            group = "verification"
            description = "Removes the '$DETEKT_TODO_MARKER [...]' markers and old module reports before an analysis."
            notCompatibleWithConfigurationCache("Rewrites Kotlin sources.")
            doLast {
                // A report left by a task that no longer counts, or by an earlier scope, would be
                // merged as if this run had produced it.
                subprojects.forEach { it.layout.buildDirectory.dir(DETEKT_ANALYSIS_REPORTS).get().asFile.deleteRecursively() }
                val modified = kotlinFiles().count { file ->
                    clearDetektTodos(file.readText())?.also(file::writeText) != null
                }
                logger.lifecycle("detekt: removed markers from $modified file(s)")
            }
        }

        val merge = tasks.register("detektMergeDelta") {
            group = "verification"
            description = "Merges this run's module reports into $DETEKT_BASELINE."
            notCompatibleWithConfigurationCache("Reads and writes report files.")
            mustRunAfter(analyses)
            doLast { mergeReports() }
        }

        val annotate = tasks.register("detektAnnotate") {
            group = "verification"
            description = "Writes this run's findings onto their source lines as '$DETEKT_TODO_MARKER [...]'."
            notCompatibleWithConfigurationCache("Reads reports and rewrites Kotlin sources.")
            mustRunAfter(analyses, merge)
            doLast { annotateSources() }
        }

        tasks.register("detektFull") {
            group = "verification"
            description = "Clears the markers, analyses every module, replaces $DETEKT_BASELINE and re-annotates."
            dependsOn(clear, analyses, merge, annotate)
        }

        gradle.projectsEvaluated {
            analyses.get().forEach { task -> task.mustRunAfter(clear) }
        }
    }
}

private fun Project.changedFiles(): List<File>? =
    providers.gradleProperty(DETEKT_CHANGED_FILES_PROPERTY).orNull
        ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        ?.map { File(it).canonicalFile }

private fun Project.canonicalPaths(property: String): Set<String>? =
    providers.gradleProperty(property).orNull
        ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        ?.map { File(it).canonicalPath }?.toSet()

private fun Project.kotlinFiles(): List<File> =
    changedFiles()?.filter { it.isFile && it.extension == "kt" }
        ?: rootDir.walkTopDown()
            .onEnter { it.name !in setOf("build", ".git", ".gradle", "build-logic") }
            .filter { it.isFile && it.extension == "kt" }
            .toList()

private fun Project.analysisReports(): List<File> =
    rootDir.walkTopDown()
        .onEnter { it.name !in setOf(".git", ".gradle", "build-logic", "src") }
        .filter { it.isFile && it.extension == "xml" && it.parentFile.invariantSeparatorsPath.endsWith("build/$DETEKT_ANALYSIS_REPORTS") }
        .toList()

private fun Task.mergeReports() = with(project) {
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

private fun Task.annotateSources() = with(project) {
    val scoped = canonicalPaths(DETEKT_CHANGED_FILES_PROPERTY)
    val byFile = linkedMapOf<File, MutableMap<Int, MutableSet<String>>>()
    analysisReports().forEach { report ->
        findings(report, rootDir).forEach { (file, lines) ->
            if (file.isFile && file.extension == "kt" && (scoped == null || file.canonicalPath in scoped)) {
                val target = byFile.getOrPut(file) { linkedMapOf() }
                lines.forEach { (line, labels) -> target.getOrPut(line) { linkedSetOf() }.addAll(labels) }
            }
        }
    }
    byFile.forEach { (file, lines) -> file.writeText(annotate(file.readText(), lines)) }
    logger.lifecycle("detekt: ${byFile.values.sumOf { it.values.sumOf(Set<String>::size) }} marker(s) in ${byFile.size} file(s)")
}
