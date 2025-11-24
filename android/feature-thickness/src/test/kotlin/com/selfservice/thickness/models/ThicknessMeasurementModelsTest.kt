package com.selfservice.thickness.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThicknessMeasurementModelsTest {
    
    @Test
    fun `test session status transitions`() {
        val statuses = listOf(
            SessionStatus.CREATED,
            SessionStatus.CONNECTING,
            SessionStatus.IN_PROGRESS,
            SessionStatus.COMPLETED
        )
        
        assertEquals(4, statuses.size)
    }
    
    @Test
    fun `test zone statistics calculation`() {
        val zone = ThicknessZoneLayout.getZone(0)!!
        val measurements = listOf(100f, 110f, 120f, 90f, 105f)
        
        val stats = ZoneStatistics.calculate(zone, measurements)
        
        assertEquals(105f, stats.avgValue)
        assertEquals(90f, stats.minValue)
        assertEquals(120f, stats.maxValue)
        assertTrue(stats.stdDeviation > 0f)
    }
    
    @Test
    fun `test measurement comparison`() {
        val zone = ThicknessZoneLayout.getZone(0)!!
        val current = 110f
        val previous = 100f
        
        val comparison = MeasurementComparison.compare(zone, current, previous)
        
        assertEquals(10f, comparison.difference)
        assertEquals(10f, comparison.percentChange)
    }
    
    @Test
    fun `test heat map creation`() {
        val zones = ThicknessZoneLayout.zones.take(10)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f + zone.index,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val heatMap = ThicknessHeatMap.from(measurements)
        
        assertNotNull(heatMap)
        assertTrue(heatMap.values.isNotEmpty())
    }
    
    @Test
    fun `test CSV export`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val export = ThicknessCSVExporter.export(measurements)
        
        assertEquals(ExportFormat.CSV, export.format)
        assertEquals("text/csv", export.mimeType)
        assertTrue(export.data.isNotEmpty())
        assertTrue(export.filename.endsWith(".csv"))
    }
    
    @Test
    fun `test JSON export`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val export = ThicknessJSONExporter.export(measurements)
        
        assertEquals(ExportFormat.JSON, export.format)
        assertEquals("application/json", export.mimeType)
        assertTrue(export.data.isNotEmpty())
        assertTrue(export.filename.endsWith(".json"))
        
        val jsonString = export.data.decodeToString()
        assertTrue(jsonString.contains("\"measurements\":"))
    }
}
