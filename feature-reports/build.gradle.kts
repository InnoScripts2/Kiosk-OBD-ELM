@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.selfservice.feature.reports"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        
        // BuildConfig для режимов DEV/QA/PROD
        buildConfigField("String", "APP_MODE", "\"DEV\"")
        buildConfigField("boolean", "ENABLE_DEV_BADGE", "true")
    }

    buildTypes {
        debug {
            buildConfigField("String", "APP_MODE", "\"DEV\"")
            buildConfigField("boolean", "ENABLE_DEV_BADGE", "true")
        }
        release {
            buildConfigField("String", "APP_MODE", "\"PROD\"")
            buildConfigField("boolean", "ENABLE_DEV_BADGE", "false")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
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
    // Project modules
    implementation(project(":core"))
    implementation(project(":platform-data"))
    implementation(project(":feature-lock-control"))
    
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Timber logging
    implementation(libs.timber)
    
    // Compose UI (Session 11B)
    implementation("androidx.compose.ui:ui:1.6.8")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.foundation:foundation:1.6.8")
    implementation("androidx.compose.runtime:runtime:1.6.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    
    // Compose preview support
    debugImplementation("androidx.compose.ui:ui-tooling:1.6.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
    
    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    
    // Robolectric для Android тестов
    testImplementation(libs.robolectric)
    
    // Compose testing
    testImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.6.8")
}

// ========================================================================================
// npm Tasks для report-kit
// ========================================================================================

val reportKitDir = file("report-kit")

tasks.register<Exec>("npmInstallReportKit") {
    group = "device-kit"
    description = "Install npm dependencies for report-kit"
    workingDir = reportKitDir
    commandLine("npm", "install")
    onlyIf { reportKitDir.exists() && file("$reportKitDir/package.json").exists() }
    
    inputs.file("$reportKitDir/package.json")
    outputs.dir("$reportKitDir/node_modules")
}

tasks.register<Exec>("buildReportKit") {
    group = "device-kit"
    description = "Build TypeScript report-kit (npm run build)"
    dependsOn("npmInstallReportKit")
    workingDir = reportKitDir
    commandLine("npm", "run", "build")
    onlyIf { reportKitDir.exists() && file("$reportKitDir/package.json").exists() }
    
    inputs.dir("$reportKitDir/src")
    inputs.file("$reportKitDir/tsconfig.json")
    outputs.dir("$reportKitDir/dist")
}

tasks.register<Exec>("testReportKit") {
    group = "device-kit"
    description = "Run Jest tests for report-kit"
    dependsOn("npmInstallReportKit")
    workingDir = reportKitDir
    commandLine("npm", "test")
    onlyIf { reportKitDir.exists() && file("$reportKitDir/package.json").exists() }
    
    inputs.dir("$reportKitDir/src")
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("lintReportKit") {
    group = "device-kit"
    description = "Run linting for report-kit"
    dependsOn("npmInstallReportKit")
    workingDir = reportKitDir
    commandLine("npm", "run", "lint")
    onlyIf { reportKitDir.exists() && file("$reportKitDir/package.json").exists() }
    
    inputs.dir("$reportKitDir/src")
    outputs.upToDateWhen { false }
}
