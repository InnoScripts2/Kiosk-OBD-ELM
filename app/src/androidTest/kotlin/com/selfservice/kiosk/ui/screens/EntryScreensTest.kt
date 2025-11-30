package com.selfservice.kiosk.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI tests for приветственный блок экранов (Attract → Welcome → Service).
 */
@RunWith(AndroidJUnit4::class)
class EntryScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun attractScreen_clickTriggersCallback() {
        var tapped = false

        composeTestRule.setContent {
            AttractScreen(onTap = { tapped = true })
        }

        composeTestRule.onNodeWithTag("attract-screen").performClick()

        assertTrue("Клик по экрану ожидания должен запускать переход", tapped)
    }

    @Test
    fun welcomeScreen_continueDisabledUntilConsent() {
        var continued = false

        composeTestRule.setContent {
            WelcomeScreen(
                onContinue = { continued = true }
            )
        }

        val continueButton = composeTestRule.onNodeWithTag("welcome-continue")
        continueButton.assertIsNotEnabled()

        composeTestRule.onNodeWithTag("welcome-consent-checkbox").performClick()

        continueButton.assertIsEnabled()
        continueButton.performClick()

        assertTrue("После согласия должна вызываться навигация", continued)
    }

    @Test
    fun serviceSelection_thicknessCardInvokesHandler() {
        var thicknessSelected = false
        var obdSelected = false

        composeTestRule.setContent {
            ServiceSelectionScreen(
                onSelectThickness = { thicknessSelected = true },
                onSelectObd = { obdSelected = true }
            )
        }

        composeTestRule.onNodeWithTag("service-card-thickness").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Клик по карточке толщинометра должен запустить действие", thicknessSelected)
        assertFalse("Выбор толщинометра не должен трогать OBD", obdSelected)
    }

    @Test
    fun serviceSelection_obdCardInvokesHandler() {
        var obdSelected = false

        composeTestRule.setContent {
            ServiceSelectionScreen(
                onSelectThickness = {},
                onSelectObd = { obdSelected = true }
            )
        }

        composeTestRule.onNodeWithTag("service-card-obd").performClick()
        composeTestRule.waitForIdle()

        assertTrue("Клик по карточке OBD должен запускать переход", obdSelected)
    }
}
