package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.flavor.AppFlavors
import io.thernal.buildkit.buildlogic.flavor.flavorFromTaskNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.gradle.api.GradleException

class AppFlavorsTest {
    private val flavors = AppFlavors.parse("regress, dev, beta, prod", "prod", "dev")

    @Test
    fun `parses the list, production and default`() {
        assertEquals(listOf("regress", "dev", "beta", "prod"), flavors.all)
        assertEquals("prod", flavors.production)
        assertEquals("dev", flavors.default)
        assertEquals(listOf("regress", "dev", "beta"), flavors.nonProduction)
    }

    @Test
    fun `production and default fall back to the last and first flavor`() {
        val parsed = AppFlavors.parse("dev,prod", null, null)
        assertEquals("prod", parsed.production)
        assertEquals("dev", parsed.default)
    }

    @Test
    fun `rejects an empty list`() {
        assertFailsWith<GradleException> { AppFlavors.parse(null, null, null) }
        assertFailsWith<GradleException> { AppFlavors.parse(" , ", null, null) }
    }

    @Test
    fun `a single flavor is production and default`() {
        val parsed = AppFlavors.parse("prod", null, null)
        assertEquals(listOf("prod"), parsed.all)
        assertEquals("prod", parsed.production)
        assertEquals("prod", parsed.default)
        assertEquals(emptyList(), parsed.nonProduction)
    }

    @Test
    fun `rejects names Android reserves`() {
        assertFailsWith<GradleException> { AppFlavors.parse("test,prod", null, null) }
        assertFailsWith<GradleException> { AppFlavors.parse("testing,prod", null, null) }
        assertFailsWith<GradleException> { AppFlavors.parse("main,prod", null, null) }
        assertFailsWith<GradleException> { AppFlavors.parse("release,prod", null, null) }
    }

    @Test
    fun `rejects malformed and duplicate names`() {
        assertFailsWith<GradleException> { AppFlavors.parse("Dev,prod", null, null) }
        assertFailsWith<GradleException> { AppFlavors.parse("dev-1,prod", null, null) }
        assertFailsWith<GradleException> { AppFlavors.parse("dev,dev,prod", null, null) }
    }

    @Test
    fun `rejects a production or default flavor outside the list`() {
        assertFailsWith<GradleException> { AppFlavors.parse("dev,prod", "live", null) }
        assertFailsWith<GradleException> { AppFlavors.parse("dev,prod", null, "local") }
    }

    @Test
    fun `reads the flavor from variant and aggregate task names`() {
        assertEquals("beta", flavorFromTaskNames(listOf(":apps:customer:assembleBetaDebug"), flavors))
        assertEquals("regress", flavorFromTaskNames(listOf("testRegressReleaseUnitTest"), flavors))
        assertEquals("prod", flavorFromTaskNames(listOf("bundleProd"), flavors))
        assertEquals("dev", flavorFromTaskNames(listOf("installDev", "clean"), flavors))
    }

    @Test
    fun `task names without a flavor name none`() {
        assertNull(flavorFromTaskNames(listOf("build", "assembleDebug", "check"), flavors))
    }

    @Test
    fun `tasks spanning two flavors fail`() {
        assertFailsWith<GradleException> {
            flavorFromTaskNames(listOf("assembleDevDebug", "assembleProdRelease"), flavors)
        }
    }
}
