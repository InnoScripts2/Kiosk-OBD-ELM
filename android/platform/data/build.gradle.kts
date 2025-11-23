@file:Suppress("DSL_SCOPE_VIOLATION")

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.selfservice.platform.data"
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
    implementation(project(":core"))
    implementation(libs.org.json)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

// Gradle task to generate DTC catalogs from source data
tasks.register<Exec>("generateDtcCatalog") {
    group = "build"
    description = "Generate DTC catalogs from android/base using Python script"

    val scriptPath = project.rootProject.file("tools/dtc/build_catalog.py")
    val basePath = project.rootProject.file("base")
    val genericOutPath = project.rootProject.file(
        "feature-obd-core/src/main/resources/com/selfservice/obd/core/dtc/dtc.json"
    )
    val manufacturerOutPath = project.file("src/main/assets/dtc_database.json")
    val versionOutPath = project.file("src/main/assets/catalog_version.json")
    
    // Get version from project or use default
    val catalogVersion = project.findProperty("dtcCatalogVersion")?.toString() ?: "1.0.0"

    // Define inputs and outputs for up-to-date checking
    inputs.dir(basePath)
    inputs.file(scriptPath)
    outputs.file(genericOutPath)
    outputs.file(manufacturerOutPath)
    outputs.file(versionOutPath)

    commandLine(
        "python3",
        scriptPath.absolutePath,
        "--base", basePath.absolutePath,
        "--generic-out", genericOutPath.absolutePath,
        "--manufacturer-out", manufacturerOutPath.absolutePath,
        "--version-out", versionOutPath.absolutePath,
        "--version", catalogVersion,
        "-v"
    )

    doFirst {
        logger.lifecycle("Generating DTC catalogs (version $catalogVersion)...")
    }

    doLast {
        logger.lifecycle("DTC catalogs generated successfully")
        logger.lifecycle("  Generic catalog: ${genericOutPath.absolutePath}")
        logger.lifecycle("  Manufacturer catalog: ${manufacturerOutPath.absolutePath}")
        logger.lifecycle("  Version metadata: ${versionOutPath.absolutePath}")
    }
}

// Hook into the preBuild task to ensure catalogs are generated
tasks.named("preBuild") {
    dependsOn("generateDtcCatalog")
}
