@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.selfservice.obd.core"
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
    implementation(project(":platform-data"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.org.json)
    implementation(libs.androidx.startup)
    implementation(libs.evalex)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
}

// ========================================================================================
// npm Tasks для device-obd-kit
// ========================================================================================

val deviceObdKitDir = file("device-obd-kit")

tasks.register<Exec>("npmInstallDeviceObdKit") {
    group = "device-kit"
    description = "Install npm dependencies for device-obd-kit"
    workingDir = deviceObdKitDir
    commandLine("npm", "install")
    onlyIf { deviceObdKitDir.exists() && file("$deviceObdKitDir/package.json").exists() }
    
    inputs.file("$deviceObdKitDir/package.json")
    outputs.dir("$deviceObdKitDir/node_modules")
}

tasks.register<Exec>("buildDeviceObdKit") {
    group = "device-kit"
    description = "Build TypeScript device-obd-kit (npm run build)"
    dependsOn("npmInstallDeviceObdKit")
    workingDir = deviceObdKitDir
    commandLine("npm", "run", "build")
    onlyIf { deviceObdKitDir.exists() && file("$deviceObdKitDir/package.json").exists() }
    
    inputs.dir("$deviceObdKitDir/src")
    inputs.file("$deviceObdKitDir/tsconfig.json")
    outputs.dir("$deviceObdKitDir/dist")
}

tasks.register<Exec>("testDeviceObdKit") {
    group = "device-kit"
    description = "Run Jest tests for device-obd-kit"
    dependsOn("npmInstallDeviceObdKit")
    workingDir = deviceObdKitDir
    commandLine("npm", "test")
    onlyIf { deviceObdKitDir.exists() && file("$deviceObdKitDir/package.json").exists() }
    
    inputs.dir("$deviceObdKitDir/src")
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("lintDeviceObdKit") {
    group = "device-kit"
    description = "Run linting for device-obd-kit"
    dependsOn("npmInstallDeviceObdKit")
    workingDir = deviceObdKitDir
    commandLine("npm", "run", "lint")
    onlyIf { deviceObdKitDir.exists() && file("$deviceObdKitDir/package.json").exists() }
    
    inputs.dir("$deviceObdKitDir/src")
    outputs.upToDateWhen { false }
}
