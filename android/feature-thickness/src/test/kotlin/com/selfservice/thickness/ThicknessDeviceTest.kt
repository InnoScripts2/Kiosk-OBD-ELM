package com.selfservice.thickness

import org.junit.Test
import kotlin.test.assertEquals

/**
 * Unit-тесты для моделей данных толщиномера
 */
class ThicknessDeviceTest {
    
    @Test
    fun `ThicknessMeasurement создаётся с корректными значениями`() {
        val measurement = ThicknessMeasurement(
            timestamp = 1234567890L,
            value = 120.5f,
            zone = "Капот передний",
            status = MeasurementStatus.VALID
        )
        
        assertEquals(1234567890L, measurement.timestamp)
        assertEquals(120.5f, measurement.value)
        assertEquals("Капот передний", measurement.zone)
        assertEquals(MeasurementStatus.VALID, measurement.status)
    }
    
    @Test
    fun `MeasurementStatus содержит все необходимые статусы`() {
        val statuses = MeasurementStatus.values()
        assertEquals(4, statuses.size)
        assert(statuses.contains(MeasurementStatus.VALID))
        assert(statuses.contains(MeasurementStatus.ERROR))
        assert(statuses.contains(MeasurementStatus.TIMEOUT))
        assert(statuses.contains(MeasurementStatus.OUT_OF_RANGE))
    }
    
    @Test
    fun `ConnectionStatus содержит все необходимые статусы`() {
        val statuses = ConnectionStatus.values()
        assertEquals(4, statuses.size)
        assert(statuses.contains(ConnectionStatus.DISCONNECTED))
        assert(statuses.contains(ConnectionStatus.CONNECTING))
        assert(statuses.contains(ConnectionStatus.CONNECTED))
        assert(statuses.contains(ConnectionStatus.ERROR))
    }
}
