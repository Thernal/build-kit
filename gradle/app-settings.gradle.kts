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
run {
    fun property(name: String): String? = providers.gradleProperty(name).orNull?.trim()?.takeIf(String::isNotEmpty)
    val root = property("app.modules.root")?.let(settingsDir::resolve) ?: settingsDir
    val areas = (property("app.modules.areas") ?: "apps,core,designsystem,features").split(',').map(String::trim)

    areas.map(root::resolve).filter(File::exists).forEach { area ->
        area.walkTopDown()
            .onEnter { directory -> directory.name != "build" && !directory.name.startsWith(".") }
            .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
            .forEach { moduleDirectory ->
                include(":" + moduleDirectory.relativeTo(settingsDir).invariantSeparatorsPath.replace('/', ':'))
            }
    }
}
