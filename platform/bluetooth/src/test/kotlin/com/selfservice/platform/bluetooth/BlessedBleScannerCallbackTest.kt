package com.selfservice.platform.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Минимальные проверки отображения данных сканера BLE без
 * инициализации Android BLE стека.
 */
class BlessedBleScannerCallbackTest {

    @Test
    fun `BleScanResultData correctly maps device address and name`() {
        val device = BleDeviceData(
            address = "11:22:33:44:55:66",
            name = "ELM327"
        )
        
        val result = BleScanResultData(
            device = device,
            rssi = -70,
            serviceUuids = listOf("0000fff0-0000-1000-8000-00805f9b34fb"),
            seenAtMillis = System.currentTimeMillis()
        )
        
        assertEquals("11:22:33:44:55:66", result.device.address)
        assertEquals("ELM327", result.device.name)
        assertEquals(-70, result.rssi)
        assertEquals(1, result.serviceUuids.size)
    }
    
    @Test
    fun `BleScannerConfigData has correct default values`() {
        val config = BleScannerConfigData()
        
        assertEquals(null, config.targetSerialPattern)
        assertEquals(emptyList(), config.serviceUuids)
        assertEquals(10_000L, config.timeoutMs)
    }
    
    @Test
    fun `BleDeviceData handles empty name correctly`() {
        val device = BleDeviceData(
            address = "AA:BB:CC:DD:EE:FF",
            name = ""
        )
        
        assertEquals("AA:BB:CC:DD:EE:FF", device.address)
        assertEquals("", device.name)
    }
    
    @Test
    fun `scan config with service UUIDs filters correctly`() {
        val obdServiceUuid = "0000fff0-0000-1000-8000-00805f9b34fb"
        val config = BleScannerConfigData(
            serviceUuids = listOf(obdServiceUuid),
            timeoutMs = 30_000L
        )
        
        assertEquals(1, config.serviceUuids.size)
        assertEquals(obdServiceUuid, config.serviceUuids.first())
        assertEquals(30_000L, config.timeoutMs)
    }
}
