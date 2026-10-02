// A single-module Android app (no android/, ios/ and shared/ parts): version.properties sits beside
// this file, and artifacts are named after the module.
plugins {
    alias(libs.plugins.buildkit.android.application)
}

dependencies {
    implementation(projects.fixture.core.platform)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
}
