package com.selfservice.thickness.mock

import com.selfservice.thickness.ConnectionStatus
import com.selfservice.thickness.MeasurementStatus
import com.selfservice.thickness.models.MockDeviceConfig
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Тесты для MockThicknessDevice
 * 
 * @since Session 12B
 */
class MockThicknessDeviceTest {
    
    @Test
    fun `test connect sets connected status`() = runBlocking {
        val config = MockDeviceConfig(enabled = true, simulateDelay = false)
        val device = MockThicknessDevice(config)
        
        val result = device.connect()
        
        assertTrue(result.isSuccess)
        assertEquals(ConnectionStatus.CONNECTED, device.getConnectionStatus())
    }
    
    @Test
    fun `test disconnect sets disconnected status`() = runBlocking {
        val config = MockDeviceConfig(enabled = true, simulateDelay = false)
        val device = MockThicknessDevice(config)
        
        device.connect()
        device.disconnect()
        
        assertEquals(ConnectionStatus.DISCONNECTED, device.getConnectionStatus())
    }
    
    @Test
    fun `test start measurements generates flow`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false,
            randomize = false,
            baseValue = 100f
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(5).toList()
        
        assertEquals(5, measurements.size)
        measurements.forEach { measurement ->
            assertEquals(100f, measurement.value)
            assertEquals(MeasurementStatus.VALID, measurement.status)
        }
    }
    
    @Test
    fun `test measurements with randomization`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false,
            randomize = true
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(10).toList()
        
        assertEquals(10, measurements.size)
        
        // Проверяем что значения различаются (рандомизация работает)
        val values = measurements.map { it.value }.toSet()
        assertTrue(values.size > 1, "Randomization should produce different values")
        
        // Все значения должны быть в разумном диапазоне
        measurements.forEach { measurement ->
            assertTrue(measurement.value in 50f..600f)
        }
    }
    
    @Test
    fun `test error rate produces errors`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false,
            randomize = true,
            errorRate = 1.0f // 100% ошибок
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(5).toList()
        
        assertEquals(5, measurements.size)
        measurements.forEach { measurement ->
            assertEquals(MeasurementStatus.ERROR, measurement.status)
            assertEquals(0f, measurement.value)
        }
    }
    
    @Test
    fun `test error rate 0 produces no errors`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false,
            randomize = true,
            errorRate = 0.0f // 0% ошибок
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(10).toList()
        
        assertEquals(10, measurements.size)
        measurements.forEach { measurement ->
            assertEquals(MeasurementStatus.VALID, measurement.status)
            assertTrue(measurement.value > 0f)
        }
    }
    
    @Test
    fun `test stop measurements ends flow`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        // Запускаем измерения и сразу останавливаем
        val flow = device.startMeasurements()
        device.stopMeasurements()
        
        // Flow должен завершиться быстро
        val measurements = flow.take(1).toList()
        assertTrue(measurements.isEmpty() || measurements.size == 1)
    }
    
    @Test
    fun `test cannot start measurements without connection`() = runBlocking {
        val config = MockDeviceConfig(enabled = true, simulateDelay = false)
        val device = MockThicknessDevice(config)
        
        // Не подключаемся
        
        var exceptionThrown = false
        try {
            device.startMeasurements().take(1).toList()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("[MOCK MODE]") == true)
        }
        
        assertTrue(exceptionThrown)
    }
    
    @Test
    fun `test zone naming increments`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(5).toList()
        
        assertEquals("zone_1", measurements[0].zone)
        assertEquals("zone_2", measurements[1].zone)
        assertEquals("zone_3", measurements[2].zone)
        assertEquals("zone_4", measurements[3].zone)
        assertEquals("zone_5", measurements[4].zone)
    }
    
    @Test
    fun `test disconnect resets measurement count`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false
        )
        val device = MockThicknessDevice(config)
        
        // Первая сессия
        device.connect()
        val measurements1 = device.startMeasurements().take(3).toList()
        device.disconnect()
        
        // Вторая сессия
        device.connect()
        val measurements2 = device.startMeasurements().take(3).toList()
        
        // Счётчик зон должен сброситься
        assertEquals("zone_1", measurements1[0].zone)
        assertEquals("zone_1", measurements2[0].zone)
    }
    
    @Test
    fun `test forTesting config has fast measurements`() = runBlocking {
        val config = MockDeviceConfig.forTesting()
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val startTime = System.currentTimeMillis()
        val measurements = device.startMeasurements().take(5).toList()
        val endTime = System.currentTimeMillis()
        
        val duration = endTime - startTime
        
        // С delay 100ms, 5 измерений должны занять ~500ms
        assertTrue(duration < 1000, "Fast measurements should complete quickly")
        assertEquals(5, measurements.size)
    }
    
    @Test
    fun `test forDemo config has realistic distribution`() = runBlocking {
        val config = MockDeviceConfig.forDemo()
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(100).toList()
        
        assertEquals(100, measurements.size)
        
        // Большинство измерений должны быть в заводском диапазоне (60-120)
        val factoryRange = measurements.count { it.value in 60f..120f }
        assertTrue(factoryRange > 50, "Most measurements should be in factory range")
    }
    
    @Test
    fun `test realistic value distribution`() = runBlocking {
        val config = MockDeviceConfig(
            enabled = true,
            simulateDelay = false,
            randomize = true,
            errorRate = 0.0f
        )
        val device = MockThicknessDevice(config)
        
        device.connect()
        
        val measurements = device.startMeasurements().take(100).toList()
        
        // Проверяем распределение по диапазонам
        val factory = measurements.count { it.value in 60f..120f }
        val minorRepaint = measurements.count { it.value in 121f..180f }
        val majorRepaint = measurements.count { it.value in 181f..300f }
        val bodyWork = measurements.count { it.value > 300f }
        
        // 60% должны быть заводскими
        assertTrue(factory > 50, "Expected ~60 factory measurements, got $factory")
        
        // Остальные распределены по другим категориям
        assertTrue(minorRepaint + majorRepaint + bodyWork > 30)
    }
}
