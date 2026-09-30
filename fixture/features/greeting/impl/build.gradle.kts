plugins {
    alias(libs.plugins.buildkit.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.fixture.features.greeting.api)
            implementation(projects.fixture.core.config)
        }
    }
}
