package io.thernal.buildkit.buildlogic.application

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import io.thernal.buildkit.buildlogic.flavor.ENVIRONMENT_DIMENSION
import java.io.File
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

internal const val DEBUG_SIGNING_DIRECTORY = "config/signing/debug"
internal const val RELEASE_SIGNING_DIRECTORY = "config/signing/release"
private const val KEYSTORE_PROPERTIES = "keystore.properties"
internal const val SHARED_DEBUG_SIGNING = "sharedDebug"
private const val PRODUCTION_RELEASE_SIGNING = "productionRelease"

/** `-PrequireReleaseSigning=true` turns a missing release keystore from a skip into a failure (CI). */
private const val REQUIRE_RELEASE_SIGNING_PROPERTY = "requireReleaseSigning"

private val REQUIRED_KEYS = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

/**
 * A debug keystore shared by the whole team, committed under `config/signing/debug/`, so every
 * machine produces the same debug signature (Google sign-in, Firebase and app links all pin it). It
 * signs `debug` and every non-production release. Without the directory, AGP's per-machine debug
 * key signs both, so a tester's build always installs and nothing fails.
 */
internal fun ApplicationExtension.configureSharedDebugSigning(rootDirectory: File): Boolean {
    val directory = rootDirectory.resolve(DEBUG_SIGNING_DIRECTORY)
    if (!directory.isDirectory) return false

    val sharedDebug = signingConfigs.maybeCreate(SHARED_DEBUG_SIGNING)
    sharedDebug.loadFrom(directory)
    buildTypes.getByName("debug").signingConfig = sharedDebug
    return true
}

/**
 * The production keystore stays outside the repository (`config/signing/release/` is git-ignored):
 * the app's own in `config/signing/release/<app>/`, else the shared one in `config/signing/release/`
 * ([releaseSigningDirectory]). It signs only the `release` variant of [flavors] — the production
 * flavor and those sharing its application id — (flavor, build type) pairs the flavor and build type
 * DSL blocks cannot address, hence the variant API. Without it those variants build unsigned, unless
 * [REQUIRE_RELEASE_SIGNING_PROPERTY] says a release is being cut.
 */
internal fun Project.configureProductionReleaseSigning(flavors: List<String>, appName: String) {
    val directory = releaseSigningDirectory(rootDir, appName)
    if (directory == null) {
        val variants = flavors.joinToString { "${it}Release" }
        val shared = rootDir.resolve(RELEASE_SIGNING_DIRECTORY)
        val message = "$path: no $KEYSTORE_PROPERTIES in ${shared.resolve(appName)} or $shared; " +
            "$variants stays unsigned"
        if (providers.gradleProperty(REQUIRE_RELEASE_SIGNING_PROPERTY).orNull == "true") {
            throw GradleException(message)
        }
        logger.info(message)
        return
    }

    val android = extensions.getByType<ApplicationExtension>()
    val release = android.signingConfigs.maybeCreate(PRODUCTION_RELEASE_SIGNING)
    release.loadFrom(directory)

    extensions.configure<ApplicationAndroidComponentsExtension> {
        flavors.forEach { flavor ->
            val selector = selector().withBuildType("release").withFlavor(ENVIRONMENT_DIMENSION to flavor)
            onVariants(selector) { variant -> variant.signingConfig.setConfig(release) }
        }
    }
}

/**
 * Where an app's production keystore is: `config/signing/release/<app>/` when it holds one — a
 * repository that ships several apps signs each with its own key — else `config/signing/release/`,
 * shared by every app; null when neither has a `keystore.properties`.
 */
internal fun releaseSigningDirectory(rootDirectory: File, appName: String): File? {
    val shared = rootDirectory.resolve(RELEASE_SIGNING_DIRECTORY)
    return listOf(shared.resolve(appName), shared).firstOrNull { it.resolve(KEYSTORE_PROPERTIES).isFile }
}

private fun ApkSigningConfig.loadFrom(directory: File) {
    val propertiesFile = directory.resolve(KEYSTORE_PROPERTIES)
    if (!propertiesFile.isFile) throw GradleException("Missing signing properties: $propertiesFile")

    val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
    val missing = REQUIRED_KEYS.filter { properties.getProperty(it).isNullOrBlank() }
    if (missing.isNotEmpty()) throw GradleException("Missing ${missing.joinToString()} in $propertiesFile")

    val keystore = directory.resolve(properties.getProperty("storeFile").trim())
    if (!keystore.isFile) throw GradleException("Missing keystore: $keystore")

    storeFile = keystore
    storePassword = properties.getProperty("storePassword").trim()
    keyAlias = properties.getProperty("keyAlias").trim()
    keyPassword = properties.getProperty("keyPassword").trim()
}
