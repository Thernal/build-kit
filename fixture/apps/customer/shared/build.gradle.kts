import io.thernal.buildkit.buildlogic.nonProductionImplementation

plugins {
    alias(libs.plugins.buildkit.compose)
    alias(libs.plugins.buildkit.injection)
}

kotlin {
    // The framework customer's iOS application (../ios) embeds; build-kit names it
    // `FixtureAppsCustomerShared` and makes it static.
    // `Greeter` is exported so Swift can implement or call it — the one case `api(...)` is allowed,
    // through `app.api.allowed` in gradle.properties.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework { export(projects.fixture.features.greeting.api) }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.fixture.features.greeting.api)
            implementation(projects.fixture.features.greeting.impl)
            implementation(projects.fixture.features.greeting.wiring)
            // Resolved per invocation: absent from a production build's classpath altogether.
            nonProductionImplementation(projects.fixture.core.diagnostics)
        }
    }
}
