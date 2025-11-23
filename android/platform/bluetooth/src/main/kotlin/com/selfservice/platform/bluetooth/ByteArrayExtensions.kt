/*
 * Адаптировано из blessed-kotlin (рес 6) для работы с BLE устройствами.
 * Лицензия MIT - см. исходный проект.
 */
package com.selfservice.platform.bluetooth

import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN

/**
 * Преобразует байт в hex-строку.
 */
fun Byte.asHexString(): String {
    var hexString = this.toUByte().toString(16).uppercase()
    if (this.toUInt() < 16u) hexString = "0$hexString"
    return hexString
}

/**
 * Преобразует ByteArray в hex-строку с разделителем.
 */
fun ByteArray.asFormattedHexString(separator: String?): String {
    var resultString = ""
    for ((index, value) in this.iterator().withIndex()) {
        resultString += value.asHexString()
        if (separator != null && index < (this.size - 1)) resultString += separator
    }
    return resultString
}

/**
 * Преобразует ByteArray в hex-строку без разделителей.
 */
fun ByteArray.asHexString(): String {
    return this.asFormattedHexString(null)
}

/**
 * Получает UInt8 из массива байтов по указанному смещению.
 */
fun ByteArray.getUInt8(offset: UInt): UInt {
    require(offset < this.size.toUInt()) { "Смещение за пределами массива" }
    return this[offset.toInt()].toUByte().toUInt()
}

/**
 * Получает Int8 из массива байтов по указанному смещению.
 */
fun ByteArray.getInt8(offset: UInt): Int {
    require(offset < this.size.toUInt()) { "Смещение за пределами массива" }
    return this[offset.toInt()].toInt()
}

/**
 * Получает UInt16 из массива байтов по указанному смещению с учетом порядка байтов.
 */
fun ByteArray.getUInt16(offset: UInt, byteOrder: ByteOrder = LITTLE_ENDIAN): UShort {
    require(offset + 1u < this.size.toUInt()) { "Смещение за пределами массива" }
    
    return if (byteOrder == LITTLE_ENDIAN) {
        ((this[offset.toInt() + 1].toUByte().toUInt() shl 8) or 
         this[offset.toInt()].toUByte().toUInt()).toUShort()
    } else {
        ((this[offset.toInt()].toUByte().toUInt() shl 8) or 
         this[offset.toInt() + 1].toUByte().toUInt()).toUShort()
    }
}

/**
 * Получает Int16 из массива байтов по указанному смещению с учетом порядка байтов.
 */
fun ByteArray.getInt16(offset: UInt, byteOrder: ByteOrder = LITTLE_ENDIAN): Short {
    val unsigned = getUInt16(offset, byteOrder).toUInt()
    return unsignedToSigned(unsigned, 16u).toShort()
}

/**
 * Получает UInt32 из массива байтов по указанному смещению с учетом порядка байтов.
 */
fun ByteArray.getUInt32(offset: UInt, byteOrder: ByteOrder = LITTLE_ENDIAN): UInt {
    require(offset + 3u < this.size.toUInt()) { "Смещение за пределами массива" }
    
    return if (byteOrder == LITTLE_ENDIAN) {
        (this[offset.toInt() + 3].toUByte().toUInt() shl 24) or
        (this[offset.toInt() + 2].toUByte().toUInt() shl 16) or
        (this[offset.toInt() + 1].toUByte().toUInt() shl 8) or
        this[offset.toInt()].toUByte().toUInt()
    } else {
        (this[offset.toInt()].toUByte().toUInt() shl 24) or
        (this[offset.toInt() + 1].toUByte().toUInt() shl 16) or
        (this[offset.toInt() + 2].toUByte().toUInt() shl 8) or
        this[offset.toInt() + 3].toUByte().toUInt()
    }
}

/**
 * Получает Int32 из массива байтов по указанному смещению с учетом порядка байтов.
 */
fun ByteArray.getInt32(offset: UInt, byteOrder: ByteOrder = LITTLE_ENDIAN): Int {
    val unsigned = getUInt32(offset, byteOrder)
    return unsignedToSigned(unsigned, 32u)
}

/**
 * Преобразует беззнаковое значение в знаковое с использованием дополнительного кода.
 */
private fun unsignedToSigned(unsigned: UInt, size: UInt): Int {
    if (size > 24u) throw IllegalArgumentException("размер слишком велик")

    val signBit: UInt = (1u shl ((size - 1u).toInt()))
    if (unsigned and signBit != 0u) {
        // Преобразуем в отрицательное значение
        val nonsignedPart = (unsigned and (signBit - 1u))
        return -1 * (signBit - nonsignedPart).toInt()
    }
    return unsigned.toInt()
}
