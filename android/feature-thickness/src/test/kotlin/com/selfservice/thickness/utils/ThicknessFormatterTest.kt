package com.selfservice.thickness.utils

import com.selfservice.thickness.models.*
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для форматтеров толщиномера
 * @since Session 12B
 */
class ThicknessFormatterTest {
    
    @Test
    fun `test format value with unit`() {
        val result = ThicknessFormatter.formatValue(123.456f, includeUnit = true)
        assertEquals("123.5 μm", result)
    }
    
    @Test
    fun `test format value without unit`() {
        val result = ThicknessFormatter.formatValue(123.456f, includeUnit = false)
        assertEquals("123.5", result)
    }
    
    @Test
    fun `test format null value`() {
        val result = ThicknessFormatter.formatValue(null)
        assertEquals("—", result)
    }
    
    @Test
    fun `test format timestamp`() {
        val timestamp = 1700000000000L // Примерно 14.11.2023
        val result = ThicknessFormatter.formatTimestamp(timestamp)
        assertTrue(result.contains("2023"))
    }
    
    @Test
    fun `test format classification`() {
        assertEquals("Заводская покраска", 
            ThicknessFormatter.formatClassification(MeasurementClassification.FACTORY))
        assertEquals("Незначительная перекраска", 
            ThicknessFormatter.formatClassification(MeasurementClassification.MINOR_REPAINT))
        assertEquals("Значительная перекраска", 
            ThicknessFormatter.formatClassification(MeasurementClassification.MAJOR_REPAINT))
        assertEquals("Кузовной ремонт", 
            ThicknessFormatter.formatClassification(MeasurementClassification.BODY_WORK))
        assertEquals("Слишком тонкая", 
            ThicknessFormatter.formatClassification(MeasurementClassification.TOO_THIN))
        assertEquals("Неизвестно", 
            ThicknessFormatter.formatClassification(MeasurementClassification.UNKNOWN))
    }
    
    @Test
    fun `test format range`() {
        assertEquals("60-120 μm", ThicknessFormatter.formatRange(MeasurementClassification.FACTORY))
        assertEquals("120-180 μm", ThicknessFormatter.formatRange(MeasurementClassification.MINOR_REPAINT))
        assertEquals("180-300 μm", ThicknessFormatter.formatRange(MeasurementClassification.MAJOR_REPAINT))
        assertEquals(">300 μm", ThicknessFormatter.formatRange(MeasurementClassification.BODY_WORK))
        assertEquals("<60 μm", ThicknessFormatter.formatRange(MeasurementClassification.TOO_THIN))
    }
    
    @Test
    fun `test format percentage`() {
        assertEquals("75.5%", ThicknessFormatter.formatPercentage(75.5f, 1))
        assertEquals("75.50%", ThicknessFormatter.formatPercentage(75.5f, 2))
        assertEquals("76%", ThicknessFormatter.formatPercentage(75.5f, 0))
    }
    
    @Test
    fun `test format statistics`() {
        val result = ThicknessFormatter.formatStatistics(
            avg = 100f,
            min = 80f,
            max = 120f,
            stdDev = 15f
        )
        
        assertTrue(result.contains("Среднее: 100.0 μm"))
        assertTrue(result.contains("Минимум: 80.0 μm"))
        assertTrue(result.contains("Максимум: 120.0 μm"))
        assertTrue(result.contains("Отклонение: 15.0 μm"))
    }
    
    @Test
    fun `test format analysis`() {
        val analysis = ThicknessAnalysis(
            avgValue = 105f,
            minValue = 80f,
            maxValue = 130f,
            deviations = 5,
            factoryCount = 45,
            repaintCount = 10,
            bodyWorkCount = 5,
            recommendation = "Состояние хорошее"
        )
        
        val result = ThicknessAnalysisFormatter.formatAnalysis(analysis)
        
        assertTrue(result.contains("АНАЛИЗ ИЗМЕРЕНИЙ"))
        assertTrue(result.contains("Среднее значение: 105.0 μm"))
        assertTrue(result.contains("Заводская покраска: 45 зон"))
        assertTrue(result.contains("Перекраска: 10 зон"))
        assertTrue(result.contains("Кузовной ремонт: 5 зон"))
        assertTrue(result.contains("Всего отклонений: 5"))
        assertTrue(result.contains("Состояние хорошее"))
    }
    
    @Test
    fun `test format summary`() {
        val analysis = ThicknessAnalysis(
            avgValue = 105f,
            minValue = 80f,
            maxValue = 130f,
            deviations = 5,
            factoryCount = 45,
            repaintCount = 10,
            bodyWorkCount = 5,
            recommendation = "Состояние хорошее"
        )
        
        val result = ThicknessAnalysisFormatter.formatSummary(analysis)
        
        assertTrue(result.contains("Среднее: 105.0 μm"))
        assertTrue(result.contains("Отклонений: 5"))
    }
    
