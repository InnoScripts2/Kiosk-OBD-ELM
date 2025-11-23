package com.selfservice.platform.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.ByteOrder

/**
 * Тесты для BluetoothBytesParser из blessed-kotlin
 */
class BluetoothBytesParserTest {

    @Test
    fun `test sequential reading of UInt8`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val parser = BluetoothBytesParser(bytes)
        
        assertEquals(0x01u, parser.getUInt8())
        assertEquals(0x02u, parser.getUInt8())
        assertEquals(0x03u, parser.getUInt8())
        assertEquals(3, parser.offset)
    }

    @Test
    fun `test sequential reading of Int8`() {
        val bytes = byteArrayOf(0x7F, 0xFF.toByte())
        val parser = BluetoothBytesParser(bytes)
        
        assertEquals(127, parser.getInt8())
        assertEquals(-1, parser.getInt8())
    }

    @Test
    fun `test sequential reading of UInt16 little endian`() {
        val bytes = byteArrayOf(0x34, 0x12, 0x78, 0x56)
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0x1234u.toUShort(), parser.getUInt16())
        assertEquals(0x5678u.toUShort(), parser.getUInt16())
        assertEquals(4, parser.offset)
    }

    @Test
    fun `test sequential reading of Int16`() {
        val bytes = byteArrayOf(0x00, 0x10, 0xFF.toByte(), 0xFF.toByte())
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(4096, parser.getInt16())
        assertEquals(-1, parser.getInt16())
    }

    @Test
    fun `test sequential reading of UInt24`() {
        val bytes = byteArrayOf(0x78, 0x56, 0x12)
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0x125678u, parser.getUInt24())
        assertEquals(3, parser.offset)
    }

    @Test
    fun `test sequential reading of UInt32`() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12)
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0x12345678u, parser.getUInt32())
        assertEquals(4, parser.offset)
    }

    @Test
    fun `test sequential reading of Int32`() {
        val bytes = byteArrayOf(0x00, 0x00, 0x00, 0x10)
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0x10000000, parser.getInt32())
        assertEquals(4, parser.offset)
    }

    @Test
    fun `test sequential reading of UInt48`() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12, 0x90.toByte(), 0xAB.toByte())
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0xAB9012345678uL, parser.getUInt48())
        assertEquals(6, parser.offset)
    }

    @Test
    fun `test sequential reading of UInt64`() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12, 0x90.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0xEFCDAB9012345678uL, parser.getUInt64())
        assertEquals(8, parser.offset)
    }

    @Test
    fun `test getString with length`() {
        val bytes = "HelloWorld".toByteArray()
        val parser = BluetoothBytesParser(bytes)
        
        assertEquals("Hello", parser.getString(5))
        assertEquals(5, parser.offset)
    }

    @Test
    fun `test getString without length`() {
        val bytes = "Hello".toByteArray()
        val parser = BluetoothBytesParser(bytes)
        
        assertEquals("Hello", parser.getString())
        assertEquals(5, parser.offset)
    }

    @Test
    fun `test mixed type reading`() {
        // Create a complex byte array: 1 byte + 2 bytes + 4 bytes
        val bytes = byteArrayOf(
            0x01,                           // UInt8
            0x34, 0x12,                     // UInt16 little endian (0x1234)
            0x78, 0x56, 0x34, 0x12          // UInt32 little endian (0x12345678)
        )
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0x01u, parser.getUInt8())
        assertEquals(0x1234u.toUShort(), parser.getUInt16())
        assertEquals(0x12345678u, parser.getUInt32())
        assertEquals(7, parser.offset)
    }

    @Test
    fun `test getSFloat`() {
        // SFloat with mantissa=100, exponent=-2 => 100 * 10^-2 = 1.0
        val bytes = byteArrayOf(0x64, 0xF0.toByte()) // Little endian
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        val result = parser.getSFloat()
        assertEquals(1.0, result, 0.01)
        assertEquals(2, parser.offset)
    }

    @Test
    fun `test big endian reading`() {
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.BIG_ENDIAN)
        
        assertEquals(0x1234u.toUShort(), parser.getUInt16())
        assertEquals(0x5678u.toUShort(), parser.getUInt16())
    }
}
