package io.thernal.buildkit.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

/*
 * Flavor-scoped dependencies for Kotlin Multiplatform source sets, `commonMain` included.
 *
 * A dependency declared through these is added only when the invocation's [activeFlavor] matches,
 * and is absent from every target's classpath otherwise — the way a debug console or a fake backend
 * stays out of the production binary. There is no `api` variant: `api(...)` is not used.
 *
 * The same three exist on a plain `dependencies {}` block, for an Android library
 * ([AndroidLibraryConventionPlugin]) or a JVM module, which have no product flavors either.
 *
 * An Android application module has real product flavors, so it uses AGP's own
 * `regressImplementation(...)` / `prodImplementation(...)` instead.
 */

/** Adds [dependencyNotation] when the active flavor is one of [flavors]. */
fun KotlinDependencyHandler.flavorImplementation(vararg flavors: String, dependencyNotation: Any) {
    val known = project.appFlavors()
    val wanted = flavors.map { known.require(it, "flavorImplementation in ${project.path}") }
    if (project.activeFlavor() in wanted) {
        implementation(dependencyNotation)
    }
}

/** Adds [dependencyNotation] only to production builds (`app.flavors.production`). */
fun KotlinDependencyHandler.productionImplementation(dependencyNotation: Any) {
    flavorImplementation(project.appFlavors().production, dependencyNotation = dependencyNotation)
}

/** Adds [dependencyNotation] to every flavor except production. */
fun KotlinDependencyHandler.nonProductionImplementation(dependencyNotation: Any) {
    val flavors = project.appFlavors().nonProduction
    flavorImplementation(*flavors.toTypedArray(), dependencyNotation = dependencyNotation)
}

/** Adds [dependencyNotation] to `implementation` when the active flavor is one of [flavors]. */
fun DependencyHandler.flavorImplementation(project: Project, vararg flavors: String, dependencyNotation: Any) {
    val known = project.appFlavors()
    val wanted = flavors.map { known.require(it, "flavorImplementation in ${project.path}") }
    if (project.activeFlavor() in wanted) {
        add("implementation", dependencyNotation)
    }
}

/** Adds [dependencyNotation] to `implementation` only in production builds. */
fun DependencyHandler.productionImplementation(project: Project, dependencyNotation: Any) {
    flavorImplementation(project, project.appFlavors().production, dependencyNotation = dependencyNotation)
}

/** Adds [dependencyNotation] to `implementation` in every flavor except production. */
fun DependencyHandler.nonProductionImplementation(project: Project, dependencyNotation: Any) {
    val flavors = project.appFlavors().nonProduction
    flavorImplementation(project, *flavors.toTypedArray(), dependencyNotation = dependencyNotation)
}
