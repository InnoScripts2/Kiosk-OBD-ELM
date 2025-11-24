package com.selfservice.thickness.exceptions

import com.selfservice.thickness.models.ThicknessDeviceError

/**
 * Специализированные исключения для работы с толщиномером
 * 
 * Расширяет базовые классы ThicknessDeviceError более специфичными сценариями.
 * 
 * @since Session 13B
 */

/**
 * Исключение при ошибке подключения к BLE устройству
 * 
 * @param deviceAddress MAC-адрес устройства
 * @param message Описание ошибки
 * @param cause Причина ошибки
 */
class ThicknessConnectionException(
    val deviceAddress: String,
    message: String = "Failed to connect to thickness device at $deviceAddress",
    cause: Throwable? = null
) : ThicknessDeviceError.ConnectionError(message, cause) {
    
    companion object {
        /**
         * Создать исключение для таймаута подключения
         */
        fun timeout(deviceAddress: String, timeoutMs: Long): ThicknessConnectionException {
            return ThicknessConnectionException(
                deviceAddress = deviceAddress,
                message = "Connection timeout after ${timeoutMs}ms for device $deviceAddress"
            )
        }
        
        /**
         * Создать исключение для отказа в соединении
         */
        fun refused(deviceAddress: String, reason: String? = null): ThicknessConnectionException {
            val msg = if (reason != null) {
                "Connection refused for device $deviceAddress: $reason"
            } else {
                "Connection refused for device $deviceAddress"
            }
            return ThicknessConnectionException(deviceAddress, msg)
        }
        
        /**
         * Создать исключение для неожиданного разрыва соединения
         */
        fun disconnected(deviceAddress: String): ThicknessConnectionException {
            return ThicknessConnectionException(
                deviceAddress = deviceAddress,
                message = "Device $deviceAddress unexpectedly disconnected"
            )
        }
    }
}

/**
 * Исключение при превышении таймаута измерения
 * 
 * @param zoneIndex Индекс зоны, где произошел таймаут
 * @param zoneName Название зоны
 * @param timeoutMs Таймаут в миллисекундах
 * @param message Описание ошибки
 */
class MeasurementTimeoutException(
    val zoneIndex: Int,
    val zoneName: String,
    val timeoutMs: Long,
    message: String = "Measurement timeout for zone $zoneName (index $zoneIndex) after ${timeoutMs}ms"
) : ThicknessDeviceError.TimeoutError(message, timeoutMs) {
    
    companion object {
        /**
         * Создать исключение для таймаута без ответа от устройства
         */
        fun noResponse(zoneIndex: Int, zoneName: String, timeoutMs: Long): MeasurementTimeoutException {
            return MeasurementTimeoutException(
                zoneIndex = zoneIndex,
                zoneName = zoneName,
                timeoutMs = timeoutMs,
                message = "No response from device for zone $zoneName after ${timeoutMs}ms"
            )
        }
        
        /**
         * Создать исключение для слишком долгого измерения
         */
        fun tooSlow(zoneIndex: Int, zoneName: String, actualMs: Long, limitMs: Long): MeasurementTimeoutException {
            return MeasurementTimeoutException(
                zoneIndex = zoneIndex,
                zoneName = zoneName,
                timeoutMs = limitMs,
                message = "Measurement for zone $zoneName took ${actualMs}ms, exceeding limit of ${limitMs}ms"
            )
        }
    }
}

/**
 * Исключение при ошибке парсинга данных от устройства
 * 
 * @param rawData Сырые данные от устройства
 * @param expectedFormat Ожидаемый формат данных
 * @param message Описание ошибки
 */
class ThicknessProtocolException(
    val rawData: ByteArray,
    val expectedFormat: String,
    message: String = "Failed to parse thickness data, expected format: $expectedFormat"
) : ThicknessDeviceError.ParseError(message, rawData) {
    
    companion object {
        /**
         * Создать исключение для невалидного формата
         */
        fun invalidFormat(
            rawData: ByteArray,
            expectedFormat: String,
            reason: String
        ): ThicknessProtocolException {
            return ThicknessProtocolException(
                rawData = rawData,
                expectedFormat = expectedFormat,
                message = "Invalid data format: $reason (expected: $expectedFormat)"
            )
        }
        
        /**
         * Создать исключение для неполных данных
         */
        fun incompleteData(
            rawData: ByteArray,
            expectedBytes: Int,
            actualBytes: Int
        ): ThicknessProtocolException {
            return ThicknessProtocolException(
                rawData = rawData,
                expectedFormat = "$expectedBytes bytes",
                message = "Incomplete data: received $actualBytes bytes, expected $expectedBytes bytes"
            )
        }
        
        /**
         * Создать исключение для неизвестного формата
         */
        fun unknownFormat(rawData: ByteArray): ThicknessProtocolException {
            val preview = rawData.take(16).joinToString(" ") { "%02X".format(it) }
            return ThicknessProtocolException(
                rawData = rawData,
                expectedFormat = "ASCII or Binary",
                message = "Unknown data format, first bytes: $preview"
            )
        }
    }
}

