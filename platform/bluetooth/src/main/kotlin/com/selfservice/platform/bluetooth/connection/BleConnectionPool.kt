package com.selfservice.platform.bluetooth.connection

import com.selfservice.platform.bluetooth.ble.BleDevice
import com.selfservice.platform.bluetooth.config.BlePlatformConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Пул соединений для управления несколькими BLE устройствами
 * 
 * Позволяет:
 * - Подключаться к нескольким устройствам одновременно
 * - Переиспользовать соединения
 * - Управлять таймаутами и очисткой
 * 
 * @since Session 07B
 */
class BleConnectionPool(
    private val config: BlePlatformConfig,
    private val maxConnections: Int = 5
) {
    
    private val connections = ConcurrentHashMap<String, BleConnectionManager>()
    private val _activeConnections = MutableStateFlow<Set<String>>(emptySet())
    val activeConnections: StateFlow<Set<String>> = _activeConnections.asStateFlow()
    
    /**
     * Получить или создать менеджер соединения для устройства
     */
    fun getOrCreate(deviceAddress: String, factory: () -> BleConnectionManager): BleConnectionManager {
        return connections.getOrPut(deviceAddress) {
            if (connections.size >= maxConnections) {
                throw IllegalStateException("Достигнут максимум подключений: $maxConnections")
            }
            
            val manager = factory()
            _activeConnections.value = connections.keys
            manager
        }
    }
    
    /**
     * Получить существующее соединение
     */
    fun get(deviceAddress: String): BleConnectionManager? = connections[deviceAddress]
    
    /**
     * Удалить соединение
     */
    fun remove(deviceAddress: String) {
        connections.remove(deviceAddress)?.release()
        _activeConnections.value = connections.keys
    }
    
    /**
     * Получить все активные соединения
     */
    fun getAll(): Map<String, BleConnectionManager> = connections.toMap()
    
    /**
     * Очистить все соединения
     */
    fun clear() {
        connections.values.forEach { it.release() }
        connections.clear()
        _activeConnections.value = emptySet()
    }
    
    /**
     * Количество активных соединений
     */
    fun size(): Int = connections.size
    
    /**
     * Проверить, есть ли соединение для устройства
     */
    fun contains(deviceAddress: String): Boolean = connections.containsKey(deviceAddress)
}

/**
 * Фабрика для создания BleConnectionManager
 */
interface BleConnectionManagerFactory {
    fun create(device: BleDevice): BleConnectionManager
}

/**
 * Стратегия переподключения
 */
interface ReconnectionStrategy {
    /**
     * Вычислить задержку перед следующей попыткой
     * 
     * @param attempt Номер попытки (начиная с 1)
     * @return Задержка в миллисекундах
     */
    fun getDelay(attempt: Int): Long
    
    /**
     * Проверить, следует ли продолжать попытки
     * 
     * @param attempt Номер попытки
     * @return true если следует продолжать
     */
    fun shouldRetry(attempt: Int): Boolean
}

/**
 * Экспоненциальная стратегия переподключения
 */
class ExponentialBackoffStrategy(
    private val initialDelay: Long = 1000L,
    private val maxDelay: Long = 30000L,
    private val maxAttempts: Int = 5
) : ReconnectionStrategy {
    
    override fun getDelay(attempt: Int): Long {
        val delay = initialDelay * (1 shl (attempt - 1)) // 2^(attempt-1)
        return minOf(delay, maxDelay)
    }
    
    override fun shouldRetry(attempt: Int): Boolean = attempt <= maxAttempts
}

/**
 * Линейная стратегия переподключения
 */
class LinearBackoffStrategy(
    private val delay: Long = 2000L,
    private val maxAttempts: Int = 3
) : ReconnectionStrategy {
    
    override fun getDelay(attempt: Int): Long = delay
    
    override fun shouldRetry(attempt: Int): Boolean = attempt <= maxAttempts
}

/**
 * Координатор переподключений
 */
class ReconnectionCoordinator(
    private val strategy: ReconnectionStrategy = ExponentialBackoffStrategy()
) {
    
    private val attempts = ConcurrentHashMap<String, Int>()
    
    /**
     * Зарегистрировать попытку переподключения
     * 
     * @param deviceAddress Адрес устройства
     * @return Задержка до следующей попытки или null если больше не нужно пытаться
     */
    fun registerAttempt(deviceAddress: String): Long? {
        val currentAttempt = attempts.compute(deviceAddress) { _, current ->
            (current ?: 0) + 1
        }!!
        
        return if (strategy.shouldRetry(currentAttempt)) {
            strategy.getDelay(currentAttempt)
        } else {
            null
        }
    }
    
    /**
     * Сбросить счетчик попыток при успешном подключении
     */
    fun reset(deviceAddress: String) {
        attempts.remove(deviceAddress)
    }
    
    /**
     * Получить количество попыток для устройства
     */
    fun getAttemptCount(deviceAddress: String): Int = attempts[deviceAddress] ?: 0
    
    /**
     * Очистить все счетчики
     */
    fun clear() {
        attempts.clear()
    }
}
