// build-kit's part of settings.gradle.kts, applied from there with
//   apply(from = "gradle/app-settings.gradle.kts")
// so it is copied and updated with the kit instead of being pasted into each application.

// `./gradlew create profile api impl` — Gradle would read `profile`, `api` and `impl` as task names.
// Everything after `create` is handed to the task as its arguments instead. It has to happen here:
// settings are evaluated before task names are resolved.
run {
    val taskNames = startParameter.taskNames
    val createIndex = taskNames.indexOfFirst { it == "create" || it == ":create" }
    if (createIndex < 0) return@run
    startParameter.setTaskNames(taskNames.take(createIndex + 1))
    System.setProperty("app.create.arguments", taskNames.drop(createIndex + 1).joinToString(" "))
}

// build-logic supplies the convention plugins and, as a dependency, the custom Detekt rules — only a
// top-level included build substitutes dependencies, so it is included here, not in pluginManagement.
includeBuild("build-logic")

// Every directory with a build file under an area in `app.modules.areas` (below `app.modules.root`)
// is a module. Adding one is creating the directory; there is no list to keep in sync with the tree.
// The walk is a ValueSource: the configuration cache runs it again on every build and is dropped when
// a module directory appears or goes, instead of reusing the old list of modules.
abstract class ModuleDirectories : ValueSource<List<String>, ModuleDirectories.Parameters> {
    interface Parameters : ValueSourceParameters {
        val settingsDirectory: Property<String>
        val modulesRoot: Property<String>
        val areas: ListProperty<String>
    }

    override fun obtain(): List<String> {
        val settingsDirectory = File(parameters.settingsDirectory.get())
        val modulesRoot = settingsDirectory.resolve(parameters.modulesRoot.get())
        return parameters.areas.get().map(modulesRoot::resolve).filter(File::exists).flatMap { area ->
            area.walkTopDown()
                .onEnter { directory -> directory.name != "build" && !directory.name.startsWith(".") }
                .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
                .map { moduleDirectory -> ":" + moduleDirectory.relativeTo(settingsDirectory).invariantSeparatorsPath.replace('/', ':') }
                .toList()
        }.sorted()
    }
}

run {
    fun property(name: String): String? = providers.gradleProperty(name).orNull?.trim()?.takeIf(String::isNotEmpty)
    val areas = (property("app.modules.areas") ?: "apps,core,designsystem,features").split(',').map(String::trim)
    val modules = providers.of(ModuleDirectories::class.java) {
        parameters.settingsDirectory.set(settingsDir.path)
        parameters.modulesRoot.set(property("app.modules.root").orEmpty())
        parameters.areas.set(areas)
    }
    modules.get().forEach { modulePath -> include(modulePath) }
}
