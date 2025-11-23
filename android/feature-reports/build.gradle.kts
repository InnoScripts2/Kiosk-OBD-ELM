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
    
    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    
    // Robolectric для Android тестов
    testImplementation(libs.robolectric)
}
