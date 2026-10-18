package io.thernal.buildkit.buildlogic.injection

import io.thernal.buildkit.buildlogic.ANDROID_APPLICATION_PLUGIN
import io.thernal.buildkit.buildlogic.KOTLIN_MULTIPLATFORM_PLUGIN
import io.thernal.buildkit.buildlogic.MODULES_ROOT_PROPERTY
import io.thernal.buildkit.buildlogic.commaSeparated
import io.thernal.buildkit.buildlogic.parseModulesRoot
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * For the module that declares the Metro graph — an app's `shared` module, or an Android-only app's module:
 * Metro, and every wiring module as a dependency, so a new capability joins the graph without an edit to the
 * app. Metro collects what is on the classpath, so the app names no `impl` and no other `api` than its own code
 * imports. `app.graph.exclude` and `app.graph.<app>.exclude` keep paths out: a capability not set up yet, or
 * another app's features in a product with several apps.
 */
class GraphConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply(InjectionConventionPlugin::class.java)

        val modulesRoot = parseModulesRoot(providers.gradleProperty(MODULES_ROOT_PROPERTY).orNull)
        val app = appNameOf(path, modulesRoot)
        val excluded = (
            providers.gradleProperty(GRAPH_EXCLUDE_PROPERTY).orNull.commaSeparated() +
                app?.let { name -> providers.gradleProperty(graphExcludeProperty(name)).orNull.commaSeparated() }.orEmpty()
            ).toList()
        val projectPaths = rootProject.allprojects.map(Project::getPath)
        val unknown = unknownGraphExclusions(projectPaths, excluded)
        check(unknown.isEmpty()) {
            "$GRAPH_EXCLUDE_PROPERTY (or its per-app form) names ${unknown.joinToString()}, which is no module and no " +
                "directory of modules"
        }
        val wiring = graphWiring(projectPaths, modulesRoot, excluded).map { wiringPath ->
            dependencies.project(mapOf("path" to wiringPath))
        }

        pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN) {
            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.named("commonMain") {
                    dependencies { wiring.forEach { dependency -> implementation(dependency) } }
                }
            }
        }
        pluginManager.withPlugin(ANDROID_APPLICATION_PLUGIN) {
            wiring.forEach { dependency -> dependencies.add("implementation", dependency) }
        }
    }
}
