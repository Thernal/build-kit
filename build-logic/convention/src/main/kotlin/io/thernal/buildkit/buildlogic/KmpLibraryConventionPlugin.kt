package io.thernal.buildkit.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * The baseline every multiplatform module shares: the Kotlin Multiplatform plugin, the target set
 * (Android, iosArm64, iosSimulatorArm64), the Android namespace, and the test dependencies.
 * Compose, injection and the environment are separate conventions, so a module names only the
 * capabilities it uses.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        if (isAndroidOnly()) {
            throw GradleException(
                "$path applies io.thernal.buildkit.kmp.library, but $PLATFORMS_PROPERTY=android: an Android-only " +
                    "application has no multiplatform modules. Apply io.thernal.buildkit.android.library instead.",
            )
        }
        pluginManager.apply(KOTLIN_MULTIPLATFORM_PLUGIN)
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        QualityConventionPlugin().apply(target)

        val catalog = libs
        val namespace = defaultNamespace()

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(catalog.version("jvm").toInt())

            compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }

            targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                this.namespace = namespace
                compileSdk = catalog.version("android-compile-sdk").toInt()
                minSdk = catalog.version("android-min-sdk").toInt()
                // Gives commonTest somewhere to run on the JVM: the Android target's host tests.
                withHostTest {}
            }
            // No iosX64: Intel simulators are gone from current Xcode, and several AndroidX
            // multiplatform artifacts (navigation3-ui among them) no longer publish it.
            iosArm64()
            iosSimulatorArm64()

            applyDefaultHierarchyTemplate()

            // A module that declares an iOS framework gets a name derived from its path and a static
            // binary, so two frameworks never collide; the module can still override both.
            val frameworkName = path.removePrefix(":").split(':', '-')
                .joinToString("") { segment -> segment.replaceFirstChar(Char::uppercaseChar) }
            targets.withType<KotlinNativeTarget>().configureEach {
                binaries.withType<Framework>().configureEach {
                    baseName = frameworkName
                    isStatic = true
                    binaryOption("bundleId", namespace)
                }
            }

            sourceSets.named("commonTest") {
                dependencies {
                    implementation(catalog.library("kotlin-test"))
                    implementation(catalog.library("kotlinx-coroutines-test"))
                }
            }
        }
    }
}
