package io.thernal.buildkit.core.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class FlavorBadgeTest {
    @Test
    fun theLabelNamesTheGeneratedFlavor() {
        assertTrue(flavorLabel().startsWith(Environment.FLAVOR))
    }
}
