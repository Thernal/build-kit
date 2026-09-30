package io.thernal.buildkit.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** Comma-separated absolute paths; set by the pre-commit hook to analyse only what changed. */
internal const val DETEKT_CHANGED_FILES_PROPERTY = "detektChangedFiles"

/** `true` lets ktlint rewrite formatting in place during analysis (the pre-commit hook sets it). */
internal const val DETEKT_AUTO_CORRECT_PROPERTY = "detektAutoCorrect"

/** Per module: every Detekt task whose findings count, and nothing twice. */
internal const val DETEKT_ANALYSIS_TASK = "detektAnalysis"

/** Where the counted tasks write their checkstyle XML, for the root pipeline to merge. */
internal const val DETEKT_ANALYSIS_REPORTS = "reports/detekt/analysis"

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

        val catalog = libs
        val changedFiles = changedKotlinFiles()
        val autoCorrect = providers.gradleProperty(DETEKT_AUTO_CORRECT_PROPERTY).orNull == "true"

        extensions.configure<DetektExtension> {
            buildUponDefaultConfig.set(true)
            allRules.set(false)
            parallel.set(true)
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        }

        dependencies {
            add("detektPlugins", catalog.library("detekt-ktlint-wrapper"))
            // Substituted for build-logic's `:detekt-rules` project, which is an included build.
            add("detektPlugins", "io.thernal.buildkit.buildlogic:detekt-rules")
        }

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

        val analysis = tasks.register(DETEKT_ANALYSIS_TASK) {
            group = "verification"
            description = "Runs every Detekt task whose findings the pre-commit hook and detektFull count."
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            analysis.configure { dependsOn(tasks.withType<Detekt>().matching { isCountedMultiplatformAnalysis(it.name) }) }
        }
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            analysis.configure { dependsOn(tasks.withType<Detekt>().matching { isCountedJvmAnalysis(it.name) }) }
        }
        // An application module has only per-variant source-set tasks, none of which sees
        // `src/main`; the plain task analyses the conventional source directories, without types.
        pluginManager.withPlugin("com.android.application") {
            analysis.configure { dependsOn(tasks.named("detekt")) }
        }
        Unit
    }
}

private fun Project.changedKotlinFiles(): Set<File>? =
    providers.gradleProperty(DETEKT_CHANGED_FILES_PROPERTY).orNull
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.map { File(it).canonicalFile }
        ?.filter { it.extension == "kt" }
        ?.toSet()

/**
 * A multiplatform module: the type-resolved Android compilations (`detektMainAndroid`,
 * `detektHostTestAndroid`), which cover common and Android sources, plus the source-set tasks for
 * everything no Android compilation sees (`detektIosMainSourceSet`, `detektAppleMainSourceSet`, …).
 */
internal fun isCountedMultiplatformAnalysis(name: String): Boolean {
    if (name == "detekt") return false
    if (!name.endsWith("SourceSet")) return true
    val sourceSet = name.removePrefix("detekt").removeSuffix("SourceSet")
    return !sourceSet.startsWith("Common") && !sourceSet.startsWith("Android")
}

/** A JVM module: the type-resolved compilations (`detektMain`, `detektTest`), which cover everything. */
internal fun isCountedJvmAnalysis(name: String): Boolean = name != "detekt" && !name.endsWith("SourceSet")
