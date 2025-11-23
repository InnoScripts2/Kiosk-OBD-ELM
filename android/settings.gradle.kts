pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "android-monorepo"

val modules = listOf(
    ":app",
    ":core",
    ":feature-obd-diagnostics",
    ":feature-obd-core",
    ":feature-obd-elm-port",
    ":feature-obd-ui",
    ":feature-payments",
    ":feature-reports",
    ":platform-data",
    ":platform-logging"
)
modules.forEach { include(it) }

project(":platform-data").projectDir = file("platform/data")
project(":platform-logging").projectDir = file("platform/logging")
