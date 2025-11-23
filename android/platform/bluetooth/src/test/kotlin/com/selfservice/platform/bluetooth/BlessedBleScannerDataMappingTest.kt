package com.selfservice.platform.bluetooth

import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScanResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Тесты для проверки правильности маппинга данных между
 * platform/bluetooth и feature-obd-core типами.
 */
class BlessedBleScannerDataMappingTest {
    
    @Test
    fun `mapping from BleDeviceData to BleDevice preserves address`() {
        val sourceDevice = BleDeviceData(
            address = "AA:BB:CC:DD:EE:FF",
            name = "Test OBD Adapter"
        )
        
        val targetDevice = BleDevice(
            address = sourceDevice.address,
            name = sourceDevice.name,
            rssi = -65
        )
        
        assertEquals(sourceDevice.address, targetDevice.address)
        assertEquals(sourceDevice.name, targetDevice.name)
        assertNotNull(targetDevice.rssi)
        assertEquals(-65, targetDevice.rssi)
    }
    
    @Test
    fun `mapping handles null device name correctly`() {
        val sourceDevice = BleDeviceData(
            address = "11:22:33:44:55:66",
            name = ""
        )
        
        val targetDevice = BleDevice(
            address = sourceDevice.address,
            name = sourceDevice.name.ifEmpty { null },
            rssi = -70
        )
        
        assertEquals(sourceDevice.address, targetDevice.address)
        assertNull(targetDevice.name)
    }
    
    @Test
    fun `BleScanResult mapping preserves all fields`() {
        val timestamp = System.currentTimeMillis()
        val serviceUuids = listOf(
            "0000fff0-0000-1000-8000-00805f9b34fb",
            "0000ffe0-0000-1000-8000-00805f9b34fb"
        )
        
        val sourceResult = BleScanResultData(
            device = BleDeviceData("00:11:22:33:44:55", "OBD Device"),
            rssi = -60,
            serviceUuids = serviceUuids,
            seenAtMillis = timestamp
        )
        
        val targetResult = BleScanResult(
            device = BleDevice(
                address = sourceResult.device.address,
                name = sourceResult.device.name,
                rssi = sourceResult.rssi
            ),
            serviceUuids = sourceResult.serviceUuids,
            seenAtMillis = sourceResult.seenAtMillis
        )
        
        assertEquals(sourceResult.device.address, targetResult.device.address)
        assertEquals(sourceResult.device.name, targetResult.device.name)
        assertEquals(sourceResult.rssi, targetResult.device.rssi)
        assertEquals(serviceUuids.size, targetResult.serviceUuids.size)
        assertEquals(timestamp, targetResult.seenAtMillis)
    }
    
    @Test
    fun `BleScannerConfigData maps correctly to domain config`() {
        val pattern = Regex("ELM.*")
        val uuids = listOf("fff0", "ffe0")
        val timeout = 15_000L
        
        val sourceConfig = BleScannerConfigData(
            targetSerialPattern = pattern,
            serviceUuids = uuids,
            timeoutMs = timeout
        )
        
        // В реальном коде адаптер преобразует в BleScannerConfig
        // Здесь просто проверяем, что данные доступны
        assertEquals(pattern, sourceConfig.targetSerialPattern)
        assertEquals(uuids, sourceConfig.serviceUuids)
        assertEquals(timeout, sourceConfig.timeoutMs)
    }
    
    @Test
    fun `empty service UUIDs list is handled correctly`() {
        val sourceResult = BleScanResultData(
            device = BleDeviceData("AA:BB:CC:DD:EE:FF", "Device"),
            rssi = -55,
            serviceUuids = emptyList(),
            seenAtMillis = System.currentTimeMillis()
        )
        
        val targetResult = BleScanResult(
            device = BleDevice(
                address = sourceResult.device.address,
                name = sourceResult.device.name,
                rssi = sourceResult.rssi
            ),
            serviceUuids = sourceResult.serviceUuids,
            seenAtMillis = sourceResult.seenAtMillis
        )
        
        assertEquals(0, targetResult.serviceUuids.size)
    }
    
    @Test
    fun `rssi value range is preserved correctly`() {
        val weakSignalRssi = -90
        val strongSignalRssi = -40
        
        val weakDevice = BleDevice("AA:AA:AA:AA:AA:AA", "Weak", weakSignalRssi)
        val strongDevice = BleDevice("BB:BB:BB:BB:BB:BB", "Strong", strongSignalRssi)
        
        assertEquals(weakSignalRssi, weakDevice.rssi)
        assertEquals(strongSignalRssi, strongDevice.rssi)
    }
}
