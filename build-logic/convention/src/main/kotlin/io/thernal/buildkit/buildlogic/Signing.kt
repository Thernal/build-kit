package io.thernal.buildkit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
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
 * key is used and nothing fails.
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
 * The production keystore stays outside the repository (`config/signing/release/` is git-ignored).
 * It signs only the production flavor's `release` variant — a single (flavor, build type) pair the
 * flavor and build type DSL blocks cannot address, hence the variant API. Without it the variant
 * builds unsigned, unless [REQUIRE_RELEASE_SIGNING_PROPERTY] says a release is being cut.
 */
internal fun Project.configureProductionReleaseSigning(productionFlavor: String) {
    val directory = rootDir.resolve(RELEASE_SIGNING_DIRECTORY)
    if (!directory.resolve(KEYSTORE_PROPERTIES).isFile) {
        val message = "$path: no ${directory.resolve(KEYSTORE_PROPERTIES)}; ${productionFlavor}Release stays unsigned"
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
        val selector = selector().withBuildType("release").withFlavor(ENVIRONMENT_DIMENSION to productionFlavor)
        onVariants(selector) { variant -> variant.signingConfig.setConfig(release) }
    }
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
