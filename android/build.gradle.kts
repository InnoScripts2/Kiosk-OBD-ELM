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

// Arduino Compilation Task (Session 18G → 19G)
tasks.register<Exec>("compileArduino") {
    group = "hardware"
    description = "Compile Arduino sketches for dispenser lock control (requires arduino-cli)"
    workingDir = projectDir
    
    // Parameters (can be overridden via -P flags)
    val dryRun = project.findProperty("arduinoDryRun")?.toString()?.toBoolean() ?: true
    val arduinoCliPath = project.findProperty("arduinoCliPath")?.toString() ?: "arduino-cli"
    val fqbn = project.findProperty("fqbn")?.toString() ?: "arduino:avr:uno"
    val sketchPath = project.findProperty("sketchPath")?.toString() ?: "hardware/arduino/dispenser.ino"
    
    if (dryRun) {
        commandLine("echo", "[DRY-RUN] Would compile Arduino sketches:")
        doLast {
            logger.lifecycle("  arduino-cli: $arduinoCliPath")
            logger.lifecycle("  FQBN: $fqbn")
            logger.lifecycle("  Sketch: $sketchPath")
        }
    } else {
        commandLine(
            arduinoCliPath, "compile",
            "--fqbn", fqbn,
            sketchPath
        )
    }
    
    doFirst {
        if (dryRun) {
            logger.lifecycle("Running Arduino compilation in DRY-RUN mode...")
            logger.lifecycle("To compile real sketches:")
            logger.lifecycle("  ./gradlew compileArduino -ParduinoDryRun=false")
            logger.lifecycle("  ./gradlew compileArduino -ParduinoDryRun=false -Pfqbn=arduino:avr:mega")
            logger.lifecycle("Requires arduino-cli in PATH: https://arduino.github.io/arduino-cli/")
        } else {
            logger.lifecycle("Compiling Arduino sketch: $sketchPath")
            logger.lifecycle("FQBN: $fqbn")
        }
    }
    
    doLast {
        if (!dryRun) {
            logger.lifecycle("Arduino compilation completed. Output in hardware/arduino/build/")
        }
    }
}

// Release Build Preparation Task (Session 20R)
tasks.register("prepareReleaseBuild") {
    group = "build"
    description = "Prepare release build: lint, test, and assemble debug APK"
    
    // Check if device tasks should be skipped
    val skipDeviceTasks = project.findProperty("skipDeviceTasks")?.toString()?.toBoolean() ?: false
    
    doFirst {
        logger.lifecycle("========================================")
        logger.lifecycle("Preparing Release Build")
        logger.lifecycle("========================================")
        if (skipDeviceTasks) {
            logger.lifecycle("⚠️ Device tasks will be skipped (-PskipDeviceTasks=true)")
        }
        logger.lifecycle("")
    }
    
    // Note: Actual dependencies will be set up when subprojects are configured
    // This task serves as an entry point for the release build process
    
    doLast {
        logger.lifecycle("")
        logger.lifecycle("========================================")
        logger.lifecycle("Release Build Preparation Complete")
        logger.lifecycle("========================================")
        logger.lifecycle("Next steps:")
        logger.lifecycle("1. Review lint reports in */build/reports/lint-results*.html")
        logger.lifecycle("2. Check test results in */build/reports/tests/")
        logger.lifecycle("3. Find APK in app/build/outputs/apk/debug/app-debug.apk")
        logger.lifecycle("")
        logger.lifecycle("To skip device-specific tasks, use: -PskipDeviceTasks=true")
    }
}
