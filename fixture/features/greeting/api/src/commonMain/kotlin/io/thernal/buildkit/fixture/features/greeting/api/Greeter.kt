package io.thernal.buildkit.fixture.features.greeting.api

/** The contract other modules ask for; `impl` provides it and `wiring` binds it. */
interface Greeter {
    fun greet(name: String): String
}
