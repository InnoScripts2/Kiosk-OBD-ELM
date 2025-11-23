package com.selfservice.lockcontrol

import kotlinx.coroutines.flow.Flow

/**
 * Конфигурация для подключения к Arduino через USB Serial
 */
data class UsbSerialConfig(
    /** Путь к Serial порту (для отладки, на Android определяется автоматически) */
    val port: String = "/dev/ttyUSB0",
    
    /** Скорость передачи данных */
    val baudRate: Int = 9600,
    
    /** Таймаут команды (мс) */
    val commandTimeout: Long = 10000,
    
    /** Задержка перед повторным подключением (мс) */
    val reconnectDelay: Long = 5000,
    
    /** Интервал heartbeat (мс) */
    val heartbeatInterval: Long = 30000
)

/**
 * Интерфейс для работы с Arduino через USB Serial
 * 
 * Реализации:
 * - UsbSerialAdapterImpl: Реальная работа через usb-serial-for-android
 * - MockUsbSerialAdapter: Mock для тестирования и DEV-режима
 */
interface UsbSerialAdapter {
    
    /**
     * Подключиться к Arduino
     * @throws UsbSerialException если подключение не удалось
     */
    suspend fun connect()
    
    /**
     * Отключиться от Arduino
     */
    suspend fun disconnect()
    
    /**
     * Отправить команду Arduino и дождаться ответа
     * @param command команда для отправки
     * @return ответ от Arduino
     * @throws UsbSerialException если команда не выполнена
     * @throws TimeoutException если нет ответа в течение commandTimeout
     */
    suspend fun sendCommand(command: ArduinoCommand): ArduinoResponse
    
    /**
     * Проверить, подключен ли адаптер
     */
    fun isConnected(): Boolean
    
    /**
     * Flow событий от Arduino (подключение, отключение, ошибки)
     */
    val events: Flow<UsbSerialEvent>
}

/**
 * События USB Serial адаптера
 */
sealed class UsbSerialEvent {
    /** Подключение установлено */
    data object Connected : UsbSerialEvent()
    
    /** Подключение потеряно */
    data object Disconnected : UsbSerialEvent()
    
    /** Ошибка порта */
    data class Error(val exception: UsbSerialException) : UsbSerialEvent()
    
    /** Получен ответ от Arduino (для отладки) */
    data class Response(val response: ArduinoResponse) : UsbSerialEvent()
}

/**
 * Исключение USB Serial
 */
sealed class UsbSerialException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Устройство не найдено */
    class DeviceNotFound(message: String = "USB device not found") : UsbSerialException(message)
    
    /** Не удалось открыть порт */
    class PortOpenFailed(message: String, cause: Throwable? = null) : UsbSerialException(message, cause)
    
    /** Ошибка чтения/записи */
    class IoError(message: String, cause: Throwable? = null) : UsbSerialException(message, cause)
    
    /** Таймаут команды */
    class CommandTimeout(command: ArduinoCommand, timeout: Long) : 
        UsbSerialException("Command $command timed out after ${timeout}ms")
    
    /** Неожиданный ответ */
    class UnexpectedResponse(val raw: String) : 
        UsbSerialException("Unexpected response: $raw")
}
