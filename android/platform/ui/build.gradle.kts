@file:Suppress("DSL_SCOPE_VIOLATION")

// ========================================================================================
// Platform UI Module — Android + TypeScript/Node.js Integration
// ========================================================================================
// Этот модуль содержит:
// 1. Android Compose UI компоненты (src/)
// 2. TypeScript/Node.js агенты (web/)

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.selfservice.platform.ui"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
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
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    testImplementation(libs.kotlin.test)
}

// Путь к Node-агентам внутри модуля
val webDir = file("web")
val agentDir = file("web/agent")
val kioskAgentDir = file("web/kiosk-agent")

// ========================================================================================
// npm Tasks для web/agent
// ========================================================================================

tasks.register<Exec>("npmInstallAgent") {
    group = "web"
    description = "Install npm dependencies for web/agent"
    workingDir = agentDir
    commandLine("npm", "install")
    
    inputs.file("$agentDir/package.json")
    inputs.file("$agentDir/package-lock.json")
    outputs.dir("$agentDir/node_modules")
}

tasks.register<Exec>("buildWebAgent") {
    group = "web"
    description = "Build TypeScript agent (npm run build)"
    dependsOn("npmInstallAgent")
    workingDir = agentDir
    commandLine("npm", "run", "build")
    
    inputs.dir("$agentDir/src")
    inputs.file("$agentDir/tsconfig.json")
    outputs.dir("$agentDir/dist")
}

tasks.register<Exec>("testWebAgent") {
    group = "web"
    description = "Run Jest tests for web/agent"
    dependsOn("npmInstallAgent")
    workingDir = agentDir
    commandLine("npm", "test")
    
    inputs.dir("$agentDir/src")
    outputs.upToDateWhen { false } // Всегда выполнять тесты
}

tasks.register<Exec>("lintWebAgent") {
    group = "web"
    description = "Run ESLint for web/agent"
    dependsOn("npmInstallAgent")
    workingDir = agentDir
    commandLine("npm", "run", "lint")
    
    inputs.dir("$agentDir/src")
    outputs.upToDateWhen { false }
}

tasks.register<Delete>("cleanWebAgent") {
    group = "web"
    description = "Clean dist and node_modules for web/agent"
    delete("$agentDir/dist", "$agentDir/node_modules")
}

// ========================================================================================
// npm Tasks для web/kiosk-agent (опционально, если package.json существует)
// ========================================================================================

tasks.register<Exec>("npmInstallKioskAgent") {
    group = "web"
    description = "Install npm dependencies for web/kiosk-agent"
    workingDir = kioskAgentDir
    commandLine("npm", "install")
    
    // Выполнять только если package.json существует
    onlyIf { file("$kioskAgentDir/package.json").exists() }
    
    inputs.file("$kioskAgentDir/package.json")
    outputs.dir("$kioskAgentDir/node_modules")
}

tasks.register<Exec>("buildKioskAgent") {
    group = "web"
    description = "Build TypeScript kiosk-agent (npm run build)"
    dependsOn("npmInstallKioskAgent")
    workingDir = kioskAgentDir
    commandLine("npm", "run", "build")
    
    onlyIf { file("$kioskAgentDir/package.json").exists() }
    
    inputs.dir("$kioskAgentDir/src")
    outputs.dir("$kioskAgentDir/dist")
}

tasks.register<Exec>("testKioskAgent") {
    group = "web"
    description = "Run tests for web/kiosk-agent"
    dependsOn("npmInstallKioskAgent")
    workingDir = kioskAgentDir
    commandLine("npm", "test")
    
    onlyIf { file("$kioskAgentDir/package.json").exists() }
    
    inputs.dir("$kioskAgentDir/src")
    outputs.upToDateWhen { false }
}

// ========================================================================================
// Агрегатные таски
// ========================================================================================

tasks.register("buildAllWeb") {
    group = "web"
    description = "Build all web components (agent + kiosk-agent)"
    dependsOn("buildWebAgent", "buildKioskAgent")
}

tasks.register("testAllWeb") {
    group = "web"
    description = "Test all web components"
    dependsOn("testWebAgent", "testKioskAgent")
}

tasks.register("lintAllWeb") {
    group = "web"
    description = "Lint all web components"
    dependsOn("lintWebAgent")
}

tasks.register("cleanAllWeb") {
    group = "web"
    description = "Clean all web artifacts"
    dependsOn("cleanWebAgent")
}

// Интеграция с главной clean task
tasks.named("clean") {
    dependsOn("cleanAllWeb")
}
