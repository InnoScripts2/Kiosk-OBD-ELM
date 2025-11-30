package com.selfservice.kiosk.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.selfservice.kiosk.ui.state.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThicknessInputScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun continueDisabledUntilVehicleAndContactsValid() {
        var forwardedType: VehicleType? = null
        var forwardedPhone: String? = null
        var forwardedEmail: String? = null

        composeTestRule.setContent {
            ThicknessInputScreen { type, phone, email ->
                forwardedType = type
                forwardedPhone = phone
                forwardedEmail = email
            }
        }

        val continueButton = composeTestRule.onNodeWithTag("thk-continue-button")
        continueButton.assertIsNotEnabled()
        composeTestRule
            .onNodeWithTag("thk-meta-contacts")
            .assertTextContains("Заполните", substring = true)

        composeTestRule.onNodeWithTag("vehicle-type-sedan").performClick()
        continueButton.assertIsNotEnabled()

        composeTestRule.onNodeWithTag("thk-phone-field").performTextInput("+7 999")
        composeTestRule.onNodeWithTag("thk-email-field").performTextInput("user@")
        continueButton.assertIsNotEnabled()
        assertNull(forwardedType)

        composeTestRule.onNodeWithTag("thk-phone-field").performTextClearance()
        composeTestRule.onNodeWithTag("thk-phone-field").performTextInput("+7 (999) 123-45-67")
        composeTestRule.onNodeWithTag("thk-email-field").performTextClearance()
        composeTestRule.onNodeWithTag("thk-email-field").performTextInput("user@example.com")

        continueButton.assertIsEnabled()
        composeTestRule
            .onNodeWithTag("thk-meta-contacts")
            .assertTextContains("Готово", substring = true)
        continueButton.performClick()

        assertEquals(VehicleType.SEDAN, forwardedType)
        assertEquals("79991234567", forwardedPhone)
        assertEquals("user@example.com", forwardedEmail)
    }

    @Test
    fun invalidContactsShowErrorMessages() {
        composeTestRule.setContent {
            ThicknessInputScreen(onNext = { _, _, _ -> })
        }

        composeTestRule.onNodeWithTag("vehicle-type-suv").performClick()
        composeTestRule.onNodeWithTag("thk-phone-field").performTextInput("+7 1")
        composeTestRule.onNodeWithTag("thk-email-field").performTextInput("bad-email")

        composeTestRule.onNodeWithText("Номер должен содержать минимум 10 цифр").assertExists()
        composeTestRule.onNodeWithText("Введите email в формате name@example.com").assertExists()
    }
}