/**
 * Исключение при получении значения вне допустимого диапазона
 * 
 * @param value Полученное значение
 * @param zoneIndex Индекс зоны
 * @param zoneName Название зоны
 * @param minValid Минимальное допустимое значение
 * @param maxValid Максимальное допустимое значение
 */
class ThicknessValueOutOfRangeException(
    val value: Float,
    val zoneIndex: Int,
    val zoneName: String,
    val minValid: Float,
    val maxValid: Float,
    message: String = "Value $value for zone $zoneName is out of range [$minValid, $maxValid]"
) : ThicknessDeviceError.OutOfRangeError(value) {
    
    /**
     * Проверить, является ли значение слишком низким
     */
    fun isTooLow(): Boolean = value < minValid
    
    /**
     * Проверить, является ли значение слишком высоким
     */
    fun isTooHigh(): Boolean = value > maxValid
    
    companion object {
        /**
         * Создать исключение для отрицательного значения
         */
        fun negative(value: Float, zoneIndex: Int, zoneName: String): ThicknessValueOutOfRangeException {
            return ThicknessValueOutOfRangeException(
                value = value,
                zoneIndex = zoneIndex,
                zoneName = zoneName,
                minValid = 0f,
                maxValid = Float.MAX_VALUE,
                message = "Negative value $value for zone $zoneName is not allowed"
            )
        }
        
        /**
         * Создать исключение для бесконечного значения
         */
        fun infinite(zoneIndex: Int, zoneName: String): ThicknessValueOutOfRangeException {
            return ThicknessValueOutOfRangeException(
                value = Float.POSITIVE_INFINITY,
                zoneIndex = zoneIndex,
                zoneName = zoneName,
                minValid = 0f,
                maxValid = 2000f,
                message = "Infinite value for zone $zoneName is not allowed"
            )
        }
        
        /**
         * Создать исключение для NaN значения
         */
        fun notANumber(zoneIndex: Int, zoneName: String): ThicknessValueOutOfRangeException {
            return ThicknessValueOutOfRangeException(
                value = Float.NaN,
                zoneIndex = zoneIndex,
                zoneName = zoneName,
                minValid = 0f,
                maxValid = 2000f,
                message = "NaN value for zone $zoneName is not allowed"
            )
        }
    }
}

/**
 * Исключение при достижении максимального числа попыток переподключения
 * 
 * @param deviceAddress MAC-адрес устройства
 * @param attempts Количество попыток
 * @param lastError Последняя произошедшая ошибка
 */
class MaxReconnectAttemptsExceededException(
    val deviceAddress: String,
    val attempts: Int,
    val lastError: Throwable? = null,
    message: String = "Failed to reconnect to device $deviceAddress after $attempts attempts"
) : ThicknessDeviceError.ConnectionError(message, lastError) {
    
    companion object {
        /**
         * Создать исключение с описанием всех попыток
         */
        fun withHistory(
            deviceAddress: String,
            attempts: Int,
            errors: List<Throwable>
        ): MaxReconnectAttemptsExceededException {
            val errorSummary = errors.joinToString("; ") { it.message ?: "Unknown error" }
            return MaxReconnectAttemptsExceededException(
                deviceAddress = deviceAddress,
                attempts = attempts,
                lastError = errors.lastOrNull(),
                message = "Failed to reconnect to device $deviceAddress after $attempts attempts. Errors: $errorSummary"
            )
        }
    }
}

/**
 * Исключение при ошибке в BLE-стеке
 * 
 * @param operation Операция, во время которой произошла ошибка
 * @param bleErrorCode Код ошибки BLE (если доступен)
 * @param message Описание ошибки
 * @param cause Причина ошибки
 */
class BleStackException(
    val operation: String,
    val bleErrorCode: Int? = null,
    message: String = "BLE stack error during operation: $operation",
    cause: Throwable? = null
) : ThicknessDeviceError.ConnectionError(message, cause) {
    
    companion object {
        /**
         * Создать исключение для ошибки характеристики
         */
        fun characteristicError(
            operation: String,
            characteristicUuid: String,
            bleErrorCode: Int? = null
        ): BleStackException {
            return BleStackException(
                operation = operation,
                bleErrorCode = bleErrorCode,
                message = "Failed to $operation characteristic $characteristicUuid" +
                        (bleErrorCode?.let { " (BLE error: $it)" } ?: "")
            )
        }
        
        /**
         * Создать исключение для ошибки сервиса
         */
        fun serviceError(
            operation: String,
            serviceUuid: String,
            bleErrorCode: Int? = null
        ): BleStackException {
            return BleStackException(
                operation = operation,
                bleErrorCode = bleErrorCode,
                message = "Failed to $operation service $serviceUuid" +
                        (bleErrorCode?.let { " (BLE error: $it)" } ?: "")
            )
        }
    }
}
