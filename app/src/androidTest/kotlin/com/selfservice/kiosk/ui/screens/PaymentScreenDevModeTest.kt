package com.selfservice.kiosk.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.selfservice.feature.payments.*
import com.selfservice.kiosk.BuildConfig
import com.selfservice.kiosk.ui.viewmodels.PaymentQrCodeData
import com.selfservice.kiosk.ui.viewmodels.PaymentUiState
import com.selfservice.kiosk.ui.viewmodels.PaymentViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for PaymentQRScreen in DEV mode
 * 
 * Verifies:
 * - DEV mode button visibility based on PAYMENT_MOCK flag
 * - [MOCK MODE] indicator display
 * - Button states and interactions
 * - Timeout countdown display
 * - QR code display
 */
@RunWith(AndroidJUnit4::class)
class PaymentScreenDevModeTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun devModeButton_visibleWhenPaymentMockEnabled() {
        // Arrange
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Processing(
                intentId = "test-intent",
                amount = 35000L,
                currency = "RUB"
            ),
            qrCode = PaymentQrCodeData(
                data = "test-qr-data",
                url = "https://example.com/pay"
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - DEV button should exist when PAYMENT_MOCK is true
        if (BuildConfig.PAYMENT_MOCK) {
            composeTestRule
                .onNodeWithText("Подтвердить", substring = true)
                .assertExists()
                .assertIsEnabled()
            
            composeTestRule
                .onNodeWithText("[DEV MODE]")
                .assertExists()
        } else {
            // In production, DEV button should not exist
            composeTestRule
                .onNodeWithText("[DEV MODE]")
                .assertDoesNotExist()
        }
    }
    
    @Test
    fun mockModeIndicator_displayedWhenPaymentMockEnabled() {
        // Arrange
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Processing(
                intentId = "test-intent",
                amount = 35000L,
                currency = "RUB"
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - [MOCK MODE] indicator should be visible when PAYMENT_MOCK is true
        if (BuildConfig.PAYMENT_MOCK) {
            composeTestRule
                .onNodeWithText("[MOCK MODE] Режим разработки", substring = true)
                .assertExists()
                .assertIsDisplayed()
        }
    }
    
    @Test
    fun cancelButton_alwaysVisible() {
        // Arrange
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Processing(
                intentId = "test-intent",
                amount = 35000L,
                currency = "RUB"
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - Cancel button should always be present
        composeTestRule
            .onNodeWithText("Отмена")
            .assertExists()
            .assertIsEnabled()
    }
    
    @Test
    fun paymentAmount_displayedCorrectly() {
        // Arrange
        val amount = 480
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Processing(
                intentId = "test-intent",
                amount = amount * 100L,
                currency = "RUB"
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = amount,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - Amount should be displayed
        composeTestRule
            .onNodeWithText("Сумма: ${amount}₽")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun qrCode_displayedWhenAvailable() {
        // Arrange
        val qrData = "payment-qr-code-data-12345"
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Processing(
                intentId = "test-intent",
                amount = 35000L,
                currency = "RUB"
            ),
            qrCode = PaymentQrCodeData(
                data = qrData,
                url = null
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - QR code placeholder should be visible
        composeTestRule
            .onNodeWithText("QR КОД", substring = true)
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun timerCountdown_displayedWithRemainingTime() {
        // Arrange
        val remainingTimeMs = 5 * 60 * 1000L // 5 minutes
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Processing(
                intentId = "test-intent",
                amount = 35000L,
                currency = "RUB"
            ),
            remainingTime = remainingTimeMs
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - Timer text should be displayed
        composeTestRule
            .onNodeWithText("Осталось времени:", substring = true)
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun loadingScreen_displayedWhenCreatingIntent() {
        // Arrange
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.CreatingIntent
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - Loading message should be displayed
        composeTestRule
            .onNodeWithText("Подготовка оплаты...")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun successScreen_displayedWhenPaymentCompleted() {
        // Arrange
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.Completed(
                intentId = "test-intent",
                timestamp = System.currentTimeMillis()
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - Success message should be displayed
        composeTestRule
            .onNodeWithText("Оплата подтверждена!")
            .assertExists()
            .assertIsDisplayed()
    }
    
    @Test
    fun errorScreen_displayedWhenPaymentTimedOut() {
        // Arrange
        val mockViewModel = createMockViewModel(
            paymentState = PaymentUiState.TimedOut(
                intentId = "test-intent",
                elapsedMs = 10 * 60 * 1000L
            )
        )
        
        // Act
        composeTestRule.setContent {
            PaymentQRScreen(
                viewModel = mockViewModel,
                amount = 350,
                onPaymentComplete = {},
                onCancel = {},
                onTimeout = {}
            )
        }
        
        // Assert - Timeout error message should be displayed
        composeTestRule
            .onNodeWithText("Время ожидания оплаты истекло", substring = true)
            .assertExists()
            .assertIsDisplayed()
    }
    
    /**
     * Creates a mock PaymentViewModel for testing
     */
    private fun createMockViewModel(
        paymentState: PaymentUiState,
        qrCode: PaymentQrCodeData? = null,
        remainingTime: Long? = null
    ): PaymentViewModel {
        return object : PaymentViewModel(MockPaymentModule()) {
            override val paymentState: StateFlow<PaymentUiState> = 
                MutableStateFlow(paymentState)
            
            override val qrCode: StateFlow<PaymentQrCodeData?> = 
                MutableStateFlow(qrCode)
            
            override val remainingTime: StateFlow<Long?> = 
                MutableStateFlow(remainingTime)
        }
    }
    
    /**
     * Mock PaymentModule for testing
     */
    private class MockPaymentModule : PaymentModule {
        override suspend fun createIntent(input: CreatePaymentIntentInput): CreatePaymentIntentResult {
            throw NotImplementedError("Not used in UI tests")
        }
        
        override suspend fun getStatus(intentId: String): PaymentStatus {
            throw NotImplementedError("Not used in UI tests")
        }
        
        override suspend fun getIntent(intentId: String): PaymentIntent {
            throw NotImplementedError("Not used in UI tests")
        }
        
        override suspend fun confirmDev(intentId: String): GetPaymentIntentResult? {
            throw NotImplementedError("Not used in UI tests")
        }
    }
}
