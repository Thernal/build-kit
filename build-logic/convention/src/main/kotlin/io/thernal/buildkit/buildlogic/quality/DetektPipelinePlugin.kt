package io.thernal.buildkit.buildlogic.quality

import io.thernal.buildkit.buildlogic.git.installGitHooks
import io.thernal.buildkit.buildlogic.git.restoreExecutableBits
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

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
        val clear = registerClearTask()
        val merge = registerMergeTask(analyses)
        val annotate = registerAnnotateTask(analyses, merge)
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

private fun Project.registerClearTask(): TaskProvider<Task> = tasks.register("detektClearTodos") {
    group = "verification"
    description = "Removes the '$DETEKT_TODO_MARKER [...]' markers and old module reports before an analysis."
    notCompatibleWithConfigurationCache("Rewrites Kotlin sources.")
    doLast { clearTodos() }
}

private fun Project.registerMergeTask(analyses: Provider<List<Task>>): TaskProvider<Task> = tasks.register("detektMergeDelta") {
    group = "verification"
    description = "Merges this run's module reports into $DETEKT_BASELINE."
    notCompatibleWithConfigurationCache("Reads and writes report files.")
    mustRunAfter(analyses)
    doLast { mergeReports() }
}

private fun Project.registerAnnotateTask(
    analyses: Provider<List<Task>>,
    merge: TaskProvider<Task>,
): TaskProvider<Task> = tasks.register("detektAnnotate") {
    group = "verification"
    description = "Writes this run's findings onto their source lines as '$DETEKT_TODO_MARKER [...]'."
    notCompatibleWithConfigurationCache("Reads reports and rewrites Kotlin sources.")
    mustRunAfter(analyses, merge)
    doLast { annotateSources() }
}
