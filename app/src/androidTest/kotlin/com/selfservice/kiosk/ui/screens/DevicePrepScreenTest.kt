package com.selfservice.kiosk.ui.screens

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class DevicePrepScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun devicePrepProgress_showsRoundedPercentage() {
        composeTestRule.setContent {
            DevicePrepProgressBlock(progress = 0.425f)
        }

        composeTestRule
            .onNodeWithTag("device-prep-progress-value")
            .assertTextContains("43%")
    }
}
