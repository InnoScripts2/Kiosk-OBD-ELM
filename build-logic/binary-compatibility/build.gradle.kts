plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.innoscripts.buildlogic"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:binary-compatibility-validator:0.18.1")
}

gradlePlugin {
    plugins {
        register("binaryCompatibilityConfiguration") {
            id = "binary-compatibility-configuration"
            implementationClass = "com.innoscripts.buildlogic.compatibility.BinaryCompatibilityConfigurationPlugin"
        }
    }
}
