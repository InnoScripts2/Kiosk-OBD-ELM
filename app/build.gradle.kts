@file:Suppress("DSL_SCOPE_VIOLATION")

import java.util.Locale
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun String.escapeForBuildConfig(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

val kioskEnvironmentRaw = (project.findProperty("kiosk.environment") as? String)?.trim().orEmpty()
val kioskEnvironmentEffective = kioskEnvironmentRaw.ifBlank { "dev" }
val normalizedKioskEnvironment = kioskEnvironmentEffective.lowercase(Locale.ROOT)
val paymentsGatewayLabel = (project.findProperty("payments.gateway") as? String)?.trim().orEmpty()
val supabaseServiceKeyProperty = (project.findProperty("supabase.serviceKey") as? String)?.trim().orEmpty()
val supabaseServiceKeyEnv = sequenceOf(
        "SUPABASE_SERVICE_KEY",
        "SUPABASE_SERVICE_TOKEN",
        "SUPABASE_SERVICEKEY",
    )
    .mapNotNull { System.getenv(it)?.trim() }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()
val supabaseServiceKeyValue = when {
    supabaseServiceKeyProperty.isNotEmpty() -> supabaseServiceKeyProperty
    supabaseServiceKeyEnv.isNotEmpty() -> supabaseServiceKeyEnv
    else -> ""
}
val yookassaWebhookSecretProperty = (project.findProperty("payments.yookassa.webhookSecret") as? String)?.trim().orEmpty()
val yookassaWebhookSecretEnv = sequenceOf(
        "YOOKASSA_WEBHOOK_SECRET",
        "PAYMENTS_YOOKASSA_WEBHOOK_SECRET",
    )
    .mapNotNull { System.getenv(it)?.trim() }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()
val yookassaWebhookSecretValue = when {
    yookassaWebhookSecretProperty.isNotEmpty() -> yookassaWebhookSecretProperty
    yookassaWebhookSecretEnv.isNotEmpty() -> yookassaWebhookSecretEnv
    else -> ""
}
val nonDevKioskEnvironments = setOf("qa", "stage", "staging", "preprod", "prod", "production", "uat")
val shouldEnforceYooWebhookSecret = paymentsGatewayLabel.equals("yookassa", ignoreCase = true) &&
    normalizedKioskEnvironment in nonDevKioskEnvironments
val shouldEnforceSupabaseServiceKey = normalizedKioskEnvironment in nonDevKioskEnvironments

fun ensureYooKassaWebhookSecretPresent() {
    if (shouldEnforceYooWebhookSecret && yookassaWebhookSecretValue.isBlank()) {
        error(
                "YooKassa webhook secret is required for environment '" +
                        kioskEnvironmentEffective +
                        "' when payments.gateway=yookassa. Provide the Gradle property `payments.yookassa.webhookSecret` " +
                        "or export the environment variable `YOOKASSA_WEBHOOK_SECRET`/`PAYMENTS_YOOKASSA_WEBHOOK_SECRET`. " +
                        "See plan-secrets-config.md for details."
        )
    }
}

fun ensureSupabaseServiceKeyPresent() {
    if (shouldEnforceSupabaseServiceKey && supabaseServiceKeyValue.isBlank()) {
        error(
                "Supabase service key is required for environment '" +
                        kioskEnvironmentEffective +
                        "'. Provide the Gradle property `supabase.serviceKey` or export the environment variable `SUPABASE_SERVICE_KEY`. " +
                        "See credential-inventory.md and plan-secrets-config.md for details."
        )
    }
}

android {
    namespace = "com.selfservice.kiosk"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.selfservice.kiosk"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables.useSupportLibrary = true

        val supabaseUrl = (project.findProperty("supabase.url") as? String)?.trim().orEmpty()
        val supabaseServiceKey = supabaseServiceKeyValue
        val kioskId = (project.findProperty("kiosk.id") as? String)?.trim().orEmpty()
        val kioskEnvironment = kioskEnvironmentRaw
        val mdmDeviceId = (project.findProperty("mdm.deviceId") as? String)?.trim().orEmpty()
        val mdmPolicyVersion = (project.findProperty("mdm.policyVersion") as? String)?.trim().orEmpty()
        val paymentsEncryptionKey = (project.findProperty("payments.encryptionKey") as? String)?.trim().orEmpty()
        val paymentsGateway = paymentsGatewayLabel
        val yooShopId = (project.findProperty("payments.yookassa.shopId") as? String)?.trim().orEmpty()
        val yooSecretKey = (project.findProperty("payments.yookassa.secretKey") as? String)?.trim().orEmpty()
        val yooReturnUrl = (project.findProperty("payments.yookassa.returnUrl") as? String)?.trim().orEmpty()
        val yooApiBaseUrl = (project.findProperty("payments.yookassa.apiBaseUrl") as? String)?.trim().orEmpty()
        val yooPaymentMethod = (project.findProperty("payments.yookassa.paymentMethodType") as? String)?.trim().orEmpty()
        val yooConfirmationType = (project.findProperty("payments.yookassa.confirmationType") as? String)?.trim().orEmpty()
        val yooReceiptDescription = (project.findProperty("payments.yookassa.receiptDescription") as? String)?.trim().orEmpty()
        val yooIssueReceipt = (project.findProperty("payments.yookassa.issueReceipt") as? String)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { value -> value.equals("true", true) || value == "1" }
            ?: true
        val yooWebhookSecret = yookassaWebhookSecretValue
        val yooReceiptVatCode = (project.findProperty("payments.yookassa.receiptVatCode") as? String)?.trim()?.toIntOrNull() ?: 0
        val yooTaxSystemCode = (project.findProperty("payments.yookassa.taxSystemCode") as? String)?.trim()?.toIntOrNull() ?: 0
        val yooRequireReceiptContact = (project.findProperty("payments.yookassa.requireCustomerForReceipt") as? String)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { value -> value.equals("true", true) || value == "1" }
            ?: false
        val yooTimeoutMs = (project.findProperty("payments.yookassa.timeoutMs") as? String)?.trim()?.toIntOrNull() ?: 0
        val yooUserAgent = (project.findProperty("payments.yookassa.userAgent") as? String)?.trim().orEmpty()
        val mdmAllowedPackages = (project.findProperty("mdm.allowedPackages") as? String)?.trim().orEmpty()
        
        // APP_MODE and PAYMENT_MOCK from environment or gradle properties
        val appMode = (project.findProperty("app.mode") as? String)?.trim()
            ?: System.getenv("APP_MODE")?.trim()
            ?: "DEV"
        val paymentMock = (project.findProperty("payment.mock") as? String)?.trim()
            ?.let { it.equals("true", ignoreCase = true) || it == "1" }
            ?: System.getenv("PAYMENT_MOCK")?.trim()?.let { it.equals("true", ignoreCase = true) || it == "1" }
            ?: (appMode.equals("DEV", ignoreCase = true))
        
        buildConfigField("String", "APP_MODE", "\"${appMode.escapeForBuildConfig()}\"")
        buildConfigField("boolean", "PAYMENT_MOCK", paymentMock.toString())
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseUrl.escapeForBuildConfig()}\"")
        buildConfigField(
            "String",
            "SUPABASE_SERVICE_KEY",
            "\"${supabaseServiceKey.escapeForBuildConfig()}\""
        )
        buildConfigField("String", "KIOSK_ID", "\"${kioskId.escapeForBuildConfig()}\"")
        buildConfigField(
            "String",
            "KIOSK_ENVIRONMENT",
            "\"${kioskEnvironment.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "MDM_DEVICE_ID",
            "\"${mdmDeviceId.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "MDM_POLICY_VERSION",
            "\"${mdmPolicyVersion.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "MDM_ALLOWED_PACKAGES",
            "\"${mdmAllowedPackages.escapeForBuildConfig()}\""
        )
        buildConfigField(
            "String",
            "PAYMENTS_ENCRYPTION_KEY",
            "\"${paymentsEncryptionKey.escapeForBuildConfig()}\""
        )
        buildConfigField("String", "PAYMENTS_GATEWAY", "\"${paymentsGateway.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_SHOP_ID", "\"${yooShopId.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_SECRET_KEY", "\"${yooSecretKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_RETURN_URL", "\"${yooReturnUrl.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_API_URL", "\"${yooApiBaseUrl.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_PAYMENT_METHOD", "\"${yooPaymentMethod.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_CONFIRMATION_TYPE", "\"${yooConfirmationType.escapeForBuildConfig()}\"")
        buildConfigField("boolean", "YOOKASSA_ISSUE_RECEIPT", yooIssueReceipt.toString())
        buildConfigField("String", "YOOKASSA_RECEIPT_DESCRIPTION", "\"${yooReceiptDescription.escapeForBuildConfig()}\"")
        buildConfigField("int", "YOOKASSA_RECEIPT_VAT_CODE", yooReceiptVatCode.toString())
        buildConfigField("int", "YOOKASSA_TAX_SYSTEM_CODE", yooTaxSystemCode.toString())
        buildConfigField("boolean", "YOOKASSA_REQUIRE_CUSTOMER_FOR_RECEIPT", yooRequireReceiptContact.toString())
        buildConfigField("int", "YOOKASSA_TIMEOUT_MS", yooTimeoutMs.toString())
        buildConfigField("String", "YOOKASSA_USER_AGENT", "\"${yooUserAgent.escapeForBuildConfig()}\"")
        buildConfigField("String", "YOOKASSA_WEBHOOK_SECRET", "\"${yooWebhookSecret.escapeForBuildConfig()}\"")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val checkYooKassaWebhookSecret = tasks.register("checkYooKassaWebhookSecret") {
    group = "verification"
    description = "Ensures the YooKassa webhook secret is provided before building or running tests."
    doLast {
        if (shouldEnforceYooWebhookSecret) {
            ensureYooKassaWebhookSecretPresent()
            println("YooKassa webhook secret detected for environment '${kioskEnvironmentEffective}'.")
        } else if (yookassaWebhookSecretValue.isBlank()) {
            println("YooKassa webhook secret not required for environment '${kioskEnvironmentEffective}'.")
        } else {
            println(
                    "YooKassa webhook secret provided; enforcement skipped for environment '${kioskEnvironmentEffective}'."
            )
        }
    }
}

