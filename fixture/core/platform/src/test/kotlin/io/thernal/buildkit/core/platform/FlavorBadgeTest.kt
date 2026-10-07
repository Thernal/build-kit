package io.thernal.buildkit.core.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class FlavorBadgeTest {
    @Test
    fun `the label names the generated flavor`() {
        assertTrue(flavorLabel().startsWith(Environment.FLAVOR))
    }
}
