plugins {
    alias(libs.plugins.buildkit.kmp.library)
    alias(libs.plugins.buildkit.injection)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.fixture.features.greeting.api)
            implementation(projects.fixture.features.greeting.impl)
        }
    }
}
