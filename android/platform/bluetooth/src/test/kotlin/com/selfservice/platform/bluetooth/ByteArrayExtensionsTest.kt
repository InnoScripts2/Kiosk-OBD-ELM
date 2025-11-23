package com.selfservice.platform.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.ByteOrder

/**
 * Тесты для ByteArray extensions
 */
class ByteArrayExtensionsTest {

    @Test
    fun `test byte to hex string`() {
        val byte: Byte = 0x0F
        assertEquals("0F", byte.asHexString())
        
        val byte2: Byte = 0x5A.toByte()
        assertEquals("5A", byte2.asHexString())
    }

    @Test
    fun `test byte array to hex string`() {
        val bytes = byteArrayOf(0x41, 0x0C, 0x1F, 0xA0.toByte())
        assertEquals("410C1FA0", bytes.asHexString())
    }

    @Test
    fun `test byte array to formatted hex string with separator`() {
        val bytes = byteArrayOf(0x41, 0x0C, 0x1F)
        assertEquals("41:0C:1F", bytes.asFormattedHexString(":"))
        assertEquals("41 0C 1F", bytes.asFormattedHexString(" "))
    }

    @Test
    fun `test getUInt8`() {
        val bytes = byteArrayOf(0x00, 0x41, 0xFF.toByte())
        assertEquals(0u, bytes.getUInt8(0u))
        assertEquals(0x41u, bytes.getUInt8(1u))
        assertEquals(0xFFu, bytes.getUInt8(2u))
    }

    @Test
    fun `test getInt8`() {
        val bytes = byteArrayOf(0x00, 0x7F, 0xFF.toByte())
        assertEquals(0, bytes.getInt8(0u))
        assertEquals(127, bytes.getInt8(1u))
        assertEquals(-1, bytes.getInt8(2u))
    }

    @Test
    fun `test getUInt16 little endian`() {
        val bytes = byteArrayOf(0x34, 0x12) // Little endian: 0x1234
        assertEquals(0x1234u.toUShort(), bytes.getUInt16(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt16 big endian`() {
        val bytes = byteArrayOf(0x12, 0x34) // Big endian: 0x1234
        assertEquals(0x1234u.toUShort(), bytes.getUInt16(0u, ByteOrder.BIG_ENDIAN))
    }

    @Test
    fun `test getInt16 positive value`() {
        val bytes = byteArrayOf(0x00, 0x10) // Little endian: 0x1000 = 4096
        assertEquals(4096, bytes.getInt16(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt32 little endian`() {
        val bytes = byteArrayOf(0x78, 0x56, 0x34, 0x12) // Little endian: 0x12345678
        assertEquals(0x12345678u, bytes.getUInt32(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt32 big endian`() {
        val bytes = byteArrayOf(0x12, 0x34, 0x56, 0x78) // Big endian: 0x12345678
        assertEquals(0x12345678u, bytes.getUInt32(0u, ByteOrder.BIG_ENDIAN))
    }

    @Test
    fun `test getInt32`() {
        val bytes = byteArrayOf(0x00, 0x00, 0x00, 0x10) // Little endian: 0x10000000
        assertEquals(0x10000000, bytes.getInt32(0u, ByteOrder.LITTLE_ENDIAN))
    }
}
