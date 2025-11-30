@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.selfservice.feature.payments"
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.org.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}

// ========================================================================================
// npm Tasks для payment-mock-kit
// ========================================================================================

val paymentMockKitDir = file("payment-mock-kit")

tasks.register<Exec>("npmInstallPaymentMockKit") {
    group = "device-kit"
    description = "Install npm dependencies for payment-mock-kit"
    workingDir = paymentMockKitDir
    commandLine("npm", "install")
    onlyIf { paymentMockKitDir.exists() && file("$paymentMockKitDir/package.json").exists() }
    
    inputs.file("$paymentMockKitDir/package.json")
    outputs.dir("$paymentMockKitDir/node_modules")
}

tasks.register<Exec>("buildPaymentMockKit") {
    group = "device-kit"
    description = "Build TypeScript payment-mock-kit (npm run build)"
    dependsOn("npmInstallPaymentMockKit")
    workingDir = paymentMockKitDir
    commandLine("npm", "run", "build")
    onlyIf { paymentMockKitDir.exists() && file("$paymentMockKitDir/package.json").exists() }
    
    inputs.dir("$paymentMockKitDir/src")
    inputs.file("$paymentMockKitDir/tsconfig.json")
    outputs.dir("$paymentMockKitDir/dist")
}

tasks.register<Exec>("testPaymentMockKit") {
    group = "device-kit"
    description = "Run Jest tests for payment-mock-kit"
    dependsOn("npmInstallPaymentMockKit")
    workingDir = paymentMockKitDir
    commandLine("npm", "test")
    onlyIf { paymentMockKitDir.exists() && file("$paymentMockKitDir/package.json").exists() }
    
    inputs.dir("$paymentMockKitDir/src")
    outputs.upToDateWhen { false }
}

tasks.register<Exec>("lintPaymentMockKit") {
    group = "device-kit"
    description = "Run linting for payment-mock-kit"
    dependsOn("npmInstallPaymentMockKit")
    workingDir = paymentMockKitDir
    commandLine("npm", "run", "lint")
    onlyIf { paymentMockKitDir.exists() && file("$paymentMockKitDir/package.json").exists() }
    
    inputs.dir("$paymentMockKitDir/src")
    outputs.upToDateWhen { false }
}
