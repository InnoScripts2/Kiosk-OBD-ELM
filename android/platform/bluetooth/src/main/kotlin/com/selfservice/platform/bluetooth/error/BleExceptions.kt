package com.selfservice.platform.bluetooth.error

import com.selfservice.platform.bluetooth.ble.BleDevice
import java.util.UUID

/**
 * Базовый класс для всех BLE исключений
 */
sealed class BleException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * Код ошибки для логирования и аналитики
     */
    abstract val errorCode: String
    
    /**
     * Пользовательское сообщение (локализованное)
     */
    abstract val userMessage: String
}

/**
 * Ошибки подключения к BLE устройству
 */
sealed class BleConnectionException(
    message: String,
    cause: Throwable? = null
) : BleException(message, cause) {
    
    /**
     * Устройство не найдено
     */
    data class DeviceNotFound(
        val deviceAddress: String
    ) : BleConnectionException(
        message = "Устройство $deviceAddress не найдено",
        cause = null
    ) {
        override val errorCode = "BLE_DEVICE_NOT_FOUND"
        override val userMessage = "Устройство не найдено. Убедитесь, что адаптер включен и находится рядом."
    }
    
    /**
     * Таймаут подключения
     */
    data class ConnectionTimeout(
        val device: BleDevice,
        val timeoutMs: Long
    ) : BleConnectionException(
        message = "Таймаут подключения к ${device.displayName} ($timeoutMs мс)",
        cause = null
    ) {
        override val errorCode = "BLE_CONNECTION_TIMEOUT"
        override val userMessage = "Не удалось подключиться к устройству. Попробуйте еще раз."
    }
    
    /**
     * Соединение потеряно
     */
    data class ConnectionLost(
        val device: BleDevice,
        val reason: String?
    ) : BleConnectionException(
        message = "Соединение потеряно с ${device.displayName}: ${reason ?: "неизвестная причина"}",
        cause = null
    ) {
        override val errorCode = "BLE_CONNECTION_LOST"
        override val userMessage = "Соединение с устройством потеряно. Проверьте, что адаптер включен."
    }
    
    /**
     * GATT ошибка
     */
    data class GattError(
        val device: BleDevice,
        val gattStatus: Int,
        val operation: String
    ) : BleConnectionException(
        message = "GATT ошибка при $operation на ${device.displayName}: статус $gattStatus",
        cause = null
    ) {
        override val errorCode = "BLE_GATT_ERROR_$gattStatus"
        override val userMessage = "Ошибка связи с устройством (код $gattStatus). Попробуйте переподключиться."
    }
    
    /**
     * Устройство уже подключено
     */
    data class AlreadyConnected(
        val device: BleDevice
    ) : BleConnectionException(
        message = "Устройство ${device.displayName} уже подключено",
        cause = null
    ) {
        override val errorCode = "BLE_ALREADY_CONNECTED"
        override val userMessage = "Устройство уже подключено."
    }
    
    /**
     * Устройство не подключено
     */
    data class NotConnected(
        val operation: String
    ) : BleConnectionException(
        message = "Устройство не подключено для операции: $operation",
        cause = null
    ) {
        override val errorCode = "BLE_NOT_CONNECTED"
        override val userMessage = "Устройство не подключено. Сначала установите соединение."
    }
}

/**
 * Ошибки сканирования BLE устройств
 */
sealed class BleScanException(
    message: String,
    cause: Throwable? = null
) : BleException(message, cause) {
    
    /**
     * Сканирование уже запущено
     */
    object ScanAlreadyStarted : BleScanException(
        message = "Сканирование уже запущено",
        cause = null
    ) {
        override val errorCode = "BLE_SCAN_ALREADY_STARTED"
        override val userMessage = "Сканирование уже выполняется."
    }
    
    /**
     * Ошибка запуска сканирования
     */
    data class ScanStartFailed(
        val reason: String
    ) : BleScanException(
        message = "Не удалось запустить сканирование: $reason",
        cause = null
    ) {
        override val errorCode = "BLE_SCAN_START_FAILED"
        override val userMessage = "Не удалось начать поиск устройств. Проверьте разрешения и Bluetooth."
    }
    
    /**
     * Сканирование не поддерживается
     */
    object ScanNotSupported : BleScanException(
        message = "BLE сканирование не поддерживается на этом устройстве",
        cause = null
    ) {
        override val errorCode = "BLE_SCAN_NOT_SUPPORTED"
        override val userMessage = "Bluetooth Low Energy не поддерживается на этом устройстве."
    }
}

