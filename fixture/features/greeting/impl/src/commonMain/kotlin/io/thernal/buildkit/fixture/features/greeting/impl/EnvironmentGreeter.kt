package io.thernal.buildkit.fixture.features.greeting.impl

import io.thernal.buildkit.fixture.core.config.AppConfig
import io.thernal.buildkit.fixture.features.greeting.api.Greeter

class EnvironmentGreeter(private val flavor: String = AppConfig.flavor) : Greeter {
    override fun greet(name: String): String = "Hello, $name, from $flavor"
}