val checkSupabaseServiceKey = tasks.register("checkSupabaseServiceKey") {
    group = "verification"
    description = "Ensures the Supabase service key is available before building or running tests."
    doLast {
        if (shouldEnforceSupabaseServiceKey) {
            ensureSupabaseServiceKeyPresent()
            println("Supabase service key detected for environment '${kioskEnvironmentEffective}'.")
        } else if (supabaseServiceKeyValue.isBlank()) {
            println("Supabase service key not required for environment '${kioskEnvironmentEffective}'.")
        } else {
            println(
                    "Supabase service key provided; enforcement skipped for environment '${kioskEnvironmentEffective}'."
            )
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(checkYooKassaWebhookSecret, checkSupabaseServiceKey)
}

tasks.withType<Test>().configureEach {
    dependsOn(checkYooKassaWebhookSecret, checkSupabaseServiceKey)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.material)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.org.json)
    implementation(libs.timber)
    implementation(libs.androidx.webkit)
    implementation(project(":core"))
    implementation(project(":feature-obd-core"))
    implementation(project(":feature-obd-diagnostics"))
    implementation(project(":feature-obd-ui"))
    implementation(project(":feature-reports"))
    implementation(project(":feature-payments"))
    implementation(project(":platform-data"))
    implementation(project(":platform-logging"))
    implementation(project(":platform-ui"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
