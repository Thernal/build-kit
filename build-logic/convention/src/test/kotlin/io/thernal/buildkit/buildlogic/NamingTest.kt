package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.modules.isApiConfiguration
import io.thernal.buildkit.buildlogic.modules.parseAllowedApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NamingTest {
    @Test
    fun `namespace follows the module path, splitting dashes`() {
        assertEquals("com.example.features.venue.management.impl", namespaceFor("com.example", ":features:venue-management:impl"))
        assertEquals("com.example", namespaceFor("com.example", ":"))
    }

    @Test
    fun `namespace leaves out the modules root`() {
        assertEquals("com.example.features.profile.impl", namespaceFor("com.example", ":fixture:features:profile:impl", listOf("fixture")))
        assertEquals("com.example.core.ui", namespaceFor("com.example", ":code:shared:core:ui", listOf("code", "shared")))
        // A module outside the root keeps its whole path.
        assertEquals("com.example.tools.lint", namespaceFor("com.example", ":tools:lint", listOf("fixture")))
        assertEquals(listOf("code", "shared"), parseModulesRoot(" code/shared "))
        assertEquals(emptyList(), parseModulesRoot(null))
    }

    @Test
    fun `api buckets are recognised in every shape`() {
        listOf("api", "commonMainApi", "iosMainApi", "debugApi", "compileOnlyApi").forEach { assertTrue(isApiConfiguration(it), it) }
        listOf("implementation", "apiElements", "commonMainImplementation", "kapt").forEach { assertFalse(isApiConfiguration(it), it) }
    }

    @Test
    fun `the api allow-list is a comma-separated set`() {
        assertEquals(setOf(":core:firebase", "com.example:bridge"), parseAllowedApi(" :core:firebase, com.example:bridge ,"))
        assertEquals(emptySet(), parseAllowedApi(null))
    }
}
