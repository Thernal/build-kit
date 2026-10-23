package io.thernal.buildkit.buildlogic.application

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException

internal const val VERSION_PROPERTIES_FILE = "version.properties"

/** `-Papp.versionName=2.4.0`: a release's version name, over `version.properties` (the store lanes pass it). */
internal const val VERSION_NAME_PROPERTY = "app.versionName"

/** `-Papp.versionCode=84`: a release's build number, over the flavor's code in `version.properties`. */
internal const val VERSION_CODE_PROPERTY = "app.versionCode"

private const val DEFAULT_VERSION_NAME = "0.0.1"
private const val DEFAULT_VERSION_CODE = 1

/** Google Play's ceiling for a version code. */
private const val MAX_VERSION_CODE = 2_100_000_000

/**
 * [ignoredKeys]: `<flavor>.versionCode` keys of flavors that count with another flavor's code, which the
 * build never reads.
 */
internal data class AppVersion(
    val versionName: String,
    val versionCodes: Map<String, Int>,
    val ignoredKeys: List<String> = emptyList(),
)

/**
 * Reads `apps/<name>/version.properties` — one file for the app, its Android and iOS sides alike:
 * `versionName=…` and one `<flavor>.versionCode=…` per flavor. `versionName` is written by hand; each flavor's code is bumped on its own by
 * `scripts/bump-version-code.sh` right before a distributable build, since stores see each flavor as
 * a separate application. A flavor on production's application id is not one: [counterFlavor] maps
 * it to the production flavor, whose code it shares. A missing file or key falls back to a default so a
 * fresh checkout builds.
 */
internal fun readAppVersion(
    appDirectory: File,
    flavors: List<String>,
    counterFlavor: (String) -> String = { it },
): AppVersion {
    val file = appDirectory.resolve(VERSION_PROPERTIES_FILE)
    val properties = Properties()
    if (file.isFile) file.inputStream().use(properties::load)

    val versionName = properties.getProperty("versionName")?.takeIf(String::isNotBlank)
        ?: DEFAULT_VERSION_NAME
    val versionCodes = flavors.associateWith { flavor ->
        properties.getProperty("${counterFlavor(flavor)}.versionCode")?.trim()?.toIntOrNull() ?: DEFAULT_VERSION_CODE
    }
    val ignoredKeys = flavors
        .filter { counterFlavor(it) != it }
        .map { "$it.versionCode" }
        .filter(properties::containsKey)
    return AppVersion(versionName, versionCodes, ignoredKeys)
}

/**
 * A release cut by the store lanes takes its version from the pipeline, not from the file: a version
 * name and a build number above everything either store has, for the one flavor the invocation builds.
 */
internal fun AppVersion.overriddenBy(versionName: String?, versionCode: String?): AppVersion {
    val name = versionName?.trim()?.takeIf(String::isNotEmpty)?.also { name ->
        if (name.any(Char::isWhitespace)) {
            throw GradleException("-P$VERSION_NAME_PROPERTY='$name' must not contain spaces (e.g. 2.4.0).")
        }
    }
    val code = versionCode?.trim()?.takeIf(String::isNotEmpty)?.let { raw ->
        raw.toIntOrNull()?.takeIf { it in 1..MAX_VERSION_CODE }
            ?: throw GradleException(
                "-P$VERSION_CODE_PROPERTY='$raw' must be a whole number from 1 to $MAX_VERSION_CODE.",
            )
    }
    return copy(
        versionName = name ?: this.versionName,
        versionCodes = if (code == null) versionCodes else versionCodes.mapValues { code },
    )
}
