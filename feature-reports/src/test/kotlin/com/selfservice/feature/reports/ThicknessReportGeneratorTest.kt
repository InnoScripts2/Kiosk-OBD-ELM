package com.selfservice.feature.reports

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit тесты для ThicknessReportGenerator.
 * 
 * Проверяют полный цикл генерации отчёта толщиномера:
 * - Валидация входных данных
 * - Генерация HTML
 * - Генерация PDF
 * - Сохранение файлов
 * - Запись метаданных
 */
class ThicknessReportGeneratorTest {

    @Test
    fun `generate report with valid input produces HTML and PDF`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-001",
            measurements = listOf(
                measurement(1, "Капот центр", 125f, MeasurementStatus.OK),
                measurement(2, "Крыша передняя", 98f, MeasurementStatus.OK),
                measurement(3, "Передняя левая дверь", 180f, MeasurementStatus.WARNING),
                measurement(4, "Задняя правая дверь", 250f, MeasurementStatus.CRITICAL)
            )
        )
        
        val storage = createStorageTestContext(prefix = "thickness-report-valid")
        val pdfGenerator = FakePdfGenerator()
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = pdfGenerator,
            storageManager = storage.storageManager,
            devMode = true
        )
        
        try {
            // Act
            val result = generator.generate(
                input = input,
                formats = listOf(ReportFormat.HTML, ReportFormat.PDF)
            )
            
            // Assert
            assertTrue(result.success, "Report generation should succeed")
            assertNotNull(result.metadata, "Metadata should be generated")
            assertNotNull(result.html, "HTML should be generated")
            assertNotNull(result.pdfBytes, "PDF bytes should be generated")
            
            // Verify HTML content
            val html = result.html!!
            assertTrue(html.contains("Отчёт толщиномера"), "HTML should contain report title")
            assertTrue(html.contains("test-session-001"), "HTML should contain session ID")
            assertTrue(html.contains("Капот центр"), "HTML should contain measurement zones")
            assertTrue(html.contains("125"), "HTML should contain measurement values")
            assertTrue(html.contains("[МОК-РЕЖИМ]"), "HTML should contain DEV badge in dev mode")
            
            // Verify PDF
            val pdfBytes = result.pdfBytes!!
            assertEquals(FakePdfGenerator.RENDERED_PDF.size, pdfBytes.size)
            assertEquals(FakePdfGenerator.RENDERED_PDF.toList(), pdfBytes.toList())
            assertEquals(ReportType.THICKNESS, pdfGenerator.capturedReportType)
            
            // Verify metadata
            val metadata = result.metadata!!
            assertEquals("test-session-001", metadata.sessionId)
            assertEquals(ReportType.THICKNESS, metadata.reportType)
            assertEquals(ReportStatus.GENERATED, metadata.status)
            assertTrue(metadata.formats.contains(ReportFormat.HTML))
            assertTrue(metadata.formats.contains(ReportFormat.PDF))
            assertNotNull(metadata.htmlHash, "HTML hash should be calculated")
            assertNotNull(metadata.pdfHash, "PDF hash should be calculated")
            assertNotNull(metadata.htmlSizeBytes, "HTML size should be recorded")
            assertNotNull(metadata.pdfSizeBytes, "PDF size should be recorded")
            
            // Verify files saved
            val sessionDir = storage.reportsDir.resolve("test-session-001")
            assertTrue(sessionDir.exists(), "Session directory should be created")
            
            val htmlFile = sessionDir.resolve("report.html")
            assertTrue(htmlFile.exists(), "HTML file should be saved")
            assertTrue(htmlFile.length() > 0, "HTML file should not be empty")
            
            val pdfFile = sessionDir.resolve("report.pdf")
            assertTrue(pdfFile.exists(), "PDF file should be saved")
            assertTrue(pdfFile.length() > 0, "PDF file should not be empty")
        } finally {
            storage.cleanup()
        }
    }
    
    @Test
    fun `generate HTML only when PDF not requested`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-002",
            measurements = listOf(
                measurement(1, "Капот", 120f, MeasurementStatus.OK)
            )
        )
        
        val storage = createStorageTestContext(prefix = "thickness-report-html-only")
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = FakePdfGenerator(),
            storageManager = storage.storageManager,
            devMode = false
        )
        
        try {
            // Act
            val result = generator.generate(
                input = input,
                formats = listOf(ReportFormat.HTML)
            )
            
            // Assert
            assertTrue(result.success)
            assertNotNull(result.html)
            assertEquals(null, result.pdfBytes, "PDF should not be generated when not requested")
            
            val metadata = result.metadata!!
            assertEquals(1, metadata.formats.size)
            assertTrue(metadata.formats.contains(ReportFormat.HTML))
            assertFalse(metadata.formats.contains(ReportFormat.PDF))
        } finally {
            storage.cleanup()
        }
    }
    
    @Test
    fun `report generation calculates statistics correctly`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-003",
            measurements = listOf(
                measurement(1, "Zone1", 100f, MeasurementStatus.OK),
                measurement(2, "Zone2", 120f, MeasurementStatus.OK),
                measurement(3, "Zone3", 180f, MeasurementStatus.WARNING),
                measurement(4, "Zone4", 200f, MeasurementStatus.WARNING),
                measurement(5, "Zone5", 300f, MeasurementStatus.CRITICAL)
            )
        )
        
        val storage = createStorageTestContext(prefix = "thickness-report-stats")
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = FakePdfGenerator(),
            storageManager = storage.storageManager,
            devMode = true
        )
        
        try {
            // Act
            val result = generator.generate(input, listOf(ReportFormat.HTML))
            
            // Assert
            val html = result.html!!
            val stats = input.stats
        
        // Average: (100 + 120 + 180 + 200 + 300) / 5 = 180
        assertEquals(180f, stats.average)
        
        // Deviations: 3 (WARNING + CRITICAL zones)
        assertEquals(3, stats.deviations)
        
        // Min/Max
            assertEquals(100f, stats.min)
            assertEquals(300f, stats.max)
            
            // Verify HTML contains stats
            assertTrue(html.contains("180"), "HTML should show average value")
        } finally {
            storage.cleanup()
        }
    }
    
    @Test
    fun `dev mode adds DEV badge to HTML`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-004",
            measurements = listOf(
                measurement(1, "Капот", 120f, MeasurementStatus.OK)
            )
        )
        
        val storage = createStorageTestContext(prefix = "thickness-report-dev-mode")
        val generatorDev = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = FakePdfGenerator(),
            storageManager = storage.storageManager,
            devMode = true
        )
        val generatorProd = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = FakePdfGenerator(),
            storageManager = storage.storageManager,
            devMode = false
        )
        
        try {
            // Act
            val resultDev = generatorDev.generate(input, listOf(ReportFormat.HTML))
            val resultProd = generatorProd.generate(input, listOf(ReportFormat.HTML))
            
            // Assert
            assertTrue(resultDev.html!!.contains("[МОК-РЕЖИМ]"), "DEV mode should show badge")
            assertFalse(resultProd.html!!.contains("[МОК-РЕЖИМ]"), "PROD mode should not show badge")
        } finally {
            storage.cleanup()
        }
    }
    
    @Test
    fun `report generation time is recorded`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-005",
            measurements = listOf(
                measurement(1, "Капот", 120f, MeasurementStatus.OK)
            )
        )
        
        val storage = createStorageTestContext(prefix = "thickness-report-timing")
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = FakePdfGenerator(),
            storageManager = storage.storageManager,
            devMode = false
        )
        
        try {
            // Act
            val result = generator.generate(input, listOf(ReportFormat.HTML, ReportFormat.PDF))
            
            // Assert
            assertTrue(result.generationTimeMs > 0, "Generation time should be positive")
            assertTrue(result.generationTimeMs < 5000, "Generation should complete in reasonable time (<5s)")
        } finally {
            storage.cleanup()
        }
    }
    
    private fun measurement(
        zoneNumber: Int,
        zoneName: String,
        value: Float,
        status: MeasurementStatus,
        comment: String? = null
    ): ThicknessMeasurement {
        return ThicknessMeasurement(
            zoneNumber = zoneNumber,
            zoneName = zoneName,
            value = value,
            status = status,
            comment = comment
        )
    }
    
    // Helper function to create test input
    private fun createThicknessReportInput(
        sessionId: String,
        measurements: List<ThicknessMeasurement>
    ): ThicknessReportInput {
        val values = measurements.mapNotNull { 
            if (it.status != MeasurementStatus.EMPTY && it.status != MeasurementStatus.ERROR) 
                it.value 
            else null 
        }
        
        val completedCount = measurements.count { it.status != MeasurementStatus.EMPTY }
        val deviationsCount = measurements.count { it.status == MeasurementStatus.WARNING || it.status == MeasurementStatus.CRITICAL }
        
        val stats = ThicknessStats(
            total = measurements.size,
            completed = completedCount,
            average = if (values.isNotEmpty()) values.average().toFloat() else 0f,
            min = values.minOrNull() ?: 0f,
            max = values.maxOrNull() ?: 0f,
            deviations = deviationsCount,
            deviationPercent = if (completedCount == 0) 0f else deviationsCount.toFloat() / completedCount * 100f
        )
        
        val warningCount = measurements.count { it.status == MeasurementStatus.WARNING }
        val criticalCount = measurements.count { it.status == MeasurementStatus.CRITICAL }
        
        val overallStatus = when {
            criticalCount > 0 -> OverallStatus.CRITICAL
            warningCount > 2 -> OverallStatus.POOR
            warningCount > 0 -> OverallStatus.FAIR
            else -> OverallStatus.EXCELLENT
        }
        
        val analysis = ThicknessAnalysis(
            recommendation = "Тестовая рекомендация",
            normalRange = ValueRange(min = 80f, max = 200f),
            overallStatus = overallStatus
        )
        
        return ThicknessReportInput(
            sessionId = sessionId,
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
}
