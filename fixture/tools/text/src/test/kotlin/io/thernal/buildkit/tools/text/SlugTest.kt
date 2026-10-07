package io.thernal.buildkit.tools.text

import kotlin.test.Test
import kotlin.test.assertEquals

class SlugTest {
    @Test
    fun `turns text into a slug`() {
        assertEquals("venue-management", slug("  Venue Management! "))
    }
}
