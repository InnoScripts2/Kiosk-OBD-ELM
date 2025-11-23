package com.selfservice.kiosk.payments

import android.util.Log
import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentGateway
import com.selfservice.feature.payments.PaymentLogger
import com.selfservice.feature.payments.gateway.DevPaymentGateway
import com.selfservice.feature.payments.gateway.yookassa.YooKassaGatewayConfig
import com.selfservice.feature.payments.gateway.yookassa.YooKassaPaymentGateway
import com.selfservice.kiosk.BuildConfig
import java.util.Locale

object PaymentGatewayResolver {

    fun create(
        environment: PaymentEnvironment,
        logger: PaymentLogger?,
        settings: PaymentGatewaySettings = buildSettingsFromConfig(),
    ): PaymentGateway {
        val label = settings.label?.trim()?.lowercase(Locale.ROOT)
        return when (label) {
            null, "", "dev", "stub", "fake" -> defaultGateway(environment)
            "yookassa", "yoo-kassa", "yoo_kassa" -> buildYooKassaGateway(environment, logger, settings.yookassa)
            else -> {
                Log.w(TAG, "Unknown payments gateway '$label', falling back to dev")
                defaultGateway(environment)
            }
        }
    }

    private fun buildYooKassaGateway(
        environment: PaymentEnvironment,
        logger: PaymentLogger?,
        settings: PaymentGatewaySettings.YooKassaSettings?,
    ): PaymentGateway {
        if (settings == null) {
            Log.e(TAG, "YooKassa settings missing, falling back to dev gateway")
            return YooKassaPaymentGateway.fallback(environment)
        }
        val shopId = settings.shopId?.trim().takeUnless { it.isNullOrEmpty() }
        val secretKey = settings.secretKey?.trim().takeUnless { it.isNullOrEmpty() }
        val returnUrl = settings.returnUrl?.trim().takeUnless { it.isNullOrEmpty() }
        val webhookSecret = settings.webhookSecret?.trim().takeUnless { it.isNullOrEmpty() }
        if (shopId == null || secretKey == null || returnUrl == null) {
            Log.e(TAG, "YooKassa credentials incomplete, falling back to dev gateway")
            return YooKassaPaymentGateway.fallback(environment)
        }
        if (environment != PaymentEnvironment.DEV && webhookSecret == null) {
            Log.e(TAG, "YooKassa webhook secret missing for $environment, falling back to dev gateway")
            return YooKassaPaymentGateway.fallback(environment)
        }
        val config = YooKassaGatewayConfig(
            shopId = shopId,
            secretKey = secretKey,
            returnUrl = returnUrl,
            apiBaseUrl = settings.apiBaseUrl?.takeUnless { it.isBlank() } ?: YooKassaGatewayConfig.DEFAULT_API_URL,
            paymentMethodType = settings.paymentMethodType?.takeUnless { it.isBlank() },
            confirmationType = settings.confirmationType?.takeUnless { it.isBlank() } ?: "redirect",
            issueReceipt = settings.issueReceipt,
            receiptItemDescription = settings.receiptItemDescription?.takeUnless { it.isBlank() },
            receiptVatCode = settings.receiptVatCode?.takeIf { it > 0 },
            taxSystemCode = settings.taxSystemCode?.takeIf { it > 0 },
            requireCustomerForReceipt = settings.requireCustomerForReceipt,
            timeoutMillis = settings.timeoutMillis ?: YooKassaGatewayConfigDefaults.timeoutMillis,
            userAgent = settings.userAgent?.takeUnless { it.isBlank() } ?: defaultUserAgent(),
            webhookSecret = webhookSecret,
        )
        return YooKassaPaymentGateway(
            environment = environment,
            config = config,
            logger = logger,
        )
    }

    private fun defaultGateway(environment: PaymentEnvironment): PaymentGateway =
        DevPaymentGateway(environment = environment, manualMode = environment != PaymentEnvironment.DEV)

    private fun defaultUserAgent(): String = "SelfServiceKiosk/${BuildConfig.VERSION_NAME}"

    fun buildSettingsFromConfig(): PaymentGatewaySettings = PaymentGatewaySettings(
        label = BuildConfig.PAYMENTS_GATEWAY,
        yookassa = PaymentGatewaySettings.YooKassaSettings(
            shopId = BuildConfig.YOOKASSA_SHOP_ID,
            secretKey = BuildConfig.YOOKASSA_SECRET_KEY,
            returnUrl = BuildConfig.YOOKASSA_RETURN_URL,
            apiBaseUrl = BuildConfig.YOOKASSA_API_URL,
            paymentMethodType = BuildConfig.YOOKASSA_PAYMENT_METHOD,
            confirmationType = BuildConfig.YOOKASSA_CONFIRMATION_TYPE,
            issueReceipt = BuildConfig.YOOKASSA_ISSUE_RECEIPT,
            receiptItemDescription = BuildConfig.YOOKASSA_RECEIPT_DESCRIPTION,
            receiptVatCode = BuildConfig.YOOKASSA_RECEIPT_VAT_CODE.takeIf { it > 0 },
            taxSystemCode = BuildConfig.YOOKASSA_TAX_SYSTEM_CODE.takeIf { it > 0 },
            requireCustomerForReceipt = BuildConfig.YOOKASSA_REQUIRE_CUSTOMER_FOR_RECEIPT,
            timeoutMillis = BuildConfig.YOOKASSA_TIMEOUT_MS.takeIf { it > 0 },
            userAgent = BuildConfig.YOOKASSA_USER_AGENT,
            webhookSecret = BuildConfig.YOOKASSA_WEBHOOK_SECRET,
        )
    )

    data class PaymentGatewaySettings(
        val label: String?,
        val yookassa: YooKassaSettings? = null,
    ) {
        data class YooKassaSettings(
            val shopId: String?,
            val secretKey: String?,
            val returnUrl: String?,
            val apiBaseUrl: String?,
            val paymentMethodType: String?,
            val confirmationType: String?,
            val issueReceipt: Boolean,
            val receiptItemDescription: String?,
            val receiptVatCode: Int?,
            val taxSystemCode: Int?,
            val requireCustomerForReceipt: Boolean,
            val timeoutMillis: Int? = null,
            val userAgent: String? = null,
            val webhookSecret: String? = null,
        )
    }

    private const val TAG = "PaymentGatewayResolver"

    private object YooKassaGatewayConfigDefaults {
        const val timeoutMillis: Int = 15_000
    }
}
