package io.thernal.buildkit.fixture.features.greeting.impl

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import io.thernal.buildkit.fixture.features.greeting.api.Greeter

@Composable
fun GreetingScreen(greeter: Greeter, name: String) {
    BasicText(text = greeter.greet(name))
}
