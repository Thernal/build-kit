package io.thernal.buildkit.fixture.features.greeting.wiring

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.thernal.buildkit.fixture.features.greeting.api.domain.Greeter
import io.thernal.buildkit.fixture.features.greeting.impl.domain.EnvironmentGreeter

@BindingContainer
@ContributesTo(AppScope::class)
object GreetingWiring {
    @Provides
    fun greeter(): Greeter {
        return EnvironmentGreeter()
    }
}
