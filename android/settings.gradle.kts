includeBuild("build-logic/binary-compatibility")

pluginManagement {

    repositories {
        // Зеркала Google Maven для обхода блокировки dl.google.com
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }

    plugins {
        id("com.google.dagger.hilt.android") version "2.48.1"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Зеркала Google Maven для обхода блокировки dl.google.com
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
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
    ":feature-thickness",
    ":feature-payments",
    ":feature-payment",
    ":feature-reports",
    ":feature-kiosk-mode",
    ":feature-lock-control",
    ":platform-background",
    ":platform-data",
    ":platform-data-supabase",
    ":platform-logging",
    ":platform-bluetooth",
    ":platform-bluetooth-kable",
    ":platform-bluetooth-reaktive",
    ":platform-ui",
    ":platform-ui-flowext"
)
modules.forEach { include(it) }

// Явная настройка директорий для модулей в platform/
project(":platform-data").projectDir = file("platform/data")
project(":platform-data-supabase").projectDir = file("platform/data/supabase")
project(":platform-logging").projectDir = file("platform/logging")
project(":platform-bluetooth").projectDir = file("platform/bluetooth")
project(":platform-bluetooth-kable").projectDir = file("platform/bluetooth/kable-core")
project(":platform-bluetooth-reaktive").projectDir = file("platform/bluetooth/reaktive")
project(":platform-background").projectDir = file("platform/background")
project(":platform-ui").projectDir = file("platform/ui")
project(":platform-ui-flowext").projectDir = file("platform/ui/flowext")
