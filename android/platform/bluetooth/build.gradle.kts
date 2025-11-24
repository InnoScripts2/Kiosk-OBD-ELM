@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.selfservice.platform.bluetooth"
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
    
    sourceSets {
        getByName("main") {
            java.srcDirs(
                "src/main/kotlin",
                "blessed/src/main/java"
            )
        }
        getByName("test") {
            java.srcDirs(
                "src/test/kotlin",
                "blessed/src/test/java"
            )
        }
    }
}

dependencies {
    api(project(":feature-obd-core"))

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    // Logging
    implementation(libs.timber)
    
    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
}
