package com.selfservice.kiosk.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.selfservice.platform.ui.foundation.KioskTheme
import org.junit.Rule
import org.junit.Test

class ServiceSelectionScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersPrimaryServicesGrid() {
        composeRule.setContent {
            KioskTheme {
                ServiceSelectionScreen(onSelectThickness = {}, onSelectObd = {})
            }
        }

        composeRule.onNodeWithText("Выберите услугу").assertExists()
        composeRule.onNodeWithTag("service-card-thickness").assertExists()
        composeRule.onNodeWithTag("service-card-obd").assertExists()
    }
}
