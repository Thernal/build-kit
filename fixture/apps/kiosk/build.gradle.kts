// A single-module Android app (no android/, ios/ and shared/ parts): version.properties sits beside
// this file, and artifacts are named after the module.
plugins {
    alias(libs.plugins.buildkit.android.application)
    // Changes nothing on an application, which has Compose already; an app may name it all the same.
    alias(libs.plugins.buildkit.compose)
}

dependencies {
    implementation(projects.fixture.core.platform)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
