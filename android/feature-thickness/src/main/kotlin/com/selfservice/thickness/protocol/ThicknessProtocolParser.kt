package com.selfservice.thickness.protocol

import com.selfservice.thickness.models.ProtocolFormat
import com.selfservice.thickness.models.ThicknessDeviceConfig
import com.selfservice.thickness.models.ThicknessDeviceError
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Парсер протокола обмена данными с толщиномером
 * 
 * Поддерживает ASCII и бинарный форматы данных.
 * 
 * ASCII формат: "VALUE:123.45\n"
 * Бинарный формат: 4 байта float (little-endian)
 * 
 * @since Session 12B
 */
class ThicknessProtocolParser(
    private val config: ThicknessDeviceConfig = ThicknessDeviceConfig()
) {
    
    /**
     * Распарсить данные от устройства
     * 
     * @param data Сырые байты от устройства
     * @return Значение измерения в микронах
     * @throws ThicknessDeviceError.ParseError при ошибке парсинга
     * @throws ThicknessDeviceError.OutOfRangeError если значение вне допустимого диапазона
     */
    fun parse(data: ByteArray): Float {
        if (data.isEmpty()) {
            throw ThicknessDeviceError.ParseError("Empty data received", data)
        }
        
        val format = when (config.protocolFormat) {
            ProtocolFormat.AUTO -> detectFormat(data)
            else -> config.protocolFormat
        }
        
        val value = when (format) {
            ProtocolFormat.ASCII -> parseAscii(data)
            ProtocolFormat.BINARY -> parseBinary(data)
            ProtocolFormat.AUTO -> throw IllegalStateException("Auto format should be resolved")
        }
        
        validateValue(value)
        return value
    }
    
    /**
     * Парсить ASCII формат: "VALUE:123.45\n"
     */
    private fun parseAscii(data: ByteArray): Float {
        try {
            val text = data.decodeToString().trim()
            
            // Формат: "VALUE:123.45"
            val parts = text.split(":")
            if (parts.size != 2 || !parts[0].equals("VALUE", ignoreCase = true)) {
                throw ThicknessDeviceError.ParseError(
                    "Invalid ASCII format: expected 'VALUE:xxx.xx', got '$text'",
                    data
                )
            }
            
            return parts[1].toFloatOrNull()
                ?: throw ThicknessDeviceError.ParseError(
                    "Cannot parse float value from '${parts[1]}'",
                    data
                )
        } catch (e: ThicknessDeviceError) {
            throw e
        } catch (e: Exception) {
            throw ThicknessDeviceError.ParseError(
                "ASCII parsing error: ${e.message}",
                data
            )
        }
    }
    
    /**
     * Парсить бинарный формат: 4 байта float (little-endian)
     */
    private fun parseBinary(data: ByteArray): Float {
        if (data.size != 4) {
            throw ThicknessDeviceError.ParseError(
                "Invalid binary format: expected 4 bytes, got ${data.size}",
                data
            )
        }
        
        return try {
            ByteBuffer.wrap(data)
                .order(ByteOrder.LITTLE_ENDIAN)
                .float
        } catch (e: Exception) {
            throw ThicknessDeviceError.ParseError(
                "Binary parsing error: ${e.message}",
                data
            )
        }
    }
    
    /**
     * Автоопределение формата по первому байту
     * 
     * ASCII начинается с 'V' (0x56)
     * Бинарный может быть любым байтом
     */
    private fun detectFormat(data: ByteArray): ProtocolFormat {
        return if (data[0].toInt().toChar() == 'V') {
            ProtocolFormat.ASCII
        } else {
            ProtocolFormat.BINARY
        }
    }
    
    /**
     * Валидировать значение измерения
     * 
     * @throws ThicknessDeviceError.OutOfRangeError если значение вне допустимого диапазона
     */
    private fun validateValue(value: Float) {
        if (value < ThicknessDeviceConfig.MIN_VALID_MEASUREMENT || 
            value > ThicknessDeviceConfig.MAX_VALID_MEASUREMENT) {
            throw ThicknessDeviceError.OutOfRangeError(value)
        }
        
        if (value.isNaN() || value.isInfinite()) {
            throw ThicknessDeviceError.ParseError(
                "Invalid float value: $value",
                ByteArray(0)
            )
        }
    }
    
    /**
     * Закодировать команду для отправки на устройство
     * 
     * В текущей реализации толщиномер работает в режиме постоянных измерений,
     * команды не требуются. Метод оставлен для будущего расширения.
     */
    fun encodeCommand(command: DeviceCommand): ByteArray {
        return when (command) {
            DeviceCommand.START_MEASUREMENT -> "START\n".toByteArray()
            DeviceCommand.STOP_MEASUREMENT -> "STOP\n".toByteArray()
            DeviceCommand.RESET -> "RESET\n".toByteArray()
        }
    }
    
    /**
     * Команды для отправки на устройство
     */
    enum class DeviceCommand {
        START_MEASUREMENT,
        STOP_MEASUREMENT,
        RESET
    }
}
