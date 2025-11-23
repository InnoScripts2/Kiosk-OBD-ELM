package com.selfservice.platform.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.ByteOrder

/**
 * Тесты для BluetoothBytesParser
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
    fun `test sequential reading of UInt32`() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12)
        val parser = BluetoothBytesParser(bytes, byteOrder = ByteOrder.LITTLE_ENDIAN)
        
        assertEquals(0x12345678u, parser.getUInt32())
        assertEquals(4, parser.offset)
    }

    @Test
    fun `test getString`() {
        val bytes = "Hello".toByteArray()
        val parser = BluetoothBytesParser(bytes)
        
        assertEquals("Hello", parser.getString(5))
        assertEquals(5, parser.offset)
    }

    @Test
    fun `test getRemainingBytes`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val parser = BluetoothBytesParser(bytes)
        
        parser.getUInt8() // Read first byte
        val remaining = parser.getRemainingBytes()
        
        assertEquals(3, remaining.size)
        assertEquals(0x02.toByte(), remaining[0])
        assertEquals(4, parser.offset)
    }

    @Test
    fun `test remainingBytes`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val parser = BluetoothBytesParser(bytes)
        
        assertEquals(3, parser.remainingBytes())
        parser.getUInt8()
        assertEquals(2, parser.remainingBytes())
        parser.getUInt8()
        assertEquals(1, parser.remainingBytes())
    }

    @Test
    fun `test hasMoreBytes`() {
        val bytes = byteArrayOf(0x01, 0x02)
        val parser = BluetoothBytesParser(bytes)
        
        assertTrue(parser.hasMoreBytes())
        parser.getUInt8()
        assertTrue(parser.hasMoreBytes())
        parser.getUInt8()
        assertFalse(parser.hasMoreBytes())
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
        assertFalse(parser.hasMoreBytes())
    }
}
