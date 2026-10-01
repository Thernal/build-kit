package io.thernal.buildkit.fixture.apps.partner.shared

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph
import io.thernal.buildkit.fixture.features.greeting.api.domain.Greeter

@DependencyGraph(AppScope::class)
interface FixtureGraph {
    val greeter: Greeter
}

fun createFixtureGraph(): FixtureGraph {
    return createGraph<FixtureGraph>()
}
