package io.thernal.buildkit.buildlogic.application

import com.android.build.api.dsl.ApplicationExtension
import io.thernal.buildkit.buildlogic.ANDROID_APPLICATION_PLUGIN
import io.thernal.buildkit.buildlogic.COMPOSE_COMPILER_PLUGIN
import io.thernal.buildkit.buildlogic.compose.configureComposeCompiler
import io.thernal.buildkit.buildlogic.configureJavaToolchain
import io.thernal.buildkit.buildlogic.defaultNamespace
import io.thernal.buildkit.buildlogic.flavor.AppFlavors
import io.thernal.buildkit.buildlogic.flavor.ENVIRONMENT_DIMENSION
import io.thernal.buildkit.buildlogic.flavor.appFlavors
import io.thernal.buildkit.buildlogic.flavor.applicationIdSuffix
import io.thernal.buildkit.buildlogic.intVersion
import io.thernal.buildkit.buildlogic.libs
import io.thernal.buildkit.buildlogic.quality.QualityConventionPlugin
import io.thernal.buildkit.buildlogic.targetJvm
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.kotlin.dsl.configure

/**
 * The Android side of an app — `apps/<name>/android`, one per app a repository ships, beside the
 * app's `ios/` (the Xcode project) and `shared/` (its Kotlin Multiplatform root, which both embed).
 * In an Android-only application (`app.platforms=android`) the app is the module itself:
 * `apps/<name>`, with `version.properties` beside its build file.
 *
 * Every application carries the same `environment` flavor dimension, built from `app.flavors`:
 * non-production flavors get an application id suffix (`.dev`), a version name suffix (`-dev`) and
 * the shared debug signature, so they install beside production; the production flavor gets release
 * signing. Version codes come per flavor from the app's `version.properties` (`apps/<name>/`).
 *
 * The namespace and application id default to `app.namespace` plus the module path; an application
 * sets its real id in its own `android { defaultConfig { applicationId = … } }`.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // No `org.jetbrains.kotlin.android`: since AGP 9 the Android plugin carries Kotlin support
        // itself, and applying the standalone plugin on top of it is an error.
        pluginManager.apply(ANDROID_APPLICATION_PLUGIN)
        pluginManager.apply(COMPOSE_COMPILER_PLUGIN)
        QualityConventionPlugin().apply(target)
        configureComposeCompiler()

        val catalog = libs
        val flavors = appFlavors()
        val appDirectory = appDirectory(projectDir)
        val version = readAppVersion(appDirectory, flavors.all)
        val idSuffixes = flavors.nonProduction.associateWith { applicationIdSuffix(it) }
        // Artifacts carry the app's name (`customer-beta-release.aab`), not the module's (`android`).
        extensions.configure<BasePluginExtension> { archivesName.set(appDirectory.name) }

        extensions.configure<ApplicationExtension> {
            configureDefaults(catalog, defaultNamespace(), version)
            configureReleaseBuildType()
            val sharedDebugSigning = configureSharedDebugSigning(rootDir)
            configureEnvironmentFlavors(flavors, version, idSuffixes, sharedDebugSigning)
        }
        configureJavaToolchain(catalog.intVersion("jvm"))
        configureProductionReleaseSigning(flavors.production)
    }
}

/** `apps/<name>` for an `apps/<name>/android` module, the module's own directory otherwise. */
internal fun appDirectory(moduleDirectory: File): File =
    if (moduleDirectory.name == "android") moduleDirectory.parentFile else moduleDirectory

private fun ApplicationExtension.configureDefaults(catalog: VersionCatalog, namespace: String, version: AppVersion) {
    this.namespace = namespace
    compileSdk = catalog.intVersion("android-compile-sdk")
    defaultConfig {
        applicationId = namespace
        minSdk = catalog.intVersion("android-min-sdk")
        targetSdk = catalog.intVersion("android-target-sdk")
        versionName = version.versionName
    }
    compileOptions.targetJvm(catalog.intVersion("jvm"))
    buildFeatures.compose = true
}

private fun ApplicationExtension.configureReleaseBuildType() {
    buildTypes.getByName("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}

private fun ApplicationExtension.configureEnvironmentFlavors(
    flavors: AppFlavors,
    version: AppVersion,
    idSuffixes: Map<String, String>,
    sharedDebugSigning: Boolean,
) {
    flavorDimensions += ENVIRONMENT_DIMENSION
    flavors.all.forEach { flavor ->
        productFlavors.create(flavor) {
            dimension = ENVIRONMENT_DIMENSION
            versionCode = version.versionCodes.getValue(flavor)
            if (flavor == flavors.production) return@create
            applicationIdSuffix = ".${idSuffixes.getValue(flavor)}"
            versionNameSuffix = "-$flavor"
            if (sharedDebugSigning) signingConfig = signingConfigs.getByName(SHARED_DEBUG_SIGNING)
        }
    }
}
