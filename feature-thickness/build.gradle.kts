@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.selfservice.thickness"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core"))
    api(project(":platform-bluetooth"))
    implementation(project(":platform-logging"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
}

// ========================================================================================
// npm Tasks для device-thickness-kit
// ========================================================================================

val thicknessKitDir = file("device-thickness-kit")

tasks.register<Exec>("npmInstallThicknessKit") {
    group = "device-kit"
    description = "Install npm dependencies for device-thickness-kit"
    workingDir = thicknessKitDir
    commandLine("npm", "install")
    onlyIf { thicknessKitDir.exists() && file("$thicknessKitDir/package.json").exists() }
    
    inputs.file("$thicknessKitDir/package.json")
    outputs.dir("$thicknessKitDir/node_modules")
}

tasks.register<Exec>("buildThicknessKit") {
    group = "device-kit"
    description = "Build TypeScript device-thickness-kit (npm run build)"
    dependsOn("npmInstallThicknessKit")
    workingDir = thicknessKitDir
    commandLine("npm", "run", "build")
    onlyIf { thicknessKitDir.exists() && file("$thicknessKitDir/package.json").exists() }
    
    inputs.dir("$thicknessKitDir/src")
    inputs.file("$thicknessKitDir/tsconfig.json")
    outputs.dir("$thicknessKitDir/dist")
}

tasks.register<Exec>("testThicknessKit") {
    group = "device-kit"
    description = "Run Jest tests for device-thickness-kit"
    dependsOn("npmInstallThicknessKit")
    workingDir = thicknessKitDir
    commandLine("npm", "test")
    onlyIf { thicknessKitDir.exists() && file("$thicknessKitDir/package.json").exists() }
    
    inputs.dir("$thicknessKitDir/src")
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("lintThicknessKit") {
    group = "device-kit"
    description = "Run linting for device-thickness-kit"
    dependsOn("npmInstallThicknessKit")
    workingDir = thicknessKitDir
    commandLine("npm", "run", "lint")
    onlyIf { thicknessKitDir.exists() && file("$thicknessKitDir/package.json").exists() }
    
    inputs.dir("$thicknessKitDir/src")
    outputs.upToDateWhen { false }
}
