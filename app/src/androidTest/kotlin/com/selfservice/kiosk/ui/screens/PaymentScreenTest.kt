package com.selfservice.kiosk.ui.screens

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class PaymentScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun paymentCountdown_formatsRemainingTime() {
        composeTestRule.setContent {
            PaymentCountdown(remaining = 125_000)
        }

        composeTestRule
            .onNodeWithTag("payment-countdown-text")
            .assertTextContains("02:05")
    }
}
