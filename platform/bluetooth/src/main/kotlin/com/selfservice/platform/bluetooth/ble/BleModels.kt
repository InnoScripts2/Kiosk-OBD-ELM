package com.selfservice.platform.bluetooth.ble

import java.util.UUID

/**
 * Представление BLE устройства
 * 
 * @property address MAC-адрес устройства
 * @property name Имя устройства (может быть null)
 * @property rssi Сила сигнала в dBm
 * @property services Список UUID рекламируемых сервисов
 */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val services: List<UUID> = emptyList()
) {
    /**
     * Уникальный идентификатор устройства (MAC-адрес)
     */
    val id: String get() = address
    
    /**
     * Отображаемое имя устройства
     */
    val displayName: String get() = name ?: "Неизвестное устройство"
    
    /**
     * Является ли устройство OBD-адаптером
     * (проверяет наличие стандартных OBD сервисов)
     */
    val isObdAdapter: Boolean
        get() = services.any { uuid ->
            uuid in OBD_SERVICE_UUIDS
        }
    
    /**
     * Уровень сигнала в процентах (0-100)
     */
    val signalStrength: Int
        get() = when {
            rssi >= -50 -> 100
            rssi >= -60 -> 80
            rssi >= -70 -> 60
            rssi >= -80 -> 40
            rssi >= -90 -> 20
            else -> 0
        }
    
    /**
     * Качество сигнала
     */
    val signalQuality: SignalQuality
        get() = when {
            rssi >= -60 -> SignalQuality.EXCELLENT
            rssi >= -70 -> SignalQuality.GOOD
            rssi >= -80 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }
    
    companion object {
        /**
         * Стандартные UUID сервисов для OBD-адаптеров
         */
        val OBD_SERVICE_UUIDS = setOf(
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), // Generic OBD
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // Common OBD service
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")  // ELM327 service
        )
    }
}

/**
 * Качество BLE сигнала
 */
enum class SignalQuality {
    EXCELLENT,  // > -60 dBm
    GOOD,       // -60 до -70 dBm
    FAIR,       // -70 до -80 dBm
    POOR        // < -80 dBm
}

/**
 * Состояние BLE соединения
 */
enum class BleConnectionState {
    /** Отключено */
    DISCONNECTED,
    
    /** Подключение в процессе */
    CONNECTING,
    
    /** Подключено, обнаружение сервисов */
    DISCOVERING_SERVICES,
    
    /** Подключено и готово к работе */
    CONNECTED,
    
    /** Отключение в процессе */
    DISCONNECTING
}

/**
 * Результат BLE сканирования
 */
data class BleScanResult(
    val device: BleDevice,
    val serviceUuids: List<UUID>,
    val seenAtMillis: Long = System.currentTimeMillis()
)

/**
 * Конфигурация BLE сканирования
 */
data class BleScanConfig(
    /**
     * Фильтр по UUID сервисов (пустой список = все устройства)
     */
    val serviceUuids: List<UUID> = emptyList(),
    
    /**
     * Фильтр по имени устройства (regex)
     */
    val nameFilter: Regex? = null,
    
    /**
     * Минимальный RSSI для фильтрации (-100 дБм по умолчанию)
     */
    val minRssi: Int = -100,
    
    /**
     * Длительность сканирования в миллисекундах (0 = бесконечно)
     */
    val scanDuration: Long = 0L,
    
    /**
     * Режим сканирования
     */
    val scanMode: BleScanMode = BleScanMode.LOW_LATENCY
)

/**
 * Режим BLE сканирования
 */
enum class BleScanMode {
    /**
     * Низкая задержка, высокое энергопотребление
     */
    LOW_LATENCY,
    
    /**
     * Балансированный режим
     */
    BALANCED,
    
    /**
     * Низкое энергопотребление
     */
    LOW_POWER
}

/**
 * Статус BLE адаптера
 */
enum class BleAdapterState {
    /** Адаптер выключен */
    OFF,
    
    /** Адаптер включается */
    TURNING_ON,
    
    /** Адаптер включен */
    ON,
    
    /** Адаптер выключается */
    TURNING_OFF,
    
    /** Адаптер недоступен */
    UNAVAILABLE
}
