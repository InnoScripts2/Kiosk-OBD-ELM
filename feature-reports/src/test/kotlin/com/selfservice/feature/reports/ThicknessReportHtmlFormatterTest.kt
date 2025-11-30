package com.selfservice.feature.reports

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit-тесты для ThicknessReportHtmlFormatter.
 * 
 * Проверяют:
 * - Генерацию корректного HTML
 * - Форматирование значений
 * - Обработку различных статусов измерений
 * - DEV-режим бейдж
 */
class ThicknessReportHtmlFormatterTest {
    
    private lateinit var formatter: ThicknessReportHtmlFormatter
    
    @Before
    fun setUp() {
        formatter = ThicknessReportHtmlFormatter()
    }
    
    @Test
    fun `format generates valid HTML with all required sections`() {
        // Arrange
        val input = createSampleThicknessReportInput()
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertTrue("HTML should start with DOCTYPE", html.startsWith("<!DOCTYPE html>"))
        assertTrue("HTML should contain title", html.contains("Отчёт толщиномера"))
        assertTrue("HTML should contain session ID", html.contains(input.sessionId))
        assertTrue("HTML should contain vehicle type", html.contains(input.vehicleType))
        assertTrue("HTML should contain styles", html.contains("<style>"))
        assertTrue("HTML should close properly", html.endsWith("</html>"))
    }
    
    @Test
    fun `format includes DEV mode badge when devMode is true`() {
        // Arrange
        val input = createSampleThicknessReportInput()
        
        // Act
        val html = formatter.format(input, devMode = true)
        
        // Assert
        assertTrue("HTML should contain DEV badge", html.contains("[МОК-РЕЖИМ]"))
        assertTrue("HTML should contain dev-mode-badge class", html.contains("dev-mode-badge"))
    }
    
    @Test
    fun `format excludes DEV mode badge when devMode is false`() {
        // Arrange
        val input = createSampleThicknessReportInput()
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertFalse("HTML should not contain DEV badge", html.contains("[МОК-РЕЖИМ]"))
        assertFalse(
            "HTML should not contain dev-mode-badge element",
            html.contains("<div class=\"dev-mode-badge\">")
        )
    }
    
    @Test
    fun `format includes KPI cards with correct values`() {
        // Arrange
        val input = createSampleThicknessReportInput()
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertTrue("HTML should show completed/total", html.contains("${input.stats.completed} / ${input.stats.total}"))
        assertTrue("HTML should show deviations", html.contains("${input.stats.deviations}"))
        assertTrue("HTML should contain KPI card class", html.contains("kpi-card"))
    }
    
    @Test
    fun `format includes measurements table with all zones`() {
        // Arrange
        val input = createSampleThicknessReportInput()
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        input.measurements.forEach { measurement ->
            assertTrue("HTML should contain zone name", html.contains(measurement.zoneName))
        }
        assertTrue("HTML should contain table", html.contains("<table>"))
        assertTrue("HTML should contain thead", html.contains("<thead>"))
        assertTrue("HTML should contain tbody", html.contains("<tbody>"))
    }
    
    @Test
    fun `format includes analysis section with recommendation`() {
        // Arrange
        val input = createSampleThicknessReportInput()
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertTrue("HTML should contain recommendation", html.contains(input.analysis.recommendation))
        assertTrue("HTML should contain normal range", html.contains("Нормальный диапазон"))
    }
    
    @Test
    fun `format includes customer contacts when provided`() {
        // Arrange
        val customer = ThicknessReportCustomer(
            phone = "+79001234567",
            email = "test@example.com"
        )
        val input = createSampleThicknessReportInput(customer = customer)
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertTrue("HTML should contain contacts section", html.contains("Контакты клиента"))
        // Телефон и email должны быть замаскированы
        assertFalse("HTML should not contain full phone", html.contains(customer.phone!!))
        assertFalse("HTML should not contain full email", html.contains(customer.email!!))
    }
    
    @Test
    fun `format excludes customer contacts when not provided`() {
        // Arrange
        val input = createSampleThicknessReportInput(customer = null)
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertFalse("HTML should not contain contacts section", html.contains("Контакты клиента"))
    }
    
