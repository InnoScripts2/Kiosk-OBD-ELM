package com.selfservice.thickness.models

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для моделей зон измерений
 * 
 * @since Session 12B
 */
class ThicknessZoneModelsTest {
    
    @Test
    fun `test zone layout has 60 zones`() {
        val zones = ThicknessZoneLayout.zones
        assertEquals(60, zones.size, "Layout must have exactly 60 measurement zones")
    }
    
    @Test
    fun `test zone indices are sequential`() {
        val zones = ThicknessZoneLayout.zones
        zones.forEachIndexed { index, zone ->
            assertEquals(index, zone.index, "Zone index must match position in list")
        }
    }
    
    @Test
    fun `test zone IDs are unique`() {
        val zones = ThicknessZoneLayout.zones
        val ids = zones.map { it.id }.toSet()
        assertEquals(zones.size, ids.size, "All zone IDs must be unique")
    }
    
    @Test
    fun `test get zone by index`() {
        val zone0 = ThicknessZoneLayout.getZone(0)
        assertNotNull(zone0, "Zone 0 must exist")
        assertEquals(0, zone0.index)
        
        val zone59 = ThicknessZoneLayout.getZone(59)
        assertNotNull(zone59, "Zone 59 must exist")
        assertEquals(59, zone59.index)
        
        val zone60 = ThicknessZoneLayout.getZone(60)
        assertEquals(null, zone60, "Zone 60 must not exist")
    }
    
    @Test
    fun `test get zone by ID`() {
        val zones = ThicknessZoneLayout.zones
        val firstZone = zones.first()
        
        val found = ThicknessZoneLayout.getZoneById(firstZone.id)
        assertNotNull(found)
        assertEquals(firstZone.id, found.id)
        assertEquals(firstZone.index, found.index)
    }
    
    @Test
    fun `test get zones by body part - hood`() {
        val hoodZones = ThicknessZoneLayout.getZonesByBodyPart(BodyPart.HOOD)
        assertEquals(6, hoodZones.size, "Hood should have 6 measurement zones")
        assertTrue(hoodZones.all { it.bodyPart == BodyPart.HOOD })
    }
    
    @Test
    fun `test get zones by body part - doors`() {
        val frontLeftDoorZones = ThicknessZoneLayout.getZonesByBodyPart(BodyPart.FRONT_DOOR_LEFT)
        assertEquals(4, frontLeftDoorZones.size, "Front left door should have 4 zones")
        
        val frontRightDoorZones = ThicknessZoneLayout.getZonesByBodyPart(BodyPart.FRONT_DOOR_RIGHT)
        assertEquals(4, frontRightDoorZones.size, "Front right door should have 4 zones")
        
        val rearLeftDoorZones = ThicknessZoneLayout.getZonesByBodyPart(BodyPart.REAR_DOOR_LEFT)
        assertEquals(4, rearLeftDoorZones.size, "Rear left door should have 4 zones")
        
        val rearRightDoorZones = ThicknessZoneLayout.getZonesByBodyPart(BodyPart.REAR_DOOR_RIGHT)
        assertEquals(4, rearRightDoorZones.size, "Rear right door should have 4 zones")
    }
    
    @Test
    fun `test measurement classification - factory`() {
        assertEquals(MeasurementClassification.FACTORY, MeasurementClassification.classify(60f))
        assertEquals(MeasurementClassification.FACTORY, MeasurementClassification.classify(90f))
        assertEquals(MeasurementClassification.FACTORY, MeasurementClassification.classify(120f))
    }
    
    @Test
    fun `test measurement classification - minor repaint`() {
        assertEquals(MeasurementClassification.MINOR_REPAINT, MeasurementClassification.classify(121f))
        assertEquals(MeasurementClassification.MINOR_REPAINT, MeasurementClassification.classify(150f))
        assertEquals(MeasurementClassification.MINOR_REPAINT, MeasurementClassification.classify(180f))
    }
    
    @Test
    fun `test measurement classification - major repaint`() {
        assertEquals(MeasurementClassification.MAJOR_REPAINT, MeasurementClassification.classify(181f))
        assertEquals(MeasurementClassification.MAJOR_REPAINT, MeasurementClassification.classify(250f))
        assertEquals(MeasurementClassification.MAJOR_REPAINT, MeasurementClassification.classify(300f))
    }
    
    @Test
    fun `test measurement classification - body work`() {
        assertEquals(MeasurementClassification.BODY_WORK, MeasurementClassification.classify(301f))
        assertEquals(MeasurementClassification.BODY_WORK, MeasurementClassification.classify(500f))
        assertEquals(MeasurementClassification.BODY_WORK, MeasurementClassification.classify(1000f))
    }
    
