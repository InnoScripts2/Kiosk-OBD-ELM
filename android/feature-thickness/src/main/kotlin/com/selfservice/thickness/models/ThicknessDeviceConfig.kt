package com.selfservice.thickness.models

/**
 * Конфигурация устройства толщиномера
 * 
 * Содержит таймауты, UUID-ы BLE сервисов и другие параметры подключения.
 * 
 * @since Session 12B
 */

/**
 * Конфигурация толщиномера
 */
data class ThicknessDeviceConfig(
    val connectionTimeout: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
    val measurementTimeout: Long = DEFAULT_MEASUREMENT_TIMEOUT_MS,
    val scanTimeout: Long = DEFAULT_SCAN_TIMEOUT_MS,
    val reconnectDelay: Long = DEFAULT_RECONNECT_DELAY_MS,
    val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    val serviceUuid: String = DEFAULT_SERVICE_UUID,
    val characteristicUuid: String = DEFAULT_CHARACTERISTIC_UUID,
    val deviceNamePattern: String = DEFAULT_DEVICE_NAME_PATTERN,
    val protocolFormat: ProtocolFormat = ProtocolFormat.ASCII
) {
    companion object {
        /** Таймаут подключения: 5 секунд (согласно требованиям) */
        const val DEFAULT_CONNECTION_TIMEOUT_MS = 5000L
        
        /** Таймаут измерения: 30 секунд на одну точку (согласно требованиям) */
        const val DEFAULT_MEASUREMENT_TIMEOUT_MS = 30000L
        
        /** Таймаут сканирования устройства: 10 секунд */
        const val DEFAULT_SCAN_TIMEOUT_MS = 10000L
        
        /** Задержка перед повторным подключением: 2 секунды */
        const val DEFAULT_RECONNECT_DELAY_MS = 2000L
        
        /** Максимальное количество попыток переподключения */
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 3
        
        /** Service UUID для толщиномера (стандартный UART UUID) */
        const val DEFAULT_SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        
        /** Characteristic UUID для данных толщиномера */
        const val DEFAULT_CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
        
        /** Паттерн имени устройства для поиска */
        const val DEFAULT_DEVICE_NAME_PATTERN = "Thickness"
        
        /** Диапазон допустимых значений измерений (микроны) */
        const val MIN_VALID_MEASUREMENT = 0f
        const val MAX_VALID_MEASUREMENT = 2000f
    }
}

/**
 * Формат протокола обмена данными
 */
enum class ProtocolFormat {
    /** ASCII строки вида "VALUE:123.45\n" */
    ASCII,
    /** Бинарный формат (4 байта float, little-endian) */
    BINARY,
    /** Автоопределение по первому байту */
    AUTO
}

/**
 * Режим работы устройства
 */
enum class DeviceMode {
    /** DEV режим - имитация данных */
    DEV,
    /** QA режим - тестирование на реальном устройстве */
    QA,
    /** Production режим - боевое использование */
    PROD
}

/**
 * Ошибки подключения и измерений
 */
sealed class ThicknessDeviceError : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)
    
    /**
     * Ошибка подключения к устройству
     */
    class ConnectionError(message: String, cause: Throwable? = null) : 
        ThicknessDeviceError(message, cause)
    
    /**
     * Ошибка измерения
     */
    class MeasurementError(message: String, cause: Throwable? = null) : 
        ThicknessDeviceError(message, cause)
    
    /**
     * Превышен таймаут
     */
    class TimeoutError(message: String, val timeoutMs: Long) : 
        ThicknessDeviceError(message)
    
    /**
     * Устройство не найдено при сканировании
     */
    class DeviceNotFoundError(message: String = "Thickness gauge device not found") : 
        ThicknessDeviceError(message)
    
    /**
     * Устройство не поддерживает необходимый сервис
     */
    class ServiceNotSupportedError(val serviceUuid: String) : 
        ThicknessDeviceError("Device does not support service $serviceUuid")
    
    /**
     * Ошибка парсинга данных от устройства
     */
    class ParseError(message: String, val rawData: ByteArray) : 
        ThicknessDeviceError(message)
    
    /**
     * Значение вне допустимого диапазона
     */
    class OutOfRangeError(val value: Float) : 
        ThicknessDeviceError("Measurement value $value is out of valid range")
}

/**
 * Конфигурация для DEV режима (мок-устройство)
 */
data class MockDeviceConfig(
    val enabled: Boolean = false,
    val simulateDelay: Boolean = true,
    val measurementDelay: Long = 1000L, // 1 секунда на измерение
    val randomize: Boolean = true,
    val baseValue: Float = 100f, // базовое значение для генерации
    val variance: Float = 30f, // разброс значений
    val errorRate: Float = 0.0f // вероятность ошибки (0.0 - 1.0)
) {
    companion object {
        /**
         * Конфигурация для тестирования (быстрые измерения)
         */
        fun forTesting() = MockDeviceConfig(
            enabled = true,
            simulateDelay = true,
            measurementDelay = 100L,
            randomize = true,
            errorRate = 0.0f
        )
        
        /**
         * Конфигурация для демонстрации (реалистичные задержки)
         */
        fun forDemo() = MockDeviceConfig(
            enabled = true,
            simulateDelay = true,
            measurementDelay = 1500L,
            randomize = true,
            errorRate = 0.02f
        )
    }
}
