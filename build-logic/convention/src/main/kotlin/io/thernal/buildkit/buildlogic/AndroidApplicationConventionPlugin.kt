package io.thernal.buildkit.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

/**
 * The Android side of an app — `apps/<name>/android`, one per app a repository ships, beside the
 * app's `ios/` (the Xcode project) and `shared/` (its Kotlin Multiplatform root, which both embed).
 *
 * Every application carries the same `environment` flavor dimension, built from `app.flavors`:
 * non-production flavors get an application id suffix (`.dev`), a version name suffix (`-dev`) and
 * the shared debug signature, so they install beside production; the production flavor gets release
 * signing. Version codes come per flavor from the app's `version.properties` (`apps/<name>/`).
 *
 * The namespace and application id default to `app.namespace` plus the module path; an application
 * sets its real id in its own `android { defaultConfig { applicationId = … } }`.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        // No `org.jetbrains.kotlin.android`: since AGP 9 the Android plugin carries Kotlin support
        // itself, and applying the standalone plugin on top of it is an error.
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        QualityConventionPlugin().apply(target)
        configureComposeCompiler()

        val catalog = libs
        val jvm = catalog.version("jvm").toInt()
        val flavors = appFlavors()
        // `apps/<name>/android`: the version is the app's, shared with `apps/<name>/ios`.
        val version = readAppVersion(projectDir.parentFile, flavors.all)
        // Artifacts carry the app's name (`customer-beta-release.aab`), not the module's (`android`).
        extensions.configure<BasePluginExtension> { archivesName.set(projectDir.parentFile.name) }

        extensions.configure<ApplicationExtension> {
            namespace = defaultNamespace()
            compileSdk = catalog.version("android-compile-sdk").toInt()

            defaultConfig {
                applicationId = defaultNamespace()
                minSdk = catalog.version("android-min-sdk").toInt()
                targetSdk = catalog.version("android-target-sdk").toInt()
                versionName = version.versionName
            }

            compileOptions {
                sourceCompatibility = JavaVersion.toVersion(jvm)
                targetCompatibility = JavaVersion.toVersion(jvm)
            }

            buildFeatures {
                compose = true
            }

            buildTypes {
                getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }

            val sharedDebugSigning = configureSharedDebugSigning(rootDir)

            flavorDimensions += ENVIRONMENT_DIMENSION
            productFlavors {
                flavors.all.forEach { flavor ->
                    create(flavor) {
                        dimension = ENVIRONMENT_DIMENSION
                        versionCode = version.versionCodes.getValue(flavor)
                        if (flavor != flavors.production) {
                            applicationIdSuffix = ".$flavor"
                            versionNameSuffix = "-$flavor"
                            if (sharedDebugSigning) signingConfig = signingConfigs.getByName(SHARED_DEBUG_SIGNING)
                        }
                    }
                }
            }
        }

        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(jvm))
        }

        configureProductionReleaseSigning(flavors.production)
    }
}