    @Test
    fun `test measurement classification - too thin`() {
        assertEquals(MeasurementClassification.TOO_THIN, MeasurementClassification.classify(59f))
        assertEquals(MeasurementClassification.TOO_THIN, MeasurementClassification.classify(30f))
        assertEquals(MeasurementClassification.TOO_THIN, MeasurementClassification.classify(10f))
    }
    
    @Test
    fun `test measurement classification - unknown`() {
        assertEquals(MeasurementClassification.UNKNOWN, MeasurementClassification.classify(null))
    }
    
    @Test
    fun `test analysis - all factory measurements`() {
        val zones = ThicknessZoneLayout.zones.take(10)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = 100f,
                status = MeasurementStatus.VALID,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val analysis = ThicknessAnalysis.analyze(measurements)
        
        assertEquals(100f, analysis.avgValue)
        assertEquals(100f, analysis.minValue)
        assertEquals(100f, analysis.maxValue)
        assertEquals(10, analysis.factoryCount)
        assertEquals(0, analysis.repaintCount)
        assertEquals(0, analysis.bodyWorkCount)
        assertEquals(0, analysis.deviations)
        assertTrue(analysis.recommendation.contains("отличном состоянии") || 
                   analysis.recommendation.contains("хорошее"))
    }
    
    @Test
    fun `test analysis - mixed measurements`() {
        val zones = ThicknessZoneLayout.zones.take(10)
        val measurements = listOf(
            // 5 заводских
            ZoneMeasurement(zones[0], 80f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[1], 90f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[2], 100f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[3], 110f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[4], 115f, MeasurementStatus.VALID, System.currentTimeMillis()),
            // 3 перекраски
            ZoneMeasurement(zones[5], 150f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[6], 200f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[7], 250f, MeasurementStatus.VALID, System.currentTimeMillis()),
            // 2 кузовной ремонт
            ZoneMeasurement(zones[8], 350f, MeasurementStatus.VALID, System.currentTimeMillis()),
            ZoneMeasurement(zones[9], 400f, MeasurementStatus.VALID, System.currentTimeMillis())
        )
        
        val analysis = ThicknessAnalysis.analyze(measurements)
        
        assertEquals(5, analysis.factoryCount)
        assertEquals(3, analysis.repaintCount)
        assertEquals(2, analysis.bodyWorkCount)
        assertEquals(5, analysis.deviations) // repaint + bodywork
        assertTrue(analysis.avgValue > 100f) // Среднее выше заводского
    }
    
    @Test
    fun `test analysis - empty measurements`() {
        val analysis = ThicknessAnalysis.analyze(emptyList())
        
        assertEquals(0f, analysis.avgValue)
        assertEquals(0f, analysis.minValue)
        assertEquals(0f, analysis.maxValue)
        assertEquals(0, analysis.factoryCount)
        assertEquals(0, analysis.repaintCount)
        assertEquals(0, analysis.bodyWorkCount)
        assertEquals(0, analysis.deviations)
        assertTrue(analysis.recommendation.contains("Недостаточно данных"))
    }
    
    @Test
    fun `test analysis - only invalid measurements`() {
        val zones = ThicknessZoneLayout.zones.take(5)
        val measurements = zones.map { zone ->
            ZoneMeasurement(
                zone = zone,
                value = null,
                status = MeasurementStatus.ERROR,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val analysis = ThicknessAnalysis.analyze(measurements)
        
        assertEquals(0f, analysis.avgValue)
        assertTrue(analysis.recommendation.contains("Недостаточно данных"))
    }
    
    @Test
    fun `test zone position grid layout`() {
        val zones = ThicknessZoneLayout.zones
        
        // Проверяем что все зоны имеют валидные позиции
        zones.forEach { zone ->
            assertTrue(zone.position.row >= 0, "Row must be non-negative")
            assertTrue(zone.position.column >= 0, "Column must be non-negative")
            assertTrue(zone.position.row <= 7, "Row must be <= 7")
            assertTrue(zone.position.column <= 9, "Column must be <= 9")
        }
    }
    
    @Test
    fun `test body part display names are in Russian`() {
        assertEquals("Капот", BodyPart.HOOD.displayName)
        assertEquals("Крыша", BodyPart.ROOF.displayName)
        assertEquals("Багажник", BodyPart.TRUNK.displayName)
        assertEquals("Переднее левое крыло", BodyPart.FRONT_FENDER_LEFT.displayName)
        assertEquals("Передняя левая дверь", BodyPart.FRONT_DOOR_LEFT.displayName)
    }
}
