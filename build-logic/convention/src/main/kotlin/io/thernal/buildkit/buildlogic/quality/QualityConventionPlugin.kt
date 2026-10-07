package io.thernal.buildkit.buildlogic.quality

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import io.thernal.buildkit.buildlogic.ANDROID_APPLICATION_PLUGIN
import io.thernal.buildkit.buildlogic.ANDROID_LIBRARY_PLUGIN
import io.thernal.buildkit.buildlogic.KOTLIN_JVM_PLUGIN
import io.thernal.buildkit.buildlogic.KOTLIN_MULTIPLATFORM_PLUGIN
import io.thernal.buildkit.buildlogic.fileListProperty
import io.thernal.buildkit.buildlogic.libs
import io.thernal.buildkit.buildlogic.library
import io.thernal.buildkit.buildlogic.modules.isKitModule
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * One Detekt setup for every module, applied by the module conventions rather than by modules — it
 * has no plugin id, since static analysis is not a capability a module opts into.
 *
 * Findings never fail the build. The pre-commit hook decides: it diffs a run against the persisted
 * report in `.misc/detekt/`, blocks a commit that introduces new findings, and marks the known ones
 * `// TODO: Detekt [...]` in the source ([DetektPipelinePlugin]).
 *
 * [DETEKT_ANALYSIS_TASK] picks the tasks that count, each source file exactly once. Detekt registers
 * one task per source set and one per compilation; only the compilation tasks resolve types, which
 * `UnsafeCollectionIndexAccess` needs, so they are preferred wherever they cover a file.
 */
internal class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dev.detekt")
        configureDetekt()
        configureDetektTasks(
            changedFiles = fileListProperty(DETEKT_CHANGED_FILES_PROPERTY)?.filter { it.extension == "kt" }?.toSet(),
            autoCorrect = providers.gradleProperty(DETEKT_AUTO_CORRECT_PROPERTY).orNull == "true",
        )
        val analysis = tasks.register(DETEKT_ANALYSIS_TASK) {
            group = "verification"
            description = "Runs every Detekt task whose findings the pre-commit hook and detektFull count."
        }
        // Code a kit installed counts for nothing here (see isKitModule): its analysis task stays empty.
        if (!isKitModule()) selectCountedAnalyses(analysis)
    }
}

private fun Project.configureDetekt() {
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        allRules.set(false)
        parallel.set(true)
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }
    val catalog = libs
    dependencies {
        add("detektPlugins", catalog.library("detekt-ktlint-wrapper"))
        // Substituted for build-logic's `:detekt-rules` project, which is an included build.
        add("detektPlugins", "io.thernal.buildkit.buildlogic:detekt-rules")
    }
}

private fun Project.configureDetektTasks(changedFiles: Set<File>?, autoCorrect: Boolean) {
    tasks.withType<Detekt>().configureEach {
        exclude("**/build/**", "**/generated/**")
        ignoreFailures.set(true)
        this.autoCorrect.set(autoCorrect)
        if (changedFiles != null) {
            include { element -> element.isDirectory || element.file.canonicalFile in changedFiles }
        }
        reports {
            checkstyle.required.set(true)
            checkstyle.outputLocation.set(layout.buildDirectory.file("$DETEKT_ANALYSIS_REPORTS/$name.xml"))
            html.required.set(true)
            sarif.required.set(false)
            markdown.required.set(false)
        }
    }
}

private fun Project.selectCountedAnalyses(analysis: TaskProvider<Task>) {
    pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN) {
        analysis.configure { dependsOn(tasks.withType<Detekt>().matching { isCountedMultiplatformAnalysis(it.name) }) }
    }
    pluginManager.withPlugin(KOTLIN_JVM_PLUGIN) {
        analysis.configure { dependsOn(tasks.withType<Detekt>().matching { isCountedJvmAnalysis(it.name) }) }
    }
    // An Android application or library has only per-variant source-set tasks, none of which sees
    // `src/main` alone; the plain task analyses the conventional source directories, without types.
    listOf(ANDROID_APPLICATION_PLUGIN, ANDROID_LIBRARY_PLUGIN).forEach { plugin ->
        pluginManager.withPlugin(plugin) {
            analysis.configure { dependsOn(tasks.named("detekt")) }
        }
    }
}
