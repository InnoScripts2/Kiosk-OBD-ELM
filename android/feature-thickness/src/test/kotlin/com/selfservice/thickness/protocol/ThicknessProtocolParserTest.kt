package com.selfservice.thickness.protocol

import com.selfservice.thickness.models.ProtocolFormat
import com.selfservice.thickness.models.ThicknessDeviceConfig
import com.selfservice.thickness.models.ThicknessDeviceError
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Тесты для парсера протокола толщиномера
 * 
 * @since Session 12B
 */
class ThicknessProtocolParserTest {
    
    @Test
    fun `test parse ASCII format - valid`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:123.45\n".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(123.45f, value, 0.01f)
    }
    
    @Test
    fun `test parse ASCII format - without newline`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:99.99".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(99.99f, value, 0.01f)
    }
    
    @Test
    fun `test parse ASCII format - with whitespace`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "  VALUE:150.0  \n".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(150.0f, value, 0.01f)
    }
    
    @Test
    fun `test parse ASCII format - integer value`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:200".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(200.0f, value, 0.01f)
    }
    
    @Test
    fun `test parse ASCII format - invalid format throws exception`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "INVALID:123.45".toByteArray()
        
        assertFailsWith<ThicknessDeviceError.ParseError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test parse ASCII format - missing colon throws exception`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE 123.45".toByteArray()
        
        assertFailsWith<ThicknessDeviceError.ParseError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test parse ASCII format - invalid float throws exception`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:abc".toByteArray()
        
        assertFailsWith<ThicknessDeviceError.ParseError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test parse binary format - valid`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.BINARY)
        val parser = ThicknessProtocolParser(config)
        
        // 123.45f в little-endian байтах
        val data = byteArrayOf(0x66, 0xE6.toByte(), 0xF6.toByte(), 0x42.toByte())
        val value = parser.parse(data)
        
        assertEquals(123.45f, value, 0.01f)
    }
    
    @Test
    fun `test parse binary format - zero value`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.BINARY)
        val parser = ThicknessProtocolParser(config)
        
        val data = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val value = parser.parse(data)
        
        assertEquals(0.0f, value, 0.01f)
    }
    
    @Test
    fun `test parse binary format - invalid size throws exception`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.BINARY)
        val parser = ThicknessProtocolParser(config)
        
        val data = byteArrayOf(0x01, 0x02, 0x03) // Только 3 байта
        
        assertFailsWith<ThicknessDeviceError.ParseError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test parse binary format - too many bytes throws exception`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.BINARY)
        val parser = ThicknessProtocolParser(config)
        
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05) // 5 байт
        
        assertFailsWith<ThicknessDeviceError.ParseError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test auto format detection - ASCII`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.AUTO)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:100.0".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(100.0f, value, 0.01f)
    }
    
    @Test
    fun `test auto format detection - binary`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.AUTO)
        val parser = ThicknessProtocolParser(config)
        
        // Не начинается с 'V', поэтому будет определён как бинарный
        val data = byteArrayOf(0x00, 0x00, 0xC8.toByte(), 0x42.toByte()) // 100.0f
        val value = parser.parse(data)
        
        assertEquals(100.0f, value, 0.01f)
    }
    
    @Test
    fun `test parse empty data throws exception`() {
        val parser = ThicknessProtocolParser()
        
        val data = byteArrayOf()
        
        assertFailsWith<ThicknessDeviceError.ParseError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test parse value out of range - too high`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:3000.0".toByteArray() // Превышает MAX_VALID_MEASUREMENT
        
        assertFailsWith<ThicknessDeviceError.OutOfRangeError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test parse value out of range - negative`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:-10.0".toByteArray()
        
        assertFailsWith<ThicknessDeviceError.OutOfRangeError> {
            parser.parse(data)
        }
    }
    
    @Test
    fun `test encode command - start measurement`() {
        val parser = ThicknessProtocolParser()
        
        val data = parser.encodeCommand(ThicknessProtocolParser.DeviceCommand.START_MEASUREMENT)
        val text = data.decodeToString()
        
        assertEquals("START\n", text)
    }
    
    @Test
    fun `test encode command - stop measurement`() {
        val parser = ThicknessProtocolParser()
        
        val data = parser.encodeCommand(ThicknessProtocolParser.DeviceCommand.STOP_MEASUREMENT)
        val text = data.decodeToString()
        
        assertEquals("STOP\n", text)
    }
    
    @Test
    fun `test encode command - reset`() {
        val parser = ThicknessProtocolParser()
        
        val data = parser.encodeCommand(ThicknessProtocolParser.DeviceCommand.RESET)
        val text = data.decodeToString()
        
        assertEquals("RESET\n", text)
    }
    
    @Test
    fun `test parse multiple valid measurements`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val measurements = listOf(60f, 90f, 120f, 150f, 200f, 350f)
        
        measurements.forEach { expected ->
            val data = "VALUE:$expected".toByteArray()
            val value = parser.parse(data)
            assertEquals(expected, value, 0.01f)
        }
    }
    
    @Test
    fun `test parse edge case - minimum valid value`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:0.0".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(0.0f, value, 0.01f)
    }
    
    @Test
    fun `test parse edge case - maximum valid value`() {
        val config = ThicknessDeviceConfig(protocolFormat = ProtocolFormat.ASCII)
        val parser = ThicknessProtocolParser(config)
        
        val data = "VALUE:2000.0".toByteArray()
        val value = parser.parse(data)
        
        assertEquals(2000.0f, value, 0.01f)
    }
}
