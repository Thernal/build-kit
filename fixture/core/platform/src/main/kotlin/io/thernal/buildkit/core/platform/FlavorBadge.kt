package io.thernal.buildkit.core.platform

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The active flavor, from the Environment generated into this Android library's sources. */
@Composable
fun FlavorBadge(modifier: Modifier = Modifier) {
    BasicText(text = flavorLabel(), modifier = modifier)
}

fun flavorLabel(): String {
    if (Environment.IS_PRODUCTION) {
        return Environment.FLAVOR
    }
    return "${Environment.FLAVOR} (not production)"
}
