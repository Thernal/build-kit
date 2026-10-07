plugins {
    `kotlin-dsl`
}

group = "io.thernal.buildkit.buildlogic"

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

// compileOnly for every plugin the application's root build declares `apply false`: the plugin is
// already on the consuming build's classpath, and these entries only supply the DSL types the
// conventions configure. A second copy here would load the plugin twice.
dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.dependency.analysis.gradle.plugin)
    // implementation, not compileOnly: Detekt is applied by a convention rather than declared in the
    // application's root build, so it has to travel with build-logic.
    implementation(libs.detekt.gradle.plugin)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "io.thernal.buildkit.kmp.library"
            implementationClass = "io.thernal.buildkit.buildlogic.libraries.KmpLibraryConventionPlugin"
        }
        register("compose") {
            id = "io.thernal.buildkit.compose"
            implementationClass = "io.thernal.buildkit.buildlogic.compose.ComposeConventionPlugin"
        }
        register("injection") {
            id = "io.thernal.buildkit.injection"
            implementationClass = "io.thernal.buildkit.buildlogic.injection.InjectionConventionPlugin"
        }
        register("androidLibrary") {
            id = "io.thernal.buildkit.android.library"
            implementationClass = "io.thernal.buildkit.buildlogic.libraries.AndroidLibraryConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "io.thernal.buildkit.kotlin.library"
            implementationClass = "io.thernal.buildkit.buildlogic.libraries.KotlinLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "io.thernal.buildkit.android.application"
            implementationClass = "io.thernal.buildkit.buildlogic.application.AndroidApplicationConventionPlugin"
        }
        register("environment") {
            id = "io.thernal.buildkit.environment"
            implementationClass = "io.thernal.buildkit.buildlogic.environment.EnvironmentConventionPlugin"
        }
        register("detekt") {
            id = "io.thernal.buildkit.detekt"
            implementationClass = "io.thernal.buildkit.buildlogic.quality.DetektPipelinePlugin"
        }
        register("modules") {
            id = "io.thernal.buildkit.modules"
            implementationClass = "io.thernal.buildkit.buildlogic.modules.ModulesConventionPlugin"
        }
    }
}
