package io.thernal.buildkit.buildlogic

import com.android.build.api.dsl.CompileOptions
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

/** The catalog's `jvm` version as the Java toolchain of a module compiled by the Android plugin. */
internal fun Project.configureJavaToolchain(jvm: Int) {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(jvm))
    }
}

internal fun CompileOptions.targetJvm(jvm: Int) {
    sourceCompatibility = JavaVersion.toVersion(jvm)
    targetCompatibility = JavaVersion.toVersion(jvm)
}
