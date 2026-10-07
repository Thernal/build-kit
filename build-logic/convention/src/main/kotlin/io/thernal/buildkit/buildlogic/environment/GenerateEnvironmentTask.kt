package io.thernal.buildkit.buildlogic.environment

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateEnvironmentTask : DefaultTask() {
    @get:Input abstract val flavor: Property<String>
    @get:Input abstract val production: Property<Boolean>
    @get:Input abstract val fields: MapProperty<String, String>
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val objectName: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val packageName = packageName.get()
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        val file = directory.resolve(packageName.replace('.', '/')).resolve("${objectName.get()}.kt")
        file.parentFile.mkdirs()
        file.writeText(environmentSource(packageName, objectName.get(), flavor.get(), production.get(), fields.get()))
    }
}
