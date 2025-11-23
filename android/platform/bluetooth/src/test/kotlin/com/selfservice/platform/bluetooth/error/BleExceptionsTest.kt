package com.selfservice.platform.bluetooth.error

import com.selfservice.platform.bluetooth.ble.BleDevice
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit-тесты для BLE исключений
 * 
 * @since Session 07B
 */
class BleExceptionsTest {
    
    private val testDevice = BleDevice("00:11:22:33:44:55", "Test Device", rssi = -60)
    
    @Test
    fun `DeviceNotFound должен содержать корректные данные`() {
        val exception = BleConnectionException.DeviceNotFound("00:11:22:33:44:55")
        
        assertEquals("BLE_DEVICE_NOT_FOUND", exception.errorCode)
        assertTrue(exception.userMessage.contains("не найдено"))
        assertTrue(exception.message?.contains("00:11:22:33:44:55") == true)
    }
    
    @Test
    fun `ConnectionTimeout должен содержать информацию о таймауте`() {
        val exception = BleConnectionException.ConnectionTimeout(testDevice, 5000L)
        
        assertEquals("BLE_CONNECTION_TIMEOUT", exception.errorCode)
        assertTrue(exception.userMessage.contains("подключиться"))
        assertTrue(exception.message?.contains("5000") == true)
        assertEquals(testDevice, exception.device)
    }
    
    @Test
    fun `ConnectionLost должен содержать причину`() {
        val exception = BleConnectionException.ConnectionLost(testDevice, "Connection reset")
        
        assertEquals("BLE_CONNECTION_LOST", exception.errorCode)
        assertTrue(exception.userMessage.contains("потеряно"))
        assertTrue(exception.message?.contains("Connection reset") == true)
        assertEquals(testDevice, exception.device)
    }
    
    @Test
    fun `GattError должен содержать статус и операцию`() {
        val exception = BleConnectionException.GattError(testDevice, 133, "чтение")
        
        assertEquals("BLE_GATT_ERROR_133", exception.errorCode)
        assertTrue(exception.userMessage.contains("133"))
        assertTrue(exception.message?.contains("чтение") == true)
        assertEquals(133, exception.gattStatus)
    }
    
    @Test
    fun `AlreadyConnected должен быть понятным`() {
        val exception = BleConnectionException.AlreadyConnected(testDevice)
        
        assertEquals("BLE_ALREADY_CONNECTED", exception.errorCode)
        assertTrue(exception.userMessage.contains("подключено"))
    }
    
    @Test
    fun `NotConnected должен указывать операцию`() {
        val exception = BleConnectionException.NotConnected("readCharacteristic")
        
        assertEquals("BLE_NOT_CONNECTED", exception.errorCode)
        assertTrue(exception.message?.contains("readCharacteristic") == true)
    }
    
    @Test
    fun `ScanAlreadyStarted должен быть корректным синглтоном`() {
        val exception1 = BleScanException.ScanAlreadyStarted
        val exception2 = BleScanException.ScanAlreadyStarted
        
        assertSame(exception1, exception2)
        assertEquals("BLE_SCAN_ALREADY_STARTED", exception1.errorCode)
    }
    
    @Test
    fun `ScanStartFailed должен содержать причину`() {
        val exception = BleScanException.ScanStartFailed("Нет разрешений")
        
        assertEquals("BLE_SCAN_START_FAILED", exception.errorCode)
        assertTrue(exception.message?.contains("Нет разрешений") == true)
    }
    
    @Test
    fun `CharacteristicNotFound должен содержать UUIDs`() {
        val serviceUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val charUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        
        val exception = BleCharacteristicException.CharacteristicNotFound(serviceUuid, charUuid)
        
        assertEquals("BLE_CHARACTERISTIC_NOT_FOUND", exception.errorCode)
        assertTrue(exception.message?.contains(serviceUuid.toString()) == true)
        assertTrue(exception.message?.contains(charUuid.toString()) == true)
        assertEquals(serviceUuid, exception.serviceUuid)
        assertEquals(charUuid, exception.characteristicUuid)
    }
    
    @Test
    fun `OperationNotSupported должен указывать операцию`() {
        val charUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val exception = BleCharacteristicException.OperationNotSupported(charUuid, "WRITE")
        
        assertEquals("BLE_OPERATION_NOT_SUPPORTED", exception.errorCode)
        assertTrue(exception.message?.contains("WRITE") == true)
    }
    
    @Test
    fun `ReadFailed должен содержать причину`() {
        val charUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val exception = BleCharacteristicException.ReadFailed(charUuid, "Таймаут")
        
        assertEquals("BLE_READ_FAILED", exception.errorCode)
        assertTrue(exception.message?.contains("Таймаут") == true)
    }
    
    @Test
    fun `WriteFailed должен содержать причину`() {
        val charUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val exception = BleCharacteristicException.WriteFailed(charUuid, "Устройство занято")
        
        assertEquals("BLE_WRITE_FAILED", exception.errorCode)
        assertTrue(exception.message?.contains("Устройство занято") == true)
    }
    
    @Test
    fun `OperationTimeout должен содержать детали`() {
        val charUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val exception = BleCharacteristicException.OperationTimeout(charUuid, "READ", 3000L)
        
        assertEquals("BLE_OPERATION_TIMEOUT", exception.errorCode)
        assertTrue(exception.message?.contains("READ") == true)
        assertTrue(exception.message?.contains("3000") == true)
    }
    
    @Test
    fun `AdapterNotAvailable должен быть синглтоном`() {
        val exception1 = BleAdapterException.AdapterNotAvailable
        val exception2 = BleAdapterException.AdapterNotAvailable
        
        assertSame(exception1, exception2)
        assertEquals("BLE_ADAPTER_NOT_AVAILABLE", exception1.errorCode)
    }
    
    @Test
    fun `AdapterOff должен быть синглтоном`() {
        val exception1 = BleAdapterException.AdapterOff
        val exception2 = BleAdapterException.AdapterOff
        
        assertSame(exception1, exception2)
        assertEquals("BLE_ADAPTER_OFF", exception1.errorCode)
    }
    
    @Test
    fun `PermissionDenied должен содержать имя разрешения`() {
        val exception = BleAdapterException.PermissionDenied("android.permission.BLUETOOTH_SCAN")
        
        assertEquals("BLE_PERMISSION_DENIED", exception.errorCode)
        assertTrue(exception.message?.contains("BLUETOOTH_SCAN") == true)
        assertEquals("android.permission.BLUETOOTH_SCAN", exception.permission)
    }
    
    @Test
    fun `BleGeneralException должен поддерживать вложенные исключения`() {
        val cause = RuntimeException("Original error")
        val exception = BleGeneralException("Общая ошибка", cause)
        
        assertEquals("BLE_GENERAL_ERROR", exception.errorCode)
        assertEquals(cause, exception.cause)
        assertEquals(cause, exception.originalCause)
    }
}
