package com.selfservice.platform.bluetooth.ble

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit-тесты для BLE моделей данных
 * 
 * @since Session 07B
 */
class BleModelsTest {
    
    @Test
    fun `BleDevice должен корректно вычислять signalStrength`() {
        val device1 = BleDevice("00:11:22:33:44:55", "Test", rssi = -50)
        assertEquals(100, device1.signalStrength)
        
        val device2 = BleDevice("00:11:22:33:44:55", "Test", rssi = -65)
        assertEquals(80, device2.signalStrength)
        
        val device3 = BleDevice("00:11:22:33:44:55", "Test", rssi = -75)
        assertEquals(60, device3.signalStrength)
        
        val device4 = BleDevice("00:11:22:33:44:55", "Test", rssi = -85)
        assertEquals(40, device4.signalStrength)
        
        val device5 = BleDevice("00:11:22:33:44:55", "Test", rssi = -95)
        assertEquals(20, device5.signalStrength)
        
        val device6 = BleDevice("00:11:22:33:44:55", "Test", rssi = -100)
        assertEquals(0, device6.signalStrength)
    }
    
    @Test
    fun `BleDevice должен корректно определять signalQuality`() {
        val excellent = BleDevice("00:11:22:33:44:55", "Test", rssi = -55)
        assertEquals(SignalQuality.EXCELLENT, excellent.signalQuality)
        
        val good = BleDevice("00:11:22:33:44:55", "Test", rssi = -65)
        assertEquals(SignalQuality.GOOD, good.signalQuality)
        
        val fair = BleDevice("00:11:22:33:44:55", "Test", rssi = -75)
        assertEquals(SignalQuality.FAIR, fair.signalQuality)
        
        val poor = BleDevice("00:11:22:33:44:55", "Test", rssi = -85)
        assertEquals(SignalQuality.POOR, poor.signalQuality)
    }
    
    @Test
    fun `BleDevice должен корректно определять OBD адаптеры`() {
        val obdUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val obdDevice = BleDevice(
            "00:11:22:33:44:55", 
            "OBD Adapter", 
            rssi = -60,
            services = listOf(obdUuid)
        )
        assertTrue(obdDevice.isObdAdapter)
        
        val normalDevice = BleDevice(
            "00:11:22:33:44:55", 
            "Normal Device", 
            rssi = -60,
            services = listOf(UUID.fromString("00001234-0000-1000-8000-00805f9b34fb"))
        )
        assertFalse(normalDevice.isObdAdapter)
    }
    
    @Test
    fun `BleDevice displayName должен возвращать имя или дефолт`() {
        val withName = BleDevice("00:11:22:33:44:55", "My Device", rssi = -60)
        assertEquals("My Device", withName.displayName)
        
        val withoutName = BleDevice("00:11:22:33:44:55", null, rssi = -60)
        assertEquals("Неизвестное устройство", withoutName.displayName)
    }
    
    @Test
    fun `BleDevice id должен быть равен address`() {
        val device = BleDevice("00:11:22:33:44:55", "Test", rssi = -60)
        assertEquals("00:11:22:33:44:55", device.id)
    }
    
    @Test
    fun `BleScanConfig должен иметь корректные значения по умолчанию`() {
        val config = BleScanConfig()
        
        assertTrue(config.serviceUuids.isEmpty())
        assertNull(config.nameFilter)
        assertEquals(-100, config.minRssi)
        assertEquals(0L, config.scanDuration)
        assertEquals(BleScanMode.LOW_LATENCY, config.scanMode)
    }
    
    @Test
    fun `BleScanConfig должен поддерживать кастомные фильтры`() {
        val serviceUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val nameFilter = Regex("OBD.*")
        
        val config = BleScanConfig(
            serviceUuids = listOf(serviceUuid),
            nameFilter = nameFilter,
            minRssi = -80,
            scanDuration = 10000L,
            scanMode = BleScanMode.BALANCED
        )
        
        assertEquals(1, config.serviceUuids.size)
        assertEquals(serviceUuid, config.serviceUuids.first())
        assertEquals(nameFilter, config.nameFilter)
        assertEquals(-80, config.minRssi)
        assertEquals(10000L, config.scanDuration)
        assertEquals(BleScanMode.BALANCED, config.scanMode)
    }
    
    @Test
    fun `BleScanResult должен содержать корректные данные`() {
        val device = BleDevice("00:11:22:33:44:55", "Test", rssi = -60)
        val serviceUuids = listOf(UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"))
        val timestamp = System.currentTimeMillis()
        
        val result = BleScanResult(
            device = device,
            serviceUuids = serviceUuids,
            seenAtMillis = timestamp
        )
        
        assertEquals(device, result.device)
        assertEquals(serviceUuids, result.serviceUuids)
        assertEquals(timestamp, result.seenAtMillis)
    }
    
    @Test
    fun `BleConnectionState переходы должны быть логичными`() {
        // Проверяем все состояния
        val states = BleConnectionState.values()
        assertEquals(5, states.size)
        
        assertTrue(BleConnectionState.DISCONNECTED in states)
        assertTrue(BleConnectionState.CONNECTING in states)
        assertTrue(BleConnectionState.DISCOVERING_SERVICES in states)
        assertTrue(BleConnectionState.CONNECTED in states)
        assertTrue(BleConnectionState.DISCONNECTING in states)
    }
    
    @Test
    fun `BleAdapterState должен содержать все состояния адаптера`() {
        val states = BleAdapterState.values()
        assertEquals(5, states.size)
        
        assertTrue(BleAdapterState.OFF in states)
        assertTrue(BleAdapterState.TURNING_ON in states)
        assertTrue(BleAdapterState.ON in states)
        assertTrue(BleAdapterState.TURNING_OFF in states)
        assertTrue(BleAdapterState.UNAVAILABLE in states)
    }
}
