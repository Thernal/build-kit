package io.thernal.buildkit.features.greeting.impl.presentation

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import io.thernal.buildkit.features.greeting.api.domain.Greeter

@Composable
fun GreetingScreen(
    greeter: Greeter,
    name: String,
) {
    BasicText(text = greeter.greet(name))
}
