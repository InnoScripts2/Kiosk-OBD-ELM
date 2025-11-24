package com.selfservice.feature.reports

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
                ThicknessMeasurement("Капот центр", 125, MeasurementStatus.OK),
                ThicknessMeasurement("Крыша передняя", 98, MeasurementStatus.OK),
                ThicknessMeasurement("Передняя левая дверь", 180, MeasurementStatus.WARNING),
                ThicknessMeasurement("Задняя правая дверь", 250, MeasurementStatus.CRITICAL)
            )
        )
        
        val tempDir = File.createTempFile("test", "").parentFile
        val storageManager = ReportStorageManager(
            config = ReportStorageConfig(baseDir = tempDir)
        )
        
        val htmlFormatter = ThicknessReportHtmlFormatter()
        val pdfGenerator = PdfGenerator()
        
        val generator = ThicknessReportGenerator(
            htmlFormatter = htmlFormatter,
            pdfGenerator = pdfGenerator,
            storageManager = storageManager,
            devMode = true
        )
        
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
        assertTrue(pdfBytes.isNotEmpty(), "PDF should not be empty")
        assertTrue(pdfBytes.size > 100, "PDF should have meaningful size")
        
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
        val sessionDir = tempDir.resolve("reports").resolve("test-session-001")
        assertTrue(sessionDir.exists(), "Session directory should be created")
        
        val htmlFile = sessionDir.resolve("report.html")
        assertTrue(htmlFile.exists(), "HTML file should be saved")
        assertTrue(htmlFile.length() > 0, "HTML file should not be empty")
        
        val pdfFile = sessionDir.resolve("report.pdf")
        assertTrue(pdfFile.exists(), "PDF file should be saved")
        assertTrue(pdfFile.length() > 0, "PDF file should not be empty")
        
        // Cleanup
        sessionDir.deleteRecursively()
    }
    
    @Test
    fun `generate HTML only when PDF not requested`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-002",
            measurements = listOf(
                ThicknessMeasurement("Капот", 120, MeasurementStatus.OK)
            )
        )
        
        val tempDir = File.createTempFile("test", "").parentFile
        val storageManager = ReportStorageManager(
            config = ReportStorageConfig(baseDir = tempDir)
        )
        
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = PdfGenerator(),
            storageManager = storageManager,
            devMode = false
        )
        
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
        
        // Cleanup
        val sessionDir = tempDir.resolve("reports").resolve("test-session-002")
        sessionDir.deleteRecursively()
    }
    
    @Test
    fun `report generation calculates statistics correctly`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-003",
            measurements = listOf(
                ThicknessMeasurement("Zone1", 100, MeasurementStatus.OK),
                ThicknessMeasurement("Zone2", 120, MeasurementStatus.OK),
                ThicknessMeasurement("Zone3", 180, MeasurementStatus.WARNING),
                ThicknessMeasurement("Zone4", 200, MeasurementStatus.WARNING),
                ThicknessMeasurement("Zone5", 300, MeasurementStatus.CRITICAL)
            )
        )
        
        val tempDir = File.createTempFile("test", "").parentFile
        val storageManager = ReportStorageManager(
            config = ReportStorageConfig(baseDir = tempDir)
        )
        
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = PdfGenerator(),
            storageManager = storageManager,
            devMode = true
        )
        
        // Act
        val result = generator.generate(input, listOf(ReportFormat.HTML))
        
        // Assert
        val html = result.html!!
        val stats = input.stats
        
        // Average: (100 + 120 + 180 + 200 + 300) / 5 = 180
        assertEquals(180, stats.avgValue)
        
        // Deviations: 3 (WARNING + CRITICAL zones)
        assertEquals(3, stats.deviations)
        
        // Min/Max
        assertEquals(100, stats.minValue)
        assertEquals(300, stats.maxValue)
        
        // Verify HTML contains stats
        assertTrue(html.contains("180"), "HTML should show average value")
        
        // Cleanup
        val sessionDir = tempDir.resolve("reports").resolve("test-session-003")
        sessionDir.deleteRecursively()
    }
    
    @Test
    fun `dev mode adds DEV badge to HTML`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-004",
            measurements = listOf(
                ThicknessMeasurement("Капот", 120, MeasurementStatus.OK)
            )
        )
        
        val tempDir = File.createTempFile("test", "").parentFile
        val storageManager = ReportStorageManager(
            config = ReportStorageConfig(baseDir = tempDir)
        )
        
        // Generate with DEV mode
        val generatorDev = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = PdfGenerator(),
            storageManager = storageManager,
            devMode = true
        )
        
        // Generate without DEV mode
        val generatorProd = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = PdfGenerator(),
            storageManager = storageManager,
            devMode = false
        )
        
        // Act
        val resultDev = generatorDev.generate(input, listOf(ReportFormat.HTML))
        val resultProd = generatorProd.generate(input, listOf(ReportFormat.HTML))
        
        // Assert
        assertTrue(resultDev.html!!.contains("[МОК-РЕЖИМ]"), "DEV mode should show badge")
        assertFalse(resultProd.html!!.contains("[МОК-РЕЖИМ]"), "PROD mode should not show badge")
        
        // Cleanup
        val sessionDir = tempDir.resolve("reports").resolve("test-session-004")
        sessionDir.deleteRecursively()
    }
    
    @Test
    fun `report generation time is recorded`() = runTest {
        // Arrange
        val input = createThicknessReportInput(
            sessionId = "test-session-005",
            measurements = listOf(
                ThicknessMeasurement("Капот", 120, MeasurementStatus.OK)
            )
        )
        
        val tempDir = File.createTempFile("test", "").parentFile
        val storageManager = ReportStorageManager(
            config = ReportStorageConfig(baseDir = tempDir)
        )
        
        val generator = ThicknessReportGenerator(
            htmlFormatter = ThicknessReportHtmlFormatter(),
            pdfGenerator = PdfGenerator(),
            storageManager = storageManager,
            devMode = false
        )
        
        // Act
        val result = generator.generate(input, listOf(ReportFormat.HTML, ReportFormat.PDF))
        
        // Assert
        assertTrue(result.generationTimeMs > 0, "Generation time should be positive")
        assertTrue(result.generationTimeMs < 5000, "Generation should complete in reasonable time (<5s)")
        
        // Cleanup
        val sessionDir = tempDir.resolve("reports").resolve("test-session-005")
        sessionDir.deleteRecursively()
    }
    
    // Helper function to create test input
    private fun createThicknessReportInput(
        sessionId: String,
        measurements: List<ThicknessMeasurement>
    ): ThicknessReportInput {
        val okCount = measurements.count { it.status == MeasurementStatus.OK }
        val warningCount = measurements.count { it.status == MeasurementStatus.WARNING }
        val criticalCount = measurements.count { it.status == MeasurementStatus.CRITICAL }
        
        val values = measurements.mapNotNull { 
            if (it.status != MeasurementStatus.EMPTY && it.status != MeasurementStatus.ERROR) 
                it.value 
            else null 
        }
        
        val avgValue = if (values.isNotEmpty()) values.average().toInt() else 0
        val minValue = values.minOrNull() ?: 0
        val maxValue = values.maxOrNull() ?: 0
        
        val stats = ThicknessStats(
            avgValue = avgValue,
            minValue = minValue,
            maxValue = maxValue,
            deviations = warningCount + criticalCount,
            okCount = okCount,
            warningCount = warningCount,
            criticalCount = criticalCount,
            emptyCount = measurements.count { it.status == MeasurementStatus.EMPTY },
            errorCount = measurements.count { it.status == MeasurementStatus.ERROR }
        )
        
        val overallStatus = when {
            criticalCount > 0 -> OverallStatus.CRITICAL
            warningCount > 2 -> OverallStatus.ATTENTION_NEEDED
            warningCount > 0 -> OverallStatus.GOOD
            else -> OverallStatus.EXCELLENT
        }
        
        val analysis = ThicknessAnalysis(
            overallStatus = overallStatus,
            recommendation = "Тестовая рекомендация",
            details = listOf("Деталь 1", "Деталь 2")
        )
        
        return ThicknessReportInput(
            sessionId = sessionId,
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
}
