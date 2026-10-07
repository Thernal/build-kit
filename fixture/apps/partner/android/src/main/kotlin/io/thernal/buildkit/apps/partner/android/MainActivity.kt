package io.thernal.buildkit.apps.partner.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.thernal.buildkit.apps.partner.shared.FixtureApp
import io.thernal.buildkit.apps.partner.shared.createFixtureGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = createFixtureGraph()
        setContent { FixtureApp(graph, appName = "Partner") }
    }
}
