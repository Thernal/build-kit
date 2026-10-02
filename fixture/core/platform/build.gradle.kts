// An Android-only module in a multiplatform repository: android.library first, so compose and
// environment take the Android path (Jetpack Compose, Environment in the Android sources).
import io.thernal.buildkit.buildlogic.nonProductionImplementation

plugins {
    alias(libs.plugins.buildkit.android.library)
    alias(libs.plugins.buildkit.compose)
    alias(libs.plugins.buildkit.environment)
}

dependencies {
    // Present in every flavor but production, the way a debug tool stays out of the release binary.
    nonProductionImplementation(project, projects.fixture.tools.text)
}
