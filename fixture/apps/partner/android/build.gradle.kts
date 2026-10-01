plugins {
    alias(libs.plugins.buildkit.android.application)
}

dependencies {
    implementation(projects.fixture.apps.partner.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
}
