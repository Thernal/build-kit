package io.thernal.buildkit.features.greeting.impl.domain

import io.thernal.buildkit.core.config.AppConfig
import io.thernal.buildkit.features.greeting.api.domain.Greeter

class EnvironmentGreeter(
    private val flavor: String = AppConfig.flavor,
) : Greeter {
    override fun greet(name: String): String {
        return "Hello, $name, from $flavor"
    }
}
