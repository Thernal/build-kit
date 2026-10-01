plugins {
    alias(libs.plugins.buildkit.android.application)
}

dependencies {
    implementation(projects.fixture.apps.customer.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.runtime)
}
