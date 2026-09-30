package io.thernal.buildkit.fixture.features.greeting.impl.domain

import io.thernal.buildkit.fixture.core.config.AppConfig
import io.thernal.buildkit.fixture.features.greeting.api.domain.Greeter

class EnvironmentGreeter(
    private val flavor: String = AppConfig.flavor,
) : Greeter {
    override fun greet(name: String): String {
        return "Hello, $name, from $flavor"
    }
}