    @Test
    fun `test format table`() {
        val zones = ThicknessZoneLayout.zones.take(3)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f + zone.index * 10f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val result = ThicknessAnalysisFormatter.formatTable(measurements)
        
        assertTrue(result.contains("Зона"))
        assertTrue(result.contains("Значение"))
        assertTrue(result.contains("Классификация"))
        measurements.forEach { measurement ->
            assertTrue(result.contains(measurement.zone.displayName))
        }
    }
    
    @Test
    fun `test format full report`() {
        val zones = ThicknessZoneLayout.zones.take(10)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val report = ThicknessReport(
            sessionId = "test_session_123",
            measurements = measurements,
            timestamp = System.currentTimeMillis(),
            vehicleType = "sedan",
            analysis = ThicknessAnalysis.analyze(measurements)
        )
        
        val result = ThicknessReportFormatter.formatFullReport(report)
        
        assertTrue(result.contains("ОТЧЁТ ПО ИЗМЕРЕНИЯМ ТОЛЩИНЫ ЛКП"))
        assertTrue(result.contains("test_session_123"))
        assertTrue(result.contains("Седан"))
        assertTrue(result.contains("Всего измерений: 10"))
        assertTrue(result.contains("АНАЛИЗ ИЗМЕРЕНИЙ"))
        assertTrue(result.contains("ДЕТАЛЬНЫЕ ИЗМЕРЕНИЯ"))
        assertTrue(result.contains("Киоск самообслуживания"))
    }
    
    @Test
    fun `test format brief report`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val report = ThicknessReport(
            sessionId = "test_session_456",
            measurements = measurements,
            timestamp = System.currentTimeMillis(),
            vehicleType = "suv",
            analysis = ThicknessAnalysis.analyze(measurements)
        )
        
        val result = ThicknessReportFormatter.formatBriefReport(report)
        
        assertTrue(result.contains("test_session_456"))
        assertTrue(result.contains("Внедорожник"))
    }
    
    @Test
    fun `test generate HTML report`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val report = ThicknessReport(
            sessionId = "test_session_789",
            measurements = measurements,
            timestamp = System.currentTimeMillis(),
            vehicleType = "minivan",
            analysis = ThicknessAnalysis.analyze(measurements)
        )
        
        val html = ThicknessHtmlFormatter.generateHtml(report)
        
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<html lang=\"ru\">"))
        assertTrue(html.contains("Отчёт по измерениям толщины ЛКП"))
        assertTrue(html.contains("test_session_789"))
        assertTrue(html.contains("<style>"))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("</html>"))
        
        // Проверяем CSS
        assertTrue(html.contains("background-color"))
        assertTrue(html.contains("font-family"))
        
        // Проверяем данные
        measurements.forEach { measurement ->
            assertTrue(html.contains(measurement.zone.displayName))
        }
    }
    
    @Test
    fun `test HTML CSS classes for classifications`() {
        val testCases = listOf(
            Pair(80f, "factory"),
            Pair(150f, "minor-repaint"),
            Pair(250f, "major-repaint"),
            Pair(350f, "body-work"),
            Pair(50f, "too-thin")
        )
        
        testCases.forEach { (value, expectedClass) ->
            val zone = ThicknessZoneLayout.getZone(0)!!
            val measurement = ZoneMeasurement(
                zone = zone,
                value = value,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
            
            val report = ThicknessReport(
                sessionId = "test",
                measurements = listOf(measurement),
                timestamp = System.currentTimeMillis(),
                vehicleType = "sedan",
                analysis = ThicknessAnalysis.analyze(listOf(measurement))
            )
            
            val html = ThicknessHtmlFormatter.generateHtml(report)
            assertTrue(html.contains("class=\"$expectedClass\""), 
                "Expected class '$expectedClass' for value $value")
        }
    }
    
    @Test
    fun `test vehicle type translation`() {
        val testCases = listOf(
            "sedan" to "Седан",
            "suv" to "Внедорожник",
            "minivan" to "Минивэн",
            "hatchback" to "Хэтчбек",
            "coupe" to "Купе",
            "wagon" to "Универсал"
        )
        
        testCases.forEach { (input, expected) ->
            val zones = ThicknessZoneLayout.zones.take(1)
            val measurements = zones.map { zone ->
                ZoneMeasurement(
                    zone = zone,
                    value = 100f,
                    status = MeasurementStatus.VALID,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            val report = ThicknessReport(
                sessionId = "test",
                measurements = measurements,
                timestamp = System.currentTimeMillis(),
                vehicleType = input,
                analysis = ThicknessAnalysis.analyze(measurements)
            )
            
            val formatted = ThicknessReportFormatter.formatFullReport(report)
            assertTrue(formatted.contains(expected), 
                "Expected '$expected' for vehicle type '$input'")
        }
    }
}
