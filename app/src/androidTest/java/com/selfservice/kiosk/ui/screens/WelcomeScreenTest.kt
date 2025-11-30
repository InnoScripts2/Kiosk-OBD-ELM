package com.selfservice.kiosk.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.selfservice.kiosk.ui.state.PromoHighlightsCatalog
import com.selfservice.platform.ui.foundation.KioskTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun consentRequiredBeforeContinue() {
        var continueInvoked = 0
        composeRule.setContent {
            KioskTheme {
                WelcomeScreen(onContinue = { continueInvoked++ })
            }
        }

        val continueButton = composeRule.onNodeWithTag("welcome-continue")
        continueButton.assertIsNotEnabled()

        composeRule.onNodeWithTag("welcome-consent-checkbox")
            .performClick()

        continueButton.assertIsEnabled()
        continueButton.performClick()

        assertEquals(1, continueInvoked)
    }

    @Test
    fun clausesVisibleWithScreenTag() {
        composeRule.setContent {
            KioskTheme {
                WelcomeScreen(onContinue = {})
            }
        }

        composeRule.onNodeWithTag("welcome-screen")
            .assertExists()

        composeRule.onNodeWithTag("welcome-clauses")
            .assertExists()

        PromoHighlightsCatalog.agreementClauses().forEach { clause ->
            composeRule.onNodeWithText(clause)
                .assertExists()
        }
    }
}
