package io.thernal.buildkit.buildlogic.application

import java.io.File
import java.util.Properties

internal const val VERSION_PROPERTIES_FILE = "version.properties"
private const val DEFAULT_VERSION_NAME = "0.0.1"
private const val DEFAULT_VERSION_CODE = 1

internal data class AppVersion(val versionName: String, val versionCodes: Map<String, Int>)

/**
 * Reads `apps/<name>/version.properties` — one file for the app, its Android and iOS sides alike:
 * `versionName=…` and one `<flavor>.versionCode=…` per flavor. `versionName` is written by hand; each flavor's code is bumped on its own by
 * `scripts/bump-version-code.sh` right before a distributable build, since stores see each flavor as
 * a separate application. A missing file or key falls back to a default so a fresh checkout builds.
 */
internal fun readAppVersion(appDirectory: File, flavors: List<String>): AppVersion {
    val file = appDirectory.resolve(VERSION_PROPERTIES_FILE)
    val properties = Properties()
    if (file.isFile) file.inputStream().use(properties::load)

    val versionName = properties.getProperty("versionName")?.takeIf(String::isNotBlank)
        ?: DEFAULT_VERSION_NAME
    val versionCodes = flavors.associateWith { flavor ->
        properties.getProperty("$flavor.versionCode")?.trim()?.toIntOrNull() ?: DEFAULT_VERSION_CODE
    }
    return AppVersion(versionName, versionCodes)
}
