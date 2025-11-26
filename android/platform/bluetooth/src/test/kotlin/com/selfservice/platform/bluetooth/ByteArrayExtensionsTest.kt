package com.selfservice.platform.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.ByteOrder

/**
 * Тесты для ByteArray extensions из blessed-kotlin
 */
class ByteArrayExtensionsTest {

    @Test
    fun `test byte to hex string`() {
        val byte: Byte = 0x0F.toByte()
        assertEquals("0F", byte.asHexString())
        
        val byte2: Byte = 0x5A.toByte()
        assertEquals("5A", byte2.asHexString())
        
        val byte3: Byte = 0x00.toByte()
        assertEquals("00", byte3.asHexString())
    }

    @Test
    fun `test byte array to hex string`() {
        val bytes = byteArrayOf(0x41.toByte(), 0x0C.toByte(), 0x1F.toByte(), 0xA0.toByte())
        assertEquals("410C1FA0", bytes.asHexString())
    }

    @Test
    fun `test byte array to formatted hex string with separator`() {
        val bytes = byteArrayOf(0x41.toByte(), 0x0C.toByte(), 0x1F.toByte())
        assertEquals("41:0C:1F", bytes.asFormattedHexString(":"))
        assertEquals("41 0C 1F", bytes.asFormattedHexString(" "))
    }

    @Test
    fun `test getUInt8`() {
        val bytes = byteArrayOf(0x00.toByte(), 0x41.toByte(), 0xFF.toByte())
        assertEquals(0u, bytes.getUInt8(0u))
        assertEquals(0x41u, bytes.getUInt8(1u))
        assertEquals(0xFFu, bytes.getUInt8(2u))
    }

    @Test
    fun `test getInt8`() {
        val bytes = byteArrayOf(0x00.toByte(), 0x7F.toByte(), 0xFF.toByte())
        assertEquals(0, bytes.getInt8(0u))
        assertEquals(127, bytes.getInt8(1u))
        assertEquals(-1, bytes.getInt8(2u))
    }

    @Test
    fun `test getUInt16 little endian`() {
        val bytes = byteArrayOf(0x34.toByte(), 0x12.toByte()) // Little endian: 0x1234
        assertEquals(0x1234u.toUShort(), bytes.getUInt16(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt16 big endian`() {
        val bytes = byteArrayOf(0x12.toByte(), 0x34.toByte()) // Big endian: 0x1234
        assertEquals(0x1234u.toUShort(), bytes.getUInt16(0u, ByteOrder.BIG_ENDIAN))
    }

    @Test
    fun `test getInt16`() {
        val bytes = byteArrayOf(0x00.toByte(), 0x10.toByte()) // Little endian: 0x1000 = 4096
        assertEquals(4096, bytes.getInt16(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt24 little endian`() {
        val bytes = byteArrayOf(0x78.toByte(), 0x56.toByte(), 0x12.toByte()) // Little endian: 0x125678
        assertEquals(0x125678u, bytes.getUInt24(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt32 little endian`() {
        val bytes = byteArrayOf(0x78.toByte(), 0x56.toByte(), 0x34.toByte(), 0x12.toByte()) // Little endian: 0x12345678
        assertEquals(0x12345678u, bytes.getUInt32(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt32 big endian`() {
        val bytes = byteArrayOf(0x12.toByte(), 0x34.toByte(), 0x56.toByte(), 0x78.toByte()) // Big endian: 0x12345678
        assertEquals(0x12345678u, bytes.getUInt32(0u, ByteOrder.BIG_ENDIAN))
    }

    @Test
    fun `test getInt32`() {
        val bytes = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte()) // Little endian: 0x10000000
        assertEquals(0x10000000, bytes.getInt32(0u, ByteOrder.LITTLE_ENDIAN))
    }

    @Test
    fun `test getUInt48`() {
        val bytes = byteArrayOf(0x78.toByte(), 0x56.toByte(), 0x34.toByte(), 0x12.toByte(), 0x90.toByte(), 0xAB.toByte())
        val result = bytes.getUInt48(0u, ByteOrder.LITTLE_ENDIAN)
        assertEquals(0xAB9012345678uL, result)
    }

    @Test
    fun `test getUInt64`() {
        val bytes = byteArrayOf(0x78.toByte(), 0x56.toByte(), 0x34.toByte(), 0x12.toByte(), 0x90.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
        val result = bytes.getUInt64(0u, ByteOrder.LITTLE_ENDIAN)
        assertEquals(0xEFCDAB9012345678uL, result)
    }

    @Test
    fun `test getString`() {
        val bytes = "Hello\u0000World".toByteArray()
        assertEquals("Hello", bytes.getString(0u))
    }

    @Test
    fun `test byteArrayOf from hex string`() {
        val hex = "410C1FA0"
        val bytes = byteArrayOf(hex)
        assertEquals(4, bytes.size)
        assertEquals(0x41.toByte(), bytes[0])
        assertEquals(0x0C.toByte(), bytes[1])
        assertEquals(0x1F.toByte(), bytes[2])
        assertEquals(0xA0.toByte(), bytes[3])
    }

    @Test
    fun `test UShort asByteArray`() {
        val value: UShort = 0x1234u
        val bytes = value.asByteArray(ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, bytes.size)
        assertEquals(0x34.toByte(), bytes[0])
        assertEquals(0x12.toByte(), bytes[1])
    }

    @Test
    fun `test UInt asByteArray`() {
        val value: UInt = 0x12345678u
        val bytes = value.asByteArray(ByteOrder.LITTLE_ENDIAN)
        assertEquals(4, bytes.size)
        assertEquals(0x78.toByte(), bytes[0])
        assertEquals(0x56.toByte(), bytes[1])
        assertEquals(0x34.toByte(), bytes[2])
        assertEquals(0x12.toByte(), bytes[3])
    }

    @Test
    fun `test mergeArrays`() {
        val arr1 = byteArrayOf(0x01.toByte(), 0x02.toByte())
        val arr2 = byteArrayOf(0x03.toByte(), 0x04.toByte())
        val arr3 = byteArrayOf(0x05.toByte())
        val merged = mergeArrays(arr1, arr2, arr3)
        assertEquals(5, merged.size)
        assertEquals(0x01.toByte(), merged[0])
        assertEquals(0x05.toByte(), merged[4])
    }

    @Test
    fun `test from16BitString`() {
        val uuid = from16BitString("180D")
        assertEquals("0000180d-0000-1000-8000-00805f9b34fb", uuid.toString())
    }
}
