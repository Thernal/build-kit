package io.thernal.buildkit.fixture.apps.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.thernal.buildkit.fixture.shared.FixtureApp
import io.thernal.buildkit.fixture.shared.createFixtureGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = createFixtureGraph()
        setContent { FixtureApp(graph, appName = "Customer") }
    }
}
