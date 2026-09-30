package io.thernal.buildkit.buildlogic

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
