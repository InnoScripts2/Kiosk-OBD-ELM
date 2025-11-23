/*
 * Адаптировано из blessed-kotlin (рес 6) для работы с BLE устройствами.
 * Лицензия MIT - см. исходный проект.
 */
package com.selfservice.platform.bluetooth

import java.nio.ByteOrder
import java.nio.ByteOrder.LITTLE_ENDIAN

/**
 * Парсер для чтения значений из массива байтов последовательно.
 * Используется для разбора BLE характеристик и OBD ответов.
 */
open class BluetoothBytesParser(
    private val value: ByteArray,
    var offset: Int = 0,
    private val byteOrder: ByteOrder = LITTLE_ENDIAN
) {
    /**
     * Получает следующий UInt8 и сдвигает offset на 1 байт.
     */
    fun getUInt8(): UInt {
        val result = value.getUInt8(offset.toUInt())
        offset += 1
        return result
    }

    /**
     * Получает следующий Int8 и сдвигает offset на 1 байт.
     */
    fun getInt8(): Int {
        val result = value.getInt8(offset.toUInt())
        offset += 1
        return result
    }

    /**
     * Получает следующий UInt16 и сдвигает offset на 2 байта.
     */
    fun getUInt16(): UShort {
        val result = value.getUInt16(offset.toUInt(), byteOrder)
        offset += 2
        return result
    }

    /**
     * Получает следующий Int16 и сдвигает offset на 2 байта.
     */
    fun getInt16(): Short {
        val result = value.getInt16(offset.toUInt(), byteOrder)
        offset += 2
        return result
    }

    /**
     * Получает следующий UInt32 и сдвигает offset на 4 байта.
     */
    fun getUInt32(): UInt {
        val result = value.getUInt32(offset.toUInt(), byteOrder)
        offset += 4
        return result
    }

    /**
     * Получает следующий Int32 и сдвигает offset на 4 байта.
     */
    fun getInt32(): Int {
        val result = value.getInt32(offset.toUInt(), byteOrder)
        offset += 4
        return result
    }

    /**
     * Получает строку заданной длины и сдвигает offset.
     */
    fun getString(length: Int): String {
        require(offset + length <= value.size) { "Смещение за пределами массива" }
        val result = value.decodeToString(offset, offset + length)
        offset += length
        return result
    }

    /**
     * Получает оставшиеся байты как ByteArray.
     */
    fun getRemainingBytes(): ByteArray {
        val result = value.copyOfRange(offset, value.size)
        offset = value.size
        return result
    }

    /**
     * Возвращает количество оставшихся байтов.
     */
    fun remainingBytes(): Int = value.size - offset

    /**
     * Проверяет, есть ли ещё данные для чтения.
     */
    fun hasMoreBytes(): Boolean = offset < value.size
}
