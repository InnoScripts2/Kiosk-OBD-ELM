package com.selfservice.platform.bluetooth.config

import com.selfservice.platform.bluetooth.ble.BleScanMode
import java.util.UUID

/**
 * Конфигурация платформы Bluetooth
 * 
 * Централизованная конфигурация для всех BLE операций
 * 
 * @since Session 07B
 */
data class BlePlatformConfig(
    /**
     * Таймаут подключения по умолчанию (мс)
     */
    val defaultConnectionTimeout: Long = 5000L,
    
    /**
     * Таймаут операции чтения/записи (мс)
     */
    val defaultOperationTimeout: Long = 3000L,
    
    /**
     * Автоматическое переподключение при потере связи
     */
    val autoReconnect: Boolean = true,
    
    /**
     * Максимальное количество попыток переподключения
     */
    val maxReconnectAttempts: Int = 3,
    
    /**
     * Задержка между попытками переподключения (мс)
     */
    val reconnectDelay: Long = 2000L,
    
    /**
     * Режим сканирования по умолчанию
     */
    val defaultScanMode: BleScanMode = BleScanMode.LOW_LATENCY,
    
    /**
     * Длительность сканирования по умолчанию (мс, 0 = бесконечно)
     */
    val defaultScanDuration: Long = 10000L,
    
    /**
     * Минимальный RSSI для фильтрации устройств
     */
    val minRssi: Int = -90,
    
    /**
     * Желаемый MTU размер
     */
    val preferredMtu: Int = 247,
    
    /**
     * Включить подробное логирование
     */
    val verboseLogging: Boolean = false,
    
    /**
     * UUID сервисов для фильтрации OBD адаптеров
     */
    val obdServiceUuids: Set<UUID> = setOf(
        UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
        UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2")
    ),
    
    /**
     * Паттерны имен для OBD адаптеров
     */
    val obdDeviceNamePatterns: List<Regex> = listOf(
        Regex("OBD.*", RegexOption.IGNORE_CASE),
        Regex("ELM.*", RegexOption.IGNORE_CASE),
        Regex("VLINK.*", RegexOption.IGNORE_CASE),
        Regex("OBDII.*", RegexOption.IGNORE_CASE),
        Regex("Bluetooth.*OBD.*", RegexOption.IGNORE_CASE)
    )
) {
    companion object {
        /**
         * Конфигурация для разработки (более агрессивные таймауты)
         */
        fun development() = BlePlatformConfig(
            defaultConnectionTimeout = 10000L,
            defaultOperationTimeout = 5000L,
            autoReconnect = true,
            maxReconnectAttempts = 5,
            verboseLogging = true
        )
        
        /**
         * Конфигурация для production (консервативные настройки)
         */
        fun production() = BlePlatformConfig(
            defaultConnectionTimeout = 5000L,
            defaultOperationTimeout = 3000L,
            autoReconnect = true,
            maxReconnectAttempts = 3,
            verboseLogging = false
        )
        
        /**
         * Конфигурация для тестирования (быстрые таймауты)
         */
        fun testing() = BlePlatformConfig(
            defaultConnectionTimeout = 2000L,
            defaultOperationTimeout = 1000L,
            autoReconnect = false,
            maxReconnectAttempts = 1,
            verboseLogging = true
        )
    }
}

/**
 * Профили конфигурации для разных сценариев
 */
enum class BleConfigProfile {
    DEVELOPMENT,
    PRODUCTION,
    TESTING
}

/**
 * Билдер для конфигурации
 */
class BlePlatformConfigBuilder {
    private var config = BlePlatformConfig()
    
    fun connectionTimeout(timeout: Long) = apply {
        config = config.copy(defaultConnectionTimeout = timeout)
    }
    
    fun operationTimeout(timeout: Long) = apply {
        config = config.copy(defaultOperationTimeout = timeout)
    }
    
    fun autoReconnect(enable: Boolean) = apply {
        config = config.copy(autoReconnect = enable)
    }
    
    fun maxReconnectAttempts(attempts: Int) = apply {
        config = config.copy(maxReconnectAttempts = attempts)
    }
    
    fun reconnectDelay(delay: Long) = apply {
        config = config.copy(reconnectDelay = delay)
    }
    
    fun scanMode(mode: BleScanMode) = apply {
        config = config.copy(defaultScanMode = mode)
    }
    
    fun scanDuration(duration: Long) = apply {
        config = config.copy(defaultScanDuration = duration)
    }
    
    fun minRssi(rssi: Int) = apply {
        config = config.copy(minRssi = rssi)
    }
    
    fun preferredMtu(mtu: Int) = apply {
        require(mtu in 23..517) { "MTU должен быть в диапазоне 23-517" }
        config = config.copy(preferredMtu = mtu)
    }
    
    fun verboseLogging(enable: Boolean) = apply {
        config = config.copy(verboseLogging = enable)
    }
    
    fun obdServiceUuids(uuids: Set<UUID>) = apply {
        config = config.copy(obdServiceUuids = uuids)
    }
    
    fun obdDeviceNamePatterns(patterns: List<Regex>) = apply {
        config = config.copy(obdDeviceNamePatterns = patterns)
    }
    
    fun build(): BlePlatformConfig = config
}

/**
 * DSL для создания конфигурации
 */
fun blePlatformConfig(block: BlePlatformConfigBuilder.() -> Unit): BlePlatformConfig {
    return BlePlatformConfigBuilder().apply(block).build()
}
