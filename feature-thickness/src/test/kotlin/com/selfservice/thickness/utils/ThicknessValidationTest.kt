package com.selfservice.thickness.utils

import com.selfservice.thickness.models.MeasurementStatus
import com.selfservice.thickness.models.ThicknessZoneLayout
import com.selfservice.thickness.models.ZoneMeasurement
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThicknessValidationTest {
    
    @Test
    fun `test validate measurement value - valid`() {
        val result = ThicknessValidation.validateMeasurementValue(100f)
        assertTrue(result.isSuccess)
        assertEquals(100f, result.getOrNull())
    }
    
    @Test
    fun `test validate measurement value - NaN`() {
        val result = ThicknessValidation.validateMeasurementValue(Float.NaN)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `test validate measurement value - out of range high`() {
        val result = ThicknessValidation.validateMeasurementValue(3000f)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `test validate measurement value - out of range low`() {
        val result = ThicknessValidation.validateMeasurementValue(-10f)
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `test validate measurements list - all valid`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val result = ThicknessValidation.validateMeasurements(measurements)
        
        assertEquals(5, result.validCount)
        assertEquals(0, result.invalidCount)
        assertFalse(result.hasErrors)
        assertEquals(100f, result.validPercentage)
    }
    
    @Test
    fun `test validate measurements list - mixed`() {
        val zones = ThicknessZoneLayout.zones.take(10)
        val measurements = listOf(
            // 5 валидных
            ZoneMeasurement(zones[0], 100f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[1], 110f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[2], 120f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[3], 90f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[4], 105f, MeasurementStatus.VALID, System.currentTimeMillis()),
            // 3 невалидных (ошибки)
            ZoneMeasurement(zones[5], null, MeasurementStatus.ERROR, System.currentTimeMillis()),
            ZoneMeasurement(zones[6], null, MeasurementStatus.TIMEOUT, System.currentTimeMillis()),
            ZoneMeasurement(zones[7], 0f, MeasurementStatus.ERROR, System.currentTimeMillis()),
            // 2 вне диапазона
            ZoneMeasurement(zones[8], 3000f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[9], -10f, MeasurementStatus.VALID, System.currentTimeMillis())
        )
        
        val result = ThicknessValidation.validateMeasurements(measurements)
        
        assertEquals(5, result.validCount)
        assertEquals(5, result.invalidCount)
        assertTrue(result.hasErrors)
        assertEquals(50f, result.validPercentage)
    }
    
    @Test
    fun `test isValid extension`() {
        val zone = ThicknessZoneLayout.getZone(0)!!
        
        val validMeasurement = ZoneMeasurement(
            zone, 100f, MeasurementStatus.VALID, System.currentTimeMillis()
        )
        assertTrue(validMeasurement.isValid())
        
        val invalidMeasurement = ZoneMeasurement(
            zone, null, MeasurementStatus.ERROR, System.currentTimeMillis()
        )
        assertFalse(invalidMeasurement.isValid())
    }
    
    @Test
    fun `test filterValid extension`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = listOf(
            ZoneMeasurement(zones[0], 100f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[1], null, MeasurementStatus.ERROR, System.currentTimeMillis()),
            ZoneMeasurement(zones[2], 110f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[3], null, MeasurementStatus.TIMEOUT, System.currentTimeMillis()),
            ZoneMeasurement(zones[4], 120f, MeasurementStatus.VALID, System.currentTimeMillis())
        )
        
        val filtered = measurements.filterValid()
        
        assertEquals(3, filtered.size)
        assertTrue(filtered.all { it.isValid() })
    }
    
    @Test
    fun `test validPercentage extension`() {
        val zones = ThicknessZoneLayout.zones.take(10)
        val measurements = (0 until 7).map { i ->
            ZoneMeasurement(zones[i], 100f, MeasurementStatus.VALID, System.currentTimeMillis())
        } + (7 until 10).map { i ->
            ZoneMeasurement(zones[i], null, MeasurementStatus.ERROR, System.currentTimeMillis())
        }
        
        val percentage = measurements.validPercentage()
        
        assertEquals(70f, percentage)
    }
}
