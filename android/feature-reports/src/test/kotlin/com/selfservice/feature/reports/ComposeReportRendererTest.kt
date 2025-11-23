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
        composeTestRule.onNodeWithText("Капот центр").assertIsDisplayed()
        composeTestRule.onNodeWithText("Крыша передняя").assertIsDisplayed()
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
            ThicknessMeasurement("Капот центр", 125, MeasurementStatus.OK),
            ThicknessMeasurement("Крыша передняя", 98, MeasurementStatus.OK),
            ThicknessMeasurement("Передняя левая дверь", 180, MeasurementStatus.WARNING),
            ThicknessMeasurement("Задняя правая дверь", 250, MeasurementStatus.CRITICAL)
        )
        
        val stats = ThicknessStats(
            avgValue = 163,
            minValue = 98,
            maxValue = 250,
            deviations = 2,
            okCount = 2,
            warningCount = 1,
            criticalCount = 1,
            emptyCount = 0,
            errorCount = 0
        )
        
        val analysis = ThicknessAnalysis(
            overallStatus = OverallStatus.ATTENTION_NEEDED,
            recommendation = "Обнаружены значительные отклонения в толщине ЛКП на некоторых панелях.",
            details = listOf(
                "Задняя правая дверь: критическое значение 250 µm",
                "Передняя левая дверь: повышенное значение 180 µm"
            )
        )
        
        return ThicknessReportInput(
            sessionId = "test-session-001",
            generatedAtMillis = System.currentTimeMillis(),
            vehicleType = "Седан",
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
