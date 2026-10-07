package io.thernal.buildkit.apps.customer.shared

import androidx.compose.runtime.Composable
import io.thernal.buildkit.features.greeting.impl.presentation.GreetingScreen

@Composable
fun FixtureApp(
    graph: FixtureGraph,
    appName: String,
) {
    GreetingScreen(greeter = graph.greeter, name = appName)
}