    @Test
    fun `format includes status icons for different measurement statuses`() {
        // Arrange
        val measurements = listOf(
            ThicknessMeasurement(1, "Капот", 120f, MeasurementStatus.OK),
            ThicknessMeasurement(2, "Крыша", 75f, MeasurementStatus.WARNING),
            ThicknessMeasurement(3, "Дверь", 250f, MeasurementStatus.CRITICAL),
            ThicknessMeasurement(4, "Багажник", 0f, MeasurementStatus.EMPTY)
        )
        val input = createSampleThicknessReportInput(measurements = measurements)
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertTrue("HTML should contain OK badge", html.contains("tag-ok"))
        assertTrue("HTML should contain WARNING badge", html.contains("tag-warning"))
        assertTrue("HTML should contain CRITICAL badge", html.contains("tag-critical"))
        assertTrue("HTML should contain EMPTY badge", html.contains("tag-empty"))
    }
    
    @Test
    fun `format escapes HTML in user inputs`() {
        // Arrange
        val maliciousZoneName = "<script>alert('XSS')</script>"
        val measurements = listOf(
            ThicknessMeasurement(1, maliciousZoneName, 120f, MeasurementStatus.OK)
        )
        val input = createSampleThicknessReportInput(measurements = measurements)
        
        // Act
        val html = formatter.format(input, devMode = false)
        
        // Assert
        assertFalse("HTML should not contain script tags", html.contains("<script>"))
        assertTrue("HTML should escape HTML entities", html.contains("&lt;script&gt;"))
    }
    
    // Helper methods
    
    private fun createSampleThicknessReportInput(
        sessionId: String = "test-session-123",
        vehicleType: String = "Седан",
        measurements: List<ThicknessMeasurement> = createSampleMeasurements(),
        customer: ThicknessReportCustomer? = null
    ): ThicknessReportInput {
        return ThicknessReportInput(
            sessionId = sessionId,
            generatedAtMillis = System.currentTimeMillis(),
            vehicleType = vehicleType,
            price = 350,
            measurements = measurements,
            stats = calculateStats(measurements),
            analysis = ThicknessAnalysis(
                recommendation = "Состояние лакокрасочного покрытия в норме.",
                normalRange = ValueRange(min = 80f, max = 200f),
                overallStatus = OverallStatus.GOOD
            ),
            customer = customer
        )
    }
    
    private fun createSampleMeasurements(): List<ThicknessMeasurement> {
        return listOf(
            ThicknessMeasurement(1, "Капот", 125f, MeasurementStatus.OK),
            ThicknessMeasurement(2, "Крыша", 118f, MeasurementStatus.OK),
            ThicknessMeasurement(3, "Дверь передняя левая", 122f, MeasurementStatus.OK),
            ThicknessMeasurement(4, "Дверь передняя правая", 120f, MeasurementStatus.OK),
            ThicknessMeasurement(5, "Крыло переднее левое", 115f, MeasurementStatus.OK),
            ThicknessMeasurement(6, "Крыло переднее правое", 117f, MeasurementStatus.OK),
            ThicknessMeasurement(7, "Дверь задняя левая", 180f, MeasurementStatus.WARNING),
            ThicknessMeasurement(8, "Дверь задняя правая", 119f, MeasurementStatus.OK),
            ThicknessMeasurement(9, "Крыло заднее левое", 220f, MeasurementStatus.CRITICAL),
            ThicknessMeasurement(10, "Крыло заднее правое", 116f, MeasurementStatus.OK)
        )
    }
    
    private fun calculateStats(measurements: List<ThicknessMeasurement>): ThicknessStats {
        val completed = measurements.filter { it.status != MeasurementStatus.EMPTY }
        val values = completed.map { it.value }
        val deviations = completed.count { 
            it.status == MeasurementStatus.WARNING || it.status == MeasurementStatus.CRITICAL 
        }
        
        return ThicknessStats(
            total = measurements.size,
            completed = completed.size,
            average = if (values.isNotEmpty()) values.average().toFloat() else 0f,
            min = values.minOrNull() ?: 0f,
            max = values.maxOrNull() ?: 0f,
            deviations = deviations,
            deviationPercent = if (completed.isNotEmpty()) {
                (deviations.toFloat() / completed.size * 100)
            } else 0f
        )
    }
}
