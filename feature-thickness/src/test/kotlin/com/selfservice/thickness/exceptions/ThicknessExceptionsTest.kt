package com.selfservice.thickness.exceptions

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Тесты для специализированных исключений толщиномера
 * 
 * @since Session 13B
 */
class ThicknessExceptionsTest {
    
    @Test
    fun `ThicknessConnectionException should contain device address`() {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val exception = ThicknessConnectionException(deviceAddress)
        
        assertEquals(deviceAddress, exception.deviceAddress)
        assertTrue(exception.message!!.contains(deviceAddress))
    }
    
    @Test
    fun `ThicknessConnectionException timeout factory should create timeout exception`() {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val timeoutMs = 5000L
        
        val exception = ThicknessConnectionException.timeout(deviceAddress, timeoutMs)
        
        assertEquals(deviceAddress, exception.deviceAddress)
        assertTrue(exception.message!!.contains("timeout"))
        assertTrue(exception.message!!.contains(timeoutMs.toString()))
    }
    
    @Test
    fun `ThicknessConnectionException refused factory should create refused exception`() {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val reason = "Device busy"
        
        val exception = ThicknessConnectionException.refused(deviceAddress, reason)
        
        assertEquals(deviceAddress, exception.deviceAddress)
        assertTrue(exception.message!!.contains("refused"))
        assertTrue(exception.message!!.contains(reason))
    }
    
    @Test
    fun `ThicknessConnectionException disconnected factory should create disconnected exception`() {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        
        val exception = ThicknessConnectionException.disconnected(deviceAddress)
        
        assertEquals(deviceAddress, exception.deviceAddress)
        assertTrue(exception.message!!.contains("disconnected"))
    }
    
    @Test
    fun `MeasurementTimeoutException should contain zone information`() {
        val zoneIndex = 5
        val zoneName = "Hood_Front_Left"
        val timeoutMs = 30000L
        
        val exception = MeasurementTimeoutException(zoneIndex, zoneName, timeoutMs)
        
        assertEquals(zoneIndex, exception.zoneIndex)
        assertEquals(zoneName, exception.zoneName)
        assertEquals(timeoutMs, exception.timeoutMs)
        assertTrue(exception.message!!.contains(zoneName))
    }
    
    @Test
    fun `MeasurementTimeoutException noResponse factory should create appropriate exception`() {
        val zoneIndex = 10
        val zoneName = "Roof_Center"
        val timeoutMs = 30000L
        
        val exception = MeasurementTimeoutException.noResponse(zoneIndex, zoneName, timeoutMs)
        
        assertEquals(zoneIndex, exception.zoneIndex)
        assertEquals(zoneName, exception.zoneName)
        assertTrue(exception.message!!.contains("No response"))
    }
    
    @Test
    fun `MeasurementTimeoutException tooSlow factory should create appropriate exception`() {
        val zoneIndex = 15
        val zoneName = "Door_Front_Right"
        val actualMs = 35000L
        val limitMs = 30000L
        
        val exception = MeasurementTimeoutException.tooSlow(zoneIndex, zoneName, actualMs, limitMs)
        
        assertEquals(zoneIndex, exception.zoneIndex)
        assertEquals(zoneName, exception.zoneName)
        assertTrue(exception.message!!.contains(actualMs.toString()))
        assertTrue(exception.message!!.contains(limitMs.toString()))
    }
    
    @Test
    fun `ThicknessProtocolException should contain raw data`() {
        val rawData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val expectedFormat = "ASCII"
        
        val exception = ThicknessProtocolException(rawData, expectedFormat)
        
        assertEquals(rawData.size, exception.rawData.size)
        assertEquals(expectedFormat, exception.expectedFormat)
    }
    
    @Test
    fun `ThicknessProtocolException invalidFormat factory should create appropriate exception`() {
        val rawData = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val expectedFormat = "VALUE:123.45"
        val reason = "Missing colon separator"
        
        val exception = ThicknessProtocolException.invalidFormat(rawData, expectedFormat, reason)
        
        assertTrue(exception.message!!.contains(reason))
        assertTrue(exception.message!!.contains(expectedFormat))
    }
    
