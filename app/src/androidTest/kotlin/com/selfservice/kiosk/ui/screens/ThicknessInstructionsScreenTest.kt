package com.selfservice.kiosk.ui.screens

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.selfservice.kiosk.ui.state.DeviceStatus
import org.junit.Rule
import org.junit.Test

class ThicknessInstructionsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun instructionsScreen_showsAllKeySections() {
        composeTestRule.setContent {
            ThicknessInstructionsContent(
                deviceStatus = DeviceStatus.Ready,
                totalPoints = 60,
                onBack = {},
                onStartMeasurements = {}
            )
        }

        listOf(
            TAG_INSTRUCTION_SCROLL,
            TAG_INSTRUCTION_HERO,
            TAG_INSTRUCTION_META_GRID,
            TAG_INSTRUCTION_STEPS_PANEL,
            TAG_INSTRUCTION_GUIDELINES_PANEL,
            TAG_INSTRUCTION_TIPS_PANEL,
            TAG_INSTRUCTION_ZONES_PANEL,
            TAG_INSTRUCTION_ACTIONS
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun instructionsScreen_reflectsTotalPointsInCopy() {
        composeTestRule.setContent {
            ThicknessInstructionsContent(
                deviceStatus = DeviceStatus.Ready,
                totalPoints = 72,
                onBack = {},
                onStartMeasurements = {}
            )
        }

        composeTestRule
            .onNodeWithText("72 зон")
            .assertExists()
    }
}
