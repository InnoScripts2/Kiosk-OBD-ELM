buildscript {
    repositories {
        // Используем те же зеркала, что и в settings.gradle.kts
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48.1")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// DevOps and Maintenance Tasks (Session 17A)
tasks.register<Exec>("runKioskMaintenance") {
    group = "maintenance"
    description = "Run kiosk maintenance utility (LogRotation, CollectMetrics, ValidateLogs, CleanupArtifacts, All)"
    workingDir = projectDir
    commandLine("pwsh", "-File", "scripts/powershell/maintenance/kiosk-maintenance.ps1", "-Task", "All", "-DryRun")
    
    doFirst {
        logger.lifecycle("Running kiosk maintenance tasks in DryRun mode...")
        logger.lifecycle("To execute real changes, edit the task and remove -DryRun parameter")
    }
}

tasks.register<Exec>("runLogRotation") {
    group = "maintenance"
    description = "Rotate and archive old logs (default: 90 days retention)"
    workingDir = projectDir
    commandLine("pwsh", "-File", "scripts/powershell/maintenance/log-rotation.ps1", "-DryRun")
    
    doFirst {
        logger.lifecycle("Running log rotation in DryRun mode...")
        logger.lifecycle("To execute real changes, edit the task and remove -DryRun parameter")
    }
}

tasks.register<Exec>("checkMavenAccess") {
    group = "verification"
    description = "Check Maven repository accessibility (Google Maven, Maven Central, etc.)"
    workingDir = projectDir
    commandLine("bash", "scripts/shell/check-maven-access.sh")
    
    doFirst {
        logger.lifecycle("Checking Maven repository accessibility...")
    }
}
