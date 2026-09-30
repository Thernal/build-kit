package io.thernal.buildkit.fixture.features.greeting.impl

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvironmentGreeterTest {
    @Test
    fun greetsWithTheFlavor() {
        assertEquals("Hello, Ada, from beta", EnvironmentGreeter(flavor = "beta").greet("Ada"))
    }
}
