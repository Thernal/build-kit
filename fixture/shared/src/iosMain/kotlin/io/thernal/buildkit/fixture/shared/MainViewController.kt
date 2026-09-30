package io.thernal.buildkit.fixture.shared

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

private val graph: FixtureGraph by lazy { createFixtureGraph() }

fun MainViewController(appName: String): UIViewController = ComposeUIViewController { FixtureApp(graph, appName) }
