package com.selfservice.kiosk.payments

import com.selfservice.feature.payments.PaymentEnvironment
import com.selfservice.feature.payments.PaymentGateway
import com.selfservice.feature.payments.gateway.DevPaymentGateway
import com.selfservice.feature.payments.gateway.yookassa.YooKassaPaymentGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaymentGatewayResolverTest {

    @Test
    fun `dev gateway is used when label empty`() {
        val gateway = resolve(
            environment = PaymentEnvironment.DEV,
            settings = PaymentGatewayResolver.PaymentGatewaySettings(label = null)
        )
        assertIs<DevPaymentGateway>(gateway)
        assertEquals(PaymentEnvironment.DEV, gateway.environment)
    }

    @Test
    fun `yookassa gateway requires credentials`() {
        val gateway = resolve(
            environment = PaymentEnvironment.PROD,
            settings = PaymentGatewayResolver.PaymentGatewaySettings(
                label = "yookassa",
                yookassa = PaymentGatewayResolver.PaymentGatewaySettings.YooKassaSettings(
                    shopId = null,
                    secretKey = null,
                    returnUrl = null,
                    apiBaseUrl = "https://unused",
                    paymentMethodType = "sbp",
                    confirmationType = "redirect",
                    issueReceipt = true,
                    receiptItemDescription = null,
                    receiptVatCode = null,
                    taxSystemCode = null,
                    requireCustomerForReceipt = false,
                    timeoutMillis = null,
                    userAgent = null,
                    webhookSecret = null,
                )
            )
        )
        assertIs<DevPaymentGateway>(gateway)
    }

    @Test
    fun `yookassa gateway requires webhook secret outside dev`() {
        val gateway = resolve(
            environment = PaymentEnvironment.QA,
            settings = PaymentGatewayResolver.PaymentGatewaySettings(
                label = "yookassa",
                yookassa = PaymentGatewayResolver.PaymentGatewaySettings.YooKassaSettings(
                    shopId = "shop",
                    secretKey = "secret",
                    returnUrl = "https://example.org/return",
                    apiBaseUrl = "https://api.test",
                    paymentMethodType = "sbp",
                    confirmationType = "redirect",
                    issueReceipt = true,
                    receiptItemDescription = null,
                    receiptVatCode = null,
                    taxSystemCode = null,
                    requireCustomerForReceipt = false,
                    timeoutMillis = null,
                    userAgent = null,
                    webhookSecret = null,
                ),
            ),
        )
        assertIs<DevPaymentGateway>(gateway)
    }

    @Test
    fun `yookassa gateway is created when settings complete`() {
        val gateway = resolve(
            environment = PaymentEnvironment.QA,
            settings = PaymentGatewayResolver.PaymentGatewaySettings(
                label = "yookassa",
                yookassa = PaymentGatewayResolver.PaymentGatewaySettings.YooKassaSettings(
                    shopId = "shop",
                    secretKey = "secret",
                    returnUrl = "https://example.org/return",
                    apiBaseUrl = "https://api.test",
                    paymentMethodType = "sbp",
                    confirmationType = "redirect",
                    issueReceipt = true,
                    receiptItemDescription = "Diagnostics",
                    receiptVatCode = 2,
                    taxSystemCode = 6,
                    requireCustomerForReceipt = false,
                    timeoutMillis = 10_000,
                    userAgent = "TestAgent/1.0",
                    webhookSecret = "whsec_test",
                )
            )
        )
        assertIs<YooKassaPaymentGateway>(gateway)
        assertEquals(PaymentEnvironment.QA, gateway.environment)
        assertTrue(gateway.name.contains("yooka", ignoreCase = true))
    }

    private fun resolve(
        environment: PaymentEnvironment,
        settings: PaymentGatewayResolver.PaymentGatewaySettings,
    ): PaymentGateway = PaymentGatewayResolver.create(environment, logger = null, settings = settings)
}