    @Test
    fun `ThicknessProtocolException incompleteData factory should create appropriate exception`() {
        val rawData = byteArrayOf(0x01, 0x02)
        val expectedBytes = 4
        val actualBytes = 2
        
        val exception = ThicknessProtocolException.incompleteData(rawData, expectedBytes, actualBytes)
        
        assertTrue(exception.message!!.contains(expectedBytes.toString()))
        assertTrue(exception.message!!.contains(actualBytes.toString()))
        assertTrue(exception.message!!.contains("Incomplete"))
    }
    
    @Test
    fun `ThicknessProtocolException unknownFormat factory should include data preview`() {
        val rawData = byteArrayOf(0x12, 0x34, 0x56, 0x78, 0x9A.toByte())
        
        val exception = ThicknessProtocolException.unknownFormat(rawData)
        
        assertTrue(exception.message!!.contains("12"))
        assertTrue(exception.message!!.contains("34"))
        assertTrue(exception.message!!.contains("Unknown"))
    }
    
    @Test
    fun `ThicknessValueOutOfRangeException should contain value and zone info`() {
        val value = 2500f
        val zoneIndex = 20
        val zoneName = "Fender_Front_Left"
        val minValid = 0f
        val maxValid = 2000f
        
        val exception = ThicknessValueOutOfRangeException(
            value, zoneIndex, zoneName, minValid, maxValid
        )
        
        assertEquals(value, exception.value)
        assertEquals(zoneIndex, exception.zoneIndex)
        assertEquals(zoneName, exception.zoneName)
        assertEquals(minValid, exception.minValid)
        assertEquals(maxValid, exception.maxValid)
    }
    
    @Test
    fun `ThicknessValueOutOfRangeException should detect too low values`() {
        val exception = ThicknessValueOutOfRangeException(
            value = -10f,
            zoneIndex = 0,
            zoneName = "Test",
            minValid = 0f,
            maxValid = 2000f
        )
        
        assertTrue(exception.isTooLow())
        assertFalse(exception.isTooHigh())
    }
    
    @Test
    fun `ThicknessValueOutOfRangeException should detect too high values`() {
        val exception = ThicknessValueOutOfRangeException(
            value = 2500f,
            zoneIndex = 0,
            zoneName = "Test",
            minValid = 0f,
            maxValid = 2000f
        )
        
        assertFalse(exception.isTooLow())
        assertTrue(exception.isTooHigh())
    }
    
    @Test
    fun `ThicknessValueOutOfRangeException negative factory should create appropriate exception`() {
        val value = -50f
        val zoneIndex = 5
        val zoneName = "Hood_Center"
        
        val exception = ThicknessValueOutOfRangeException.negative(value, zoneIndex, zoneName)
        
        assertEquals(value, exception.value)
        assertTrue(exception.message!!.contains("Negative"))
        assertTrue(exception.isTooLow())
    }
    
    @Test
    fun `ThicknessValueOutOfRangeException infinite factory should create appropriate exception`() {
        val zoneIndex = 10
        val zoneName = "Roof_Rear"
        
        val exception = ThicknessValueOutOfRangeException.infinite(zoneIndex, zoneName)
        
        assertTrue(exception.value.isInfinite())
        assertTrue(exception.message!!.contains("Infinite"))
    }
    
    @Test
    fun `ThicknessValueOutOfRangeException notANumber factory should create appropriate exception`() {
        val zoneIndex = 15
        val zoneName = "Door_Rear_Left"
        
        val exception = ThicknessValueOutOfRangeException.notANumber(zoneIndex, zoneName)
        
        assertTrue(exception.value.isNaN())
        assertTrue(exception.message!!.contains("NaN"))
    }
    
    @Test
    fun `MaxReconnectAttemptsExceededException should contain attempt count`() {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val attempts = 3
        val lastError = RuntimeException("Test error")
        
        val exception = MaxReconnectAttemptsExceededException(deviceAddress, attempts, lastError)
        
        assertEquals(deviceAddress, exception.deviceAddress)
        assertEquals(attempts, exception.attempts)
        assertEquals(lastError, exception.lastError)
        assertTrue(exception.message!!.contains(attempts.toString()))
    }
    
