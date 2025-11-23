package com.selfservice.platform.bluetooth.config

import com.selfservice.platform.bluetooth.ble.BleScanMode
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit-тесты для конфигурации BLE платформы
 * 
 * @since Session 07B
 */
class BlePlatformConfigTest {
    
    @Test
    fun `дефолтная конфигурация должна иметь разумные значения`() {
        val config = BlePlatformConfig()
        
        assertEquals(5000L, config.defaultConnectionTimeout)
        assertEquals(3000L, config.defaultOperationTimeout)
        assertTrue(config.autoReconnect)
        assertEquals(3, config.maxReconnectAttempts)
        assertEquals(2000L, config.reconnectDelay)
        assertEquals(BleScanMode.LOW_LATENCY, config.defaultScanMode)
        assertEquals(10000L, config.defaultScanDuration)
        assertEquals(-90, config.minRssi)
        assertEquals(247, config.preferredMtu)
        assertFalse(config.verboseLogging)
    }
    
    @Test
    fun `development конфигурация должна иметь увеличенные таймауты`() {
        val config = BlePlatformConfig.development()
        
        assertEquals(10000L, config.defaultConnectionTimeout)
        assertEquals(5000L, config.defaultOperationTimeout)
        assertTrue(config.autoReconnect)
        assertEquals(5, config.maxReconnectAttempts)
        assertTrue(config.verboseLogging)
    }
    
    @Test
    fun `production конфигурация должна быть консервативной`() {
        val config = BlePlatformConfig.production()
        
        assertEquals(5000L, config.defaultConnectionTimeout)
        assertEquals(3000L, config.defaultOperationTimeout)
        assertTrue(config.autoReconnect)
        assertEquals(3, config.maxReconnectAttempts)
        assertFalse(config.verboseLogging)
    }
    
    @Test
    fun `testing конфигурация должна иметь быстрые таймауты`() {
        val config = BlePlatformConfig.testing()
        
        assertEquals(2000L, config.defaultConnectionTimeout)
        assertEquals(1000L, config.defaultOperationTimeout)
        assertFalse(config.autoReconnect)
        assertEquals(1, config.maxReconnectAttempts)
        assertTrue(config.verboseLogging)
    }
    
    @Test
    fun `obdServiceUuids должен содержать стандартные UUIDs`() {
        val config = BlePlatformConfig()
        
        assertTrue(config.obdServiceUuids.isNotEmpty())
        assertTrue(config.obdServiceUuids.contains(
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        ))
    }
    
    @Test
    fun `obdDeviceNamePatterns должен содержать распространенные паттерны`() {
        val config = BlePlatformConfig()
        
        assertTrue(config.obdDeviceNamePatterns.isNotEmpty())
        assertTrue(config.obdDeviceNamePatterns.any { it.matches("OBD Device") })
        assertTrue(config.obdDeviceNamePatterns.any { it.matches("ELM327") })
        assertTrue(config.obdDeviceNamePatterns.any { it.matches("VLINK Plus") })
    }
    
    @Test
    fun `builder должен позволять настраивать конфигурацию`() {
        val config = BlePlatformConfigBuilder()
            .connectionTimeout(8000L)
            .operationTimeout(4000L)
            .autoReconnect(false)
            .maxReconnectAttempts(5)
            .reconnectDelay(3000L)
            .scanMode(BleScanMode.BALANCED)
            .scanDuration(15000L)
            .minRssi(-85)
            .preferredMtu(512)
            .verboseLogging(true)
            .build()
        
        assertEquals(8000L, config.defaultConnectionTimeout)
        assertEquals(4000L, config.defaultOperationTimeout)
        assertFalse(config.autoReconnect)
        assertEquals(5, config.maxReconnectAttempts)
        assertEquals(3000L, config.reconnectDelay)
        assertEquals(BleScanMode.BALANCED, config.scanMode)
        assertEquals(15000L, config.defaultScanDuration)
        assertEquals(-85, config.minRssi)
        assertEquals(512, config.preferredMtu)
        assertTrue(config.verboseLogging)
    }
    
    @Test
    fun `builder должен валидировать MTU диапазон`() {
        val builder = BlePlatformConfigBuilder()
        
        assertThrows(IllegalArgumentException::class.java) {
            builder.preferredMtu(22)
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            builder.preferredMtu(518)
        }
    }
    
    @Test
    fun `DSL должен позволять создавать конфигурацию`() {
        val config = blePlatformConfig {
            connectionTimeout(7000L)
            operationTimeout(3500L)
            verboseLogging(true)
        }
        
        assertEquals(7000L, config.defaultConnectionTimeout)
        assertEquals(3500L, config.defaultOperationTimeout)
        assertTrue(config.verboseLogging)
    }
    
    @Test
    fun `можно кастомизировать OBD service UUIDs`() {
        val customUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        
        val config = blePlatformConfig {
            obdServiceUuids(setOf(customUuid))
        }
        
        assertEquals(1, config.obdServiceUuids.size)
        assertTrue(config.obdServiceUuids.contains(customUuid))
    }
    
    @Test
    fun `можно кастомизировать OBD device name patterns`() {
        val customPattern = Regex("MyCustomOBD.*")
        
        val config = blePlatformConfig {
            obdDeviceNamePatterns(listOf(customPattern))
        }
        
        assertEquals(1, config.obdDeviceNamePatterns.size)
        assertTrue(config.obdDeviceNamePatterns.first().matches("MyCustomOBD Device"))
    }
}
