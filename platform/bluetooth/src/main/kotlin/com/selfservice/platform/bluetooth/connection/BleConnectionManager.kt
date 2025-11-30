package com.selfservice.platform.bluetooth.connection

import com.selfservice.platform.bluetooth.ble.BleDevice
import com.selfservice.platform.bluetooth.ble.BleConnectionState
import com.selfservice.platform.bluetooth.error.BleConnectionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Главный менеджер BLE соединений для OBD-адаптеров.
 * 
 * Управляет жизненным циклом соединения с BLE устройствами:
 * - Подключение и отключение
 * - Обнаружение сервисов и характеристик
 * - Чтение и запись данных
 * - Управление состоянием соединения
 * - Обработка ошибок и переподключение
 * 
 * Интегрирует blessed-kotlin и kable для кросс-платформенной поддержки.
 * 
 * @since Session 07B
 */
interface BleConnectionManager {
    
    /**
     * Текущее состояние соединения
     */
    val connectionState: StateFlow<BleConnectionState>
    
    /**
     * Подключенное устройство (null если не подключено)
     */
    val connectedDevice: StateFlow<BleDevice?>
    
    /**
     * Поток событий соединения
     */
    val connectionEvents: Flow<ConnectionEvent>
    
    /**
     * Подключиться к указанному BLE устройству
     * 
     * @param device Устройство для подключения
     * @param autoReconnect Автоматически переподключаться при потере связи
     * @param timeout Таймаут подключения в миллисекундах (по умолчанию 5000)
     * @throws BleConnectionException если подключение не удалось
     */
    suspend fun connect(
        device: BleDevice,
        autoReconnect: Boolean = true,
        timeout: Long = 5000L
    )
    
    /**
     * Отключиться от текущего устройства
     */
    suspend fun disconnect()
    
    /**
     * Обнаружить GATT сервисы и характеристики
     * 
     * @return Список обнаруженных сервисов
     * @throws BleConnectionException если устройство не подключено
     */
    suspend fun discoverServices(): List<BleService>
    
    /**
     * Прочитать значение характеристики
     * 
     * @param serviceUuid UUID сервиса
     * @param characteristicUuid UUID характеристики
     * @return Прочитанные данные
     * @throws BleConnectionException при ошибке чтения
     */
    suspend fun readCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID
    ): ByteArray
    
    /**
     * Записать данные в характеристику
     * 
     * @param serviceUuid UUID сервиса
     * @param characteristicUuid UUID характеристики
     * @param data Данные для записи
     * @throws BleConnectionException при ошибке записи
     */
    suspend fun writeCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        data: ByteArray
    )
    
    /**
     * Подписаться на уведомления от характеристики
     * 
     * @param serviceUuid UUID сервиса
     * @param characteristicUuid UUID характеристики
     * @return Flow с данными уведомлений
     * @throws BleConnectionException при ошибке подписки
     */
    fun subscribeToNotifications(
        serviceUuid: UUID,
        characteristicUuid: UUID
    ): Flow<ByteArray>
    
    /**
     * Отписаться от уведомлений
     * 
     * @param serviceUuid UUID сервиса
     * @param characteristicUuid UUID характеристики
     */
    suspend fun unsubscribeFromNotifications(
        serviceUuid: UUID,
        characteristicUuid: UUID
    )
    
    /**
     * Получить текущую силу сигнала (RSSI)
     * 
     * @return RSSI в dBm или null если не подключено
     */
    suspend fun readRssi(): Int?
    
    /**
     * Запросить увеличение MTU (Maximum Transmission Unit)
     * 
     * @param mtu Желаемый размер MTU (23-517 байт)
     * @return Фактический согласованный размер MTU
     */
    suspend fun requestMtu(mtu: Int): Int
    
    /**
     * Освободить ресурсы менеджера
     */
    fun release()
}

/**
 * Описание BLE сервиса
 */
data class BleService(
    val uuid: UUID,
    val characteristics: List<BleCharacteristic>
)

/**
 * Описание BLE характеристики
 */
data class BleCharacteristic(
    val uuid: UUID,
    val properties: Set<CharacteristicProperty>
)

/**
 * Свойства характеристики
 */
enum class CharacteristicProperty {
    READ,
    WRITE,
    WRITE_WITHOUT_RESPONSE,
    NOTIFY,
    INDICATE
}

/**
 * События соединения
 */
sealed class ConnectionEvent {
    data class Connected(val device: BleDevice) : ConnectionEvent()
    data class Disconnected(val device: BleDevice, val reason: DisconnectReason) : ConnectionEvent()
    data class Connecting(val device: BleDevice) : ConnectionEvent()
    data class ConnectionFailed(val device: BleDevice, val error: Throwable) : ConnectionEvent()
    data class ServicesDiscovered(val services: List<BleService>) : ConnectionEvent()
    data class RssiChanged(val rssi: Int) : ConnectionEvent()
    data class MtuChanged(val mtu: Int) : ConnectionEvent()
}

/**
 * Причина отключения
 */
enum class DisconnectReason {
    USER_REQUESTED,
    CONNECTION_LOST,
    TIMEOUT,
    DEVICE_NOT_FOUND,
    GATT_ERROR,
    UNKNOWN
}
