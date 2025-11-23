package com.selfservice.platform.bluetooth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты для BlessedBleScanner и интеграционного адаптера.
 * Проверяем правильность преобразования типов данных между слоями.
 */
class BleScannerIntegrationTest {
    
    @Test
    fun `BleScanResultData should contain all required fields`() {
        val device = BleDeviceData(
            address = "00:11:22:33:44:55",
            name = "OBD-II Adapter"
        )
        
        val result = BleScanResultData(
            device = device,
            rssi = -60,
            serviceUuids = listOf("0000fff0-0000-1000-8000-00805f9b34fb"),
            seenAtMillis = System.currentTimeMillis()
        )
        
        assertEquals("00:11:22:33:44:55", result.device.address)
        assertEquals("OBD-II Adapter", result.device.name)
        assertEquals(-60, result.rssi)
        assertEquals(1, result.serviceUuids.size)
    }
    
    @Test
    fun `BleScannerConfigData should accept nullable pattern`() {
        val config = BleScannerConfigData(
            targetSerialPattern = null,
            serviceUuids = emptyList(),
            timeoutMs = 5000L
        )
        
        assertEquals(null, config.targetSerialPattern)
        assertEquals(5000L, config.timeoutMs)
    }
    
    @Test
    fun `BleScannerConfigData should accept regex pattern`() {
        val pattern = Regex("OBD.*")
        val config = BleScannerConfigData(
            targetSerialPattern = pattern,
            serviceUuids = listOf("fff0"),
            timeoutMs = 10_000L
        )
        
        assertNotNull(config.targetSerialPattern)
        assertEquals(pattern, config.targetSerialPattern)
        assertEquals(1, config.serviceUuids.size)
    }
    
    @Test
    fun `BleDeviceData should handle empty name`() {
        val device = BleDeviceData(
            address = "AA:BB:CC:DD:EE:FF",
            name = ""
        )
        
        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        assertEquals("", device.name)
    }
    
    @Test
    fun `BleScanResultData should preserve service UUIDs`() {
        val serviceUuids = listOf(
            "0000fff0-0000-1000-8000-00805f9b34fb",
            "0000ffe0-0000-1000-8000-00805f9b34fb"
        )
        
        val result = BleScanResultData(
            device = BleDeviceData("11:22:33:44:55:66", "Test"),
            rssi = -70,
            serviceUuids = serviceUuids,
            seenAtMillis = 123456789L
        )
        
        assertEquals(2, result.serviceUuids.size)
        assertEquals(serviceUuids[0], result.serviceUuids[0])
        assertEquals(serviceUuids[1], result.serviceUuids[1])
    }
    
    @Test
    fun `BleScanResultData should handle timestamp correctly`() {
        val timestamp = System.currentTimeMillis()
        val result = BleScanResultData(
            device = BleDeviceData("00:00:00:00:00:00", "Device"),
            rssi = -50,
            serviceUuids = emptyList(),
            seenAtMillis = timestamp
        )
        
        assertEquals(timestamp, result.seenAtMillis)
    }
}
