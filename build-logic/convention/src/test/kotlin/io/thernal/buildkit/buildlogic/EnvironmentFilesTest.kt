package io.thernal.buildkit.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentFilesTest {
    @Test
    fun `parses keys and skips comments, blanks and malformed lines`() {
        val invalid = mutableListOf<String>()
        val parsed = parseEnvironment(
            """
            # comment
            BASE_URL = https://api.example.com/v1?a=b

            lowercase=1
            NO_EQUALS
            FLAVOR=shadowed
            """.trimIndent(),
            invalid::add,
        )
        assertEquals(mapOf("BASE_URL" to "https://api.example.com/v1?a=b"), parsed)
        assertEquals(listOf("lowercase=1", "NO_EQUALS", "FLAVOR=shadowed"), invalid)
    }

    @Test
    fun `generates escaped constants, sorted, after the flavor`() {
        val source = environmentSource(
            packageName = "com.example.config",
            objectName = "Environment",
            flavor = "dev",
            isProduction = false,
            fields = mapOf("Z_KEY" to "a\"b\$c\\d", "A_KEY" to "x"),
        )
        assertTrue("package com.example.config" in source)
        assertTrue("public const val FLAVOR: String = \"dev\"" in source)
        assertTrue("public const val IS_PRODUCTION: Boolean = false" in source)
        assertTrue("public const val Z_KEY: String = \"a\\\"b\\\$c\\\\d\"" in source)
        assertTrue(source.indexOf("A_KEY") < source.indexOf("Z_KEY"))
    }
}
