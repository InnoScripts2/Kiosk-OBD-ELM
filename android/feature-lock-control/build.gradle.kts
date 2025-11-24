plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.selfservice.lockcontrol"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // BuildConfig для режимов работы
        buildConfigField("Boolean", "DEVICE_MOCK_LOCK", "false")
        buildConfigField("String", "ARDUINO_PORT", "\"/dev/ttyUSB0\"")
        buildConfigField("int", "ARDUINO_BAUD", "9600")
        buildConfigField("int", "COMMAND_TIMEOUT_MS", "10000")
        buildConfigField("int", "RECONNECT_DELAY_MS", "5000")
        buildConfigField("int", "HEARTBEAT_INTERVAL_MS", "30000")
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "DEVICE_MOCK_LOCK", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("Boolean", "DEVICE_MOCK_LOCK", "false")
        }
    }
    
    buildFeatures {
        buildConfig = true
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
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    
    // USB Serial
    implementation("com.github.mik3y:usb-serial-for-android:3.7.3")
    
    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-compiler:2.48.1")
    
    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
    
    // Platform modules
    implementation(project(":platform-logging"))
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// Kapt options
kapt {
    correctErrorTypes = true
}
