package io.thernal.buildkit.features.greeting.api.domain

/** The contract other modules ask for; `impl` provides it and `wiring` binds it. */
interface Greeter {
    fun greet(name: String): String
}
