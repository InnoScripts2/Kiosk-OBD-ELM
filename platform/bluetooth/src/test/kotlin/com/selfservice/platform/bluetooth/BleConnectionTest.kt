package com.selfservice.platform.bluetooth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit-тесты для BLE компонентов.
 * Проверяют базовую логику без реального BLE оборудования.
 */
class BleConnectionStateTest {
    
    @Test
    fun `BleConnectionState Disconnected should be initial state`() {
        val state = BleConnectionState.Disconnected
        assertNotNull(state)
        assertTrue(state is BleConnectionState.Disconnected)
    }
    
    @Test
    fun `BleConnectionState Connected should contain address`() {
        val address = "00:11:22:33:44:55"
        val state = BleConnectionState.Connected(address)
        assertEquals(address, state.address)
    }
    
    @Test
    fun `BleConnectionState Error should contain message`() {
        val errorMessage = "Connection timeout"
        val state = BleConnectionState.Error(errorMessage)
        assertEquals(errorMessage, state.message)
    }
}

class BleDeviceTest {
    
    @Test
    fun `BleDevice should be created with valid parameters`() {
        val device = BleDevice(
            address = "00:11:22:33:44:55",
            name = "OBD-II Adapter",
            rssi = -65
        )
        
        assertEquals("00:11:22:33:44:55", device.address)
        assertEquals("OBD-II Adapter", device.name)
        assertEquals(-65, device.rssi)
    }
    
    @Test
    fun `BleDevice with same address should be equal`() {
        val device1 = BleDevice("00:11:22:33:44:55", "Device1", -60)
        val device2 = BleDevice("00:11:22:33:44:55", "Device1", -60)
        assertEquals(device1, device2)
    }
}

class ObdDataTest {
    
    @Test
    fun `ObdData should store raw and parsed data`() {
        val obdData = ObdData(
            raw = "41 0C 1A F8",
            parsed = mapOf("rpm" to "1726")
        )
        
        assertEquals("41 0C 1A F8", obdData.raw)
        assertEquals("1726", obdData.parsed["rpm"])
    }
    
    @Test
    fun `ObdData parsed map can be empty`() {
        val obdData = ObdData(
            raw = "NO DATA",
            parsed = emptyMap()
        )
        
        assertTrue(obdData.parsed.isEmpty())
    }
}
