package io.thernal.buildkit.fixture.tools.text

import kotlin.test.Test
import kotlin.test.assertEquals

class SlugTest {
    @Test
    fun slugs() {
        assertEquals("venue-management", slug("  Venue Management! "))
    }
}
