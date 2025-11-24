package com.selfservice.feature.reports

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

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
class ComposeReportRendererTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun thicknessReport_displaysTitle() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("Отчёт толщиномера").assertIsDisplayed()
    }
    
    @Test
    fun thicknessReport_displaysMeasurementZones() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("1. Капот центр").assertIsDisplayed()
        composeTestRule.onNodeWithText("2. Крыша передняя").assertIsDisplayed()
    }
    
    @Test
    fun thicknessReport_displaysStatistics() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("Среднее").assertIsDisplayed()
        composeTestRule.onNodeWithText("Отклонений").assertIsDisplayed()
        composeTestRule.onNodeWithText("Мин").assertIsDisplayed()
        composeTestRule.onNodeWithText("Макс").assertIsDisplayed()
    }
    
    @Test
    fun thicknessReport_displaysDevBadgeInDevMode() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act - DEV mode
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = true
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("[МОК-РЕЖИМ]").assertIsDisplayed()
    }
    
    @Test
    fun thicknessReport_doesNotDisplayDevBadgeInProdMode() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act - PROD mode
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("[МОК-РЕЖИМ]").assertDoesNotExist()
    }
    
    @Test
    fun thicknessReport_masksEmail() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert - email должен быть замаскирован
        // Оригинал: test@example.com → Маска: te***t@example.com
        composeTestRule.onNodeWithText("test@example.com").assertDoesNotExist()
        composeTestRule.onNodeWithText("te***t@example.com").assertIsDisplayed()
    }
    
    @Test
    fun thicknessReport_masksPhone() {
        // Arrange
        val input = createSampleThicknessInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.ThicknessReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert - телефон должен быть замаскирован
        // Оригинал: +79991234567 → Маска: +7***67
        composeTestRule.onNodeWithText("+79991234567").assertDoesNotExist()
        composeTestRule.onNodeWithText("+7***67").assertIsDisplayed()
    }
    
    @Test
    fun diagnosticsReport_displaysTitle() {
        // Arrange
        val input = createSampleDiagnosticsInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("Диагностический отчёт").assertIsDisplayed()
    }
    
    @Test
    fun diagnosticsReport_displaysVehicleInfo() {
        // Arrange
        val input = createSampleDiagnosticsInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("Toyota Camry").assertIsDisplayed()
        composeTestRule.onNodeWithText("2020").assertIsDisplayed()
    }
    
    @Test
    fun diagnosticsReport_masksVin() {
        // Arrange
        val input = createSampleDiagnosticsInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert - VIN должен быть замаскирован
        // Оригинал: JT1234567890VIN → Маска: JT12***0VIN
        composeTestRule.onNodeWithText("JT1234567890VIN").assertDoesNotExist()
        composeTestRule.onNodeWithText("JT12***0VIN").assertIsDisplayed()
    }
    
    @Test
    fun diagnosticsReport_displaysRecommendations() {
        // Arrange
        val input = createSampleDiagnosticsInput()
        
        // Act
        composeTestRule.setContent {
            ComposeReportRenderer.DiagnosticsReport(
                input = input,
                devMode = false
            )
        }
        
        // Assert
        composeTestRule.onNodeWithText("Рекомендации").assertIsDisplayed()
    }
    
    // Helper functions
    
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
        return sampleReportInput() // Используем существующий helper из ReportTestFixtures
    }
}
