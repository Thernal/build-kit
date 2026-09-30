package io.thernal.buildkit.fixture.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private val graph: FixtureGraph by lazy { createFixtureGraph() }

@Suppress("FunctionNaming") // The name Swift calls; iOS entry points are conventionally capitalised.
fun MainViewController(appName: String): UIViewController {
    return ComposeUIViewController { FixtureApp(graph, appName) }
}
