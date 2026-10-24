package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.application.AppVersion
import io.thernal.buildkit.buildlogic.application.overriddenBy
import io.thernal.buildkit.buildlogic.application.readAppVersion
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.gradle.api.GradleException

class AppVersionTest {
    private val flavors = listOf("dev", "beta", "prod")
    private val version = AppVersion("1.2.0", mapOf("dev" to 7, "beta" to 3, "prod" to 5))

    @Test
    fun `reads the name and each flavor's code, with defaults for missing keys`() {
        val directory = appDirectory("versionName=1.2.0\ndev.versionCode=7\nprod.versionCode=5\n")
        val read = readAppVersion(directory, flavors)
        assertEquals("1.2.0", read.versionName)
        assertEquals(mapOf("dev" to 7, "beta" to 1, "prod" to 5), read.versionCodes)
        assertEquals(emptyList(), read.ignoredKeys)
    }

    @Test
    fun `a flavor on production's id counts with production's code`() {
        val directory = appDirectory("versionName=1.2.0\nbeta.versionCode=3\nprod.versionCode=5\n")
        val read = readAppVersion(directory, flavors) { if (it == "beta") "prod" else it }
        assertEquals(5, read.versionCodes.getValue("beta"))
        assertEquals(listOf("beta.versionCode"), read.ignoredKeys)
    }

    @Test
    fun `a missing file builds with the defaults`() {
        val read = readAppVersion(Files.createTempDirectory("app").toFile(), flavors)
        assertEquals("0.0.1", read.versionName)
        assertEquals(setOf(1), read.versionCodes.values.toSet())
    }

    @Test
    fun `the override replaces the name and every flavor's code`() {
        val overridden = version.overriddenBy(versionName = " 2.4.0 ", versionCode = "84")
        assertEquals("2.4.0", overridden.versionName)
        assertEquals(mapOf("dev" to 84, "beta" to 84, "prod" to 84), overridden.versionCodes)
    }

    @Test
    fun `no or blank override keeps the file's values`() {
        assertEquals(version, version.overriddenBy(versionName = null, versionCode = null))
        assertEquals(version, version.overriddenBy(versionName = " ", versionCode = ""))
    }

    @Test
    fun `rejects a malformed override`() {
        assertFailsWith<GradleException> { version.overriddenBy(versionName = "2.4 beta", versionCode = null) }
        assertFailsWith<GradleException> { version.overriddenBy(versionName = null, versionCode = "84a") }
        assertFailsWith<GradleException> { version.overriddenBy(versionName = null, versionCode = "0") }
        assertFailsWith<GradleException> { version.overriddenBy(versionName = null, versionCode = "2100000001") }
    }

    private fun appDirectory(versionProperties: String) = Files.createTempDirectory("app").toFile().also {
        it.resolve("version.properties").writeText(versionProperties)
    }
}
