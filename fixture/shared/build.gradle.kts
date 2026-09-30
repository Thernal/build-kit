import io.thernal.buildkit.buildlogic.nonProductionImplementation

plugins {
    alias(libs.plugins.buildkit.compose)
    alias(libs.plugins.buildkit.injection)
}

kotlin {
    // The framework the iOS application embeds; build-kit names it `FixtureShared` and makes it static.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target -> target.binaries.framework {} }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.fixture.core.config)
            implementation(projects.fixture.features.greeting.api)
            implementation(projects.fixture.features.greeting.impl)
            implementation(projects.fixture.features.greeting.wiring)
            // Resolved per invocation: absent from a production build's classpath altogether.
            nonProductionImplementation(projects.fixture.core.diagnostics)
        }
    }
}