    @Test
    fun `MaxReconnectAttemptsExceededException withHistory should include error summary`() {
        val deviceAddress = "AA:BB:CC:DD:EE:FF"
        val attempts = 3
        val errors = listOf(
            RuntimeException("Error 1"),
            RuntimeException("Error 2"),
            RuntimeException("Error 3")
        )
        
        val exception = MaxReconnectAttemptsExceededException.withHistory(
            deviceAddress, attempts, errors
        )
        
        assertEquals(deviceAddress, exception.deviceAddress)
        assertEquals(attempts, exception.attempts)
        assertEquals(errors.last(), exception.lastError)
        assertTrue(exception.message!!.contains("Error 1"))
        assertTrue(exception.message!!.contains("Error 2"))
        assertTrue(exception.message!!.contains("Error 3"))
    }
    
    @Test
    fun `BleStackException should contain operation information`() {
        val operation = "read characteristic"
        val bleErrorCode = 133
        
        val exception = BleStackException(operation, bleErrorCode)
        
        assertEquals(operation, exception.operation)
        assertEquals(bleErrorCode, exception.bleErrorCode)
        assertTrue(exception.message!!.contains(operation))
        assertTrue(exception.message!!.contains(bleErrorCode.toString()))
    }
    
    @Test
    fun `BleStackException characteristicError factory should create appropriate exception`() {
        val operation = "write"
        val characteristicUuid = "0000ffe1-0000-1000-8000-00805f9b34fb"
        val bleErrorCode = 133
        
        val exception = BleStackException.characteristicError(
            operation, characteristicUuid, bleErrorCode
        )
        
        assertEquals(operation, exception.operation)
        assertEquals(bleErrorCode, exception.bleErrorCode)
        assertTrue(exception.message!!.contains(operation))
        assertTrue(exception.message!!.contains(characteristicUuid))
        assertTrue(exception.message!!.contains(bleErrorCode.toString()))
    }
    
    @Test
    fun `BleStackException serviceError factory should create appropriate exception`() {
        val operation = "discover"
        val serviceUuid = "0000ffe0-0000-1000-8000-00805f9b34fb"
        val bleErrorCode = 129
        
        val exception = BleStackException.serviceError(
            operation, serviceUuid, bleErrorCode
        )
        
        assertEquals(operation, exception.operation)
        assertEquals(bleErrorCode, exception.bleErrorCode)
        assertTrue(exception.message!!.contains(operation))
        assertTrue(exception.message!!.contains(serviceUuid))
    }
    
    @Test
    fun `All exceptions should have non-null messages`() {
        val exceptions = listOf(
            ThicknessConnectionException("AA:BB:CC:DD:EE:FF"),
            MeasurementTimeoutException(0, "Test", 5000L),
            ThicknessProtocolException(byteArrayOf(), "test"),
            ThicknessValueOutOfRangeException(100f, 0, "Test", 0f, 200f),
            MaxReconnectAttemptsExceededException("AA:BB:CC:DD:EE:FF", 3),
            BleStackException("test")
        )
        
        exceptions.forEach { exception ->
            assertNotNull(exception.message, "Exception ${exception::class.simpleName} should have a message")
            assertTrue(exception.message!!.isNotEmpty(), "Exception ${exception::class.simpleName} message should not be empty")
        }
    }
    
    @Test
    fun `All exceptions should be subclasses of ThicknessDeviceError`() {
        val exceptions = listOf(
            ThicknessConnectionException("AA:BB:CC:DD:EE:FF"),
            MeasurementTimeoutException(0, "Test", 5000L),
            ThicknessProtocolException(byteArrayOf(), "test"),
            ThicknessValueOutOfRangeException(100f, 0, "Test", 0f, 200f),
            MaxReconnectAttemptsExceededException("AA:BB:CC:DD:EE:FF", 3),
            BleStackException("test")
        )
        
        exceptions.forEach { exception ->
            assertTrue(
                exception is com.selfservice.thickness.models.ThicknessDeviceError,
                "Exception ${exception::class.simpleName} should be a subclass of ThicknessDeviceError"
            )
        }
    }
}
