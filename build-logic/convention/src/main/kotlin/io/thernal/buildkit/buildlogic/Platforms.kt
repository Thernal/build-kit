package io.thernal.buildkit.buildlogic

import org.gradle.api.Project

/**
 * Gradle property naming what the application ships on: `android,ios` (the default) or `android`.
 * It decides only what a module gets when its build file does not say — conventions applied by name
 * (`android.library`, `kmp.library`) always win.
 */
internal const val PLATFORMS_PROPERTY = "app.platforms"

internal const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
internal const val ANDROID_APPLICATION_PLUGIN = "com.android.application"
internal const val KOTLIN_MULTIPLATFORM_PLUGIN = "org.jetbrains.kotlin.multiplatform"
internal const val KOTLIN_JVM_PLUGIN = "org.jetbrains.kotlin.jvm"
internal const val COMPOSE_COMPILER_PLUGIN = "org.jetbrains.kotlin.plugin.compose"

/** True when `app.platforms` lists Android alone: a repository with no iOS app and no shared code. */
internal fun Project.isAndroidOnly(): Boolean = isAndroidOnly(providers.gradleProperty(PLATFORMS_PROPERTY).orNull)

internal fun isAndroidOnly(platforms: String?): Boolean {
    val listed = platforms.commaSeparated().map(String::lowercase).toList()
    return listed == listOf("android")
}

/**
 * Whether this module is a plain Android module — an Android library or application — rather than a
 * multiplatform one. Read when a convention is applied, so a build file names `android.library`
 * before `compose` or `environment`; with neither, [isAndroidOnly] decides.
 */
internal fun Project.isAndroidModule(): Boolean = when {
    pluginManager.hasPlugin(ANDROID_LIBRARY_PLUGIN) || pluginManager.hasPlugin(ANDROID_APPLICATION_PLUGIN) -> true
    pluginManager.hasPlugin(KOTLIN_MULTIPLATFORM_PLUGIN) -> false
    else -> isAndroidOnly()
}
