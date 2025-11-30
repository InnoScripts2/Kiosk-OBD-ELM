package com.selfservice.feature.reports

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI snapshot тесты для ComposeReportRenderer.
 *
 * Проверяют корректность отображения компонентов отчётов:
 * - Заголовки и структура
 * - KPI карточки
 * - Таблицы измерений/метрик
 * - DEV-режим бейдж
 * - Маскирование персональных данных
 */
@RunWith(AndroidJUnit4::class)
class ComposeReportRendererTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun thicknessReport_displaysTitle() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("Отчёт толщиномера").assertIsDisplayed()
    }

    @Test
    fun thicknessReport_displaysMeasurementZones() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("1. Капот центр").assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Крыша передняя").assertIsDisplayed()
    }

    @Test
    fun thicknessReport_displaysStatistics() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("Среднее").assertIsDisplayed()
        composeTestRule.onNodeWithText("Отклонений").assertIsDisplayed()
        composeTestRule.onNodeWithText("Мин").assertIsDisplayed()
        composeTestRule.onNodeWithText("Макс").assertIsDisplayed()
    }

    @Test
    fun thicknessReport_displaysDevBadgeInDevMode() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = true
            )
        }

        composeTestRule.onNodeWithText("[МОК-РЕЖИМ]").assertIsDisplayed()
    }

    @Test
    fun thicknessReport_doesNotDisplayDevBadgeInProdMode() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("[МОК-РЕЖИМ]").assertDoesNotExist()
    }

    @Test
    fun thicknessReport_masksEmail() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("test@example.com").assertDoesNotExist()
        composeTestRule.onNodeWithText("te***t@example.com").assertIsDisplayed()
    }

    @Test
    fun thicknessReport_masksPhone() {
        val input = createSampleThicknessInput()

        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("+79991234567").assertDoesNotExist()
        composeTestRule.onNodeWithText("+7***67").assertIsDisplayed()
    }

    @Test
    fun diagnosticsReport_displaysTitle() {
        val input = createSampleDiagnosticsInput()

        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("Диагностический отчёт").assertIsDisplayed()
    }

    @Test
    fun diagnosticsReport_displaysVehicleInfo() {
        val input = createSampleDiagnosticsInput()

        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("Toyota Camry").assertIsDisplayed()
        composeTestRule.onNodeWithText("2020").assertIsDisplayed()
    }

    @Test
    fun diagnosticsReport_masksVin() {
        val input = createSampleDiagnosticsInput()

        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("JT1234567890VIN").assertDoesNotExist()
        composeTestRule.onNodeWithText("JT12***0VIN").assertIsDisplayed()
    }

    @Test
    fun diagnosticsReport_displaysRecommendations() {
        val input = createSampleDiagnosticsInput()

        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }

        composeTestRule.onNodeWithText("Рекомендации").assertIsDisplayed()
    }

    private fun createSampleThicknessInput(): ThicknessReportInput {
        val measurements = listOf(
            ThicknessMeasurement(1, "Капот центр", 125f, MeasurementStatus.OK),
            ThicknessMeasurement(2, "Крыша передняя", 98f, MeasurementStatus.OK),
            ThicknessMeasurement(3, "Передняя левая дверь", 180f, MeasurementStatus.WARNING),
            ThicknessMeasurement(4, "Задняя правая дверь", 250f, MeasurementStatus.CRITICAL)
        )

        val values = measurements.map { it.value }
        val completed = measurements.count { it.status != MeasurementStatus.EMPTY }
        val deviations = measurements.count { it.status == MeasurementStatus.WARNING || it.status == MeasurementStatus.CRITICAL }

        val stats = ThicknessStats(
            total = measurements.size,
            completed = completed,
            average = values.average().toFloat(),
            min = values.minOrNull() ?: 0f,
            max = values.maxOrNull() ?: 0f,
            deviations = deviations,
            deviationPercent = if (completed > 0) deviations.toFloat() / completed * 100f else 0f
        )

        val analysis = ThicknessAnalysis(
            recommendation = "Обнаружены значительные отклонения в толщине ЛКП на некоторых панелях.",
            normalRange = ValueRange(min = 80f, max = 200f),
            overallStatus = OverallStatus.FAIR
        )

        return ThicknessReportInput(
            sessionId = "test-session-001",
            generatedAtMillis = System.currentTimeMillis(),
            vehicleType = "Седан",
            price = 350,
            measurements = measurements,
            stats = stats,
            analysis = analysis,
            customer = ThicknessReportCustomer(
                phone = "+79991234567",
                email = "test@example.com"
            )
        )
    }

    private fun createSampleDiagnosticsInput(): DiagnosticsReportInput {
        return sampleReportInput()
    }
}