/**
 * Ошибки операций с характеристиками
 */
sealed class BleCharacteristicException(
    message: String,
    cause: Throwable? = null
) : BleException(message, cause) {
    
    /**
     * Характеристика не найдена
     */
    data class CharacteristicNotFound(
        val serviceUuid: UUID,
        val characteristicUuid: UUID
    ) : BleCharacteristicException(
        message = "Характеристика $characteristicUuid не найдена в сервисе $serviceUuid",
        cause = null
    ) {
        override val errorCode = "BLE_CHARACTERISTIC_NOT_FOUND"
        override val userMessage = "Требуемая характеристика не найдена в устройстве."
    }
    
    /**
     * Операция не поддерживается
     */
    data class OperationNotSupported(
        val characteristicUuid: UUID,
        val operation: String
    ) : BleCharacteristicException(
        message = "Операция $operation не поддерживается для характеристики $characteristicUuid",
        cause = null
    ) {
        override val errorCode = "BLE_OPERATION_NOT_SUPPORTED"
        override val userMessage = "Операция не поддерживается устройством."
    }
    
    /**
     * Ошибка чтения
     */
    data class ReadFailed(
        val characteristicUuid: UUID,
        val reason: String?
    ) : BleCharacteristicException(
        message = "Ошибка чтения характеристики $characteristicUuid: ${reason ?: "неизвестная причина"}",
        cause = null
    ) {
        override val errorCode = "BLE_READ_FAILED"
        override val userMessage = "Не удалось прочитать данные с устройства."
    }
    
    /**
     * Ошибка записи
     */
    data class WriteFailed(
        val characteristicUuid: UUID,
        val reason: String?
    ) : BleCharacteristicException(
        message = "Ошибка записи в характеристику $characteristicUuid: ${reason ?: "неизвестная причина"}",
        cause = null
    ) {
        override val errorCode = "BLE_WRITE_FAILED"
        override val userMessage = "Не удалось записать данные на устройство."
    }
    
    /**
     * Таймаут операции
     */
    data class OperationTimeout(
        val characteristicUuid: UUID,
        val operation: String,
        val timeoutMs: Long
    ) : BleCharacteristicException(
        message = "Таймаут операции $operation на характеристике $characteristicUuid ($timeoutMs мс)",
        cause = null
    ) {
        override val errorCode = "BLE_OPERATION_TIMEOUT"
        override val userMessage = "Операция заняла слишком много времени. Попробуйте еще раз."
    }
}

/**
 * Ошибки адаптера Bluetooth
 */
sealed class BleAdapterException(
    message: String,
    cause: Throwable? = null
) : BleException(message, cause) {
    
    /**
     * Адаптер не доступен
     */
    object AdapterNotAvailable : BleAdapterException(
        message = "Bluetooth адаптер не доступен",
        cause = null
    ) {
        override val errorCode = "BLE_ADAPTER_NOT_AVAILABLE"
        override val userMessage = "Bluetooth не доступен на этом устройстве."
    }
    
    /**
     * Адаптер выключен
     */
    object AdapterOff : BleAdapterException(
        message = "Bluetooth адаптер выключен",
        cause = null
    ) {
        override val errorCode = "BLE_ADAPTER_OFF"
        override val userMessage = "Включите Bluetooth для продолжения."
    }
    
    /**
     * Нет разрешений
     */
    data class PermissionDenied(
        val permission: String
    ) : BleAdapterException(
        message = "Отсутствует разрешение: $permission",
        cause = null
    ) {
        override val errorCode = "BLE_PERMISSION_DENIED"
        override val userMessage = "Необходимо разрешение на использование Bluetooth."
    }
}

/**
 * Общая ошибка BLE
 */
data class BleGeneralException(
    val details: String,
    val originalCause: Throwable? = null
) : BleException(
    message = "BLE ошибка: $details",
    cause = originalCause
) {
    override val errorCode = "BLE_GENERAL_ERROR"
    override val userMessage = "Произошла ошибка Bluetooth. Попробуйте перезапустить приложение."
}
