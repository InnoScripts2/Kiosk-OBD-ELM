package com.selfservice.thickness.ble

import com.selfservice.platform.bluetooth.BlessedBleScanner
import com.selfservice.platform.bluetooth.BleConnectionManager
import com.selfservice.core.logging.Logger
import com.selfservice.thickness.models.ThicknessDeviceConfig
import com.selfservice.thickness.models.ThicknessDeviceError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay

/**
 * Адаптер для работы с толщиномером через BLE
 * 
 * Интегрируется с platform/bluetooth без изменения существующих файлов.
 * Использует BlessedBleScanner и BleConnectionManager для подключения к устройству.
 * 
 * @since Session 12B
 */
class ThicknessBleAdapter(
    private val bleScanner: BlessedBleScanner,
    private val bleConnectionManager: BleConnectionManager,
    private val logger: Logger,
    private val config: ThicknessDeviceConfig = ThicknessDeviceConfig()
) {
    
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private var currentDeviceAddress: String? = null
    private var reconnectAttempts = 0
    
    /**
     * Состояние подключения к устройству
     */
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Scanning : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceAddress: String, val deviceName: String) : ConnectionState()
        data class Error(val error: ThicknessDeviceError) : ConnectionState()
    }
    
    /**
     * Подключиться к толщиномеру
     * 
     * @param deviceAddress Адрес устройства (опционально, если null - выполняется сканирование)
     * @return Result с адресом подключенного устройства или ошибкой
     */
    suspend fun connect(deviceAddress: String? = null): Result<String> {
        logger.info(TAG, "Connecting to thickness gauge, address: $deviceAddress")
        
        return try {
            val address = deviceAddress ?: scanForDevice()
            
            _connectionState.value = ConnectionState.Connecting
            
            withTimeout(config.connectionTimeout) {
                bleConnectionManager.connect(address, config.connectionTimeout)
            }
            
            // Проверка наличия необходимого сервиса
            val hasService = bleConnectionManager.hasService(config.serviceUuid)
            if (!hasService) {
                disconnect()
                val error = ThicknessDeviceError.ServiceNotSupportedError(config.serviceUuid)
                _connectionState.value = ConnectionState.Error(error)
                return Result.failure(error)
            }
            
            currentDeviceAddress = address
            reconnectAttempts = 0
            
            _connectionState.value = ConnectionState.Connected(address, "Thickness Gauge")
            logger.info(TAG, "Successfully connected to device $address")
            
            Result.success(address)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to connect", e)
            val error = when (e) {
                is ThicknessDeviceError -> e
                else -> ThicknessDeviceError.ConnectionError("Connection failed: ${e.message}", e)
            }
            _connectionState.value = ConnectionState.Error(error)
            Result.failure(error)
        }
    }
    
    /**
     * Отключиться от устройства
     */
    suspend fun disconnect() {
        logger.info(TAG, "Disconnecting from thickness gauge")
        
        currentDeviceAddress?.let { address ->
            try {
                bleConnectionManager.disconnect(address)
            } catch (e: Exception) {
                logger.error(TAG, "Error during disconnect", e)
            }
        }
        
        currentDeviceAddress = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Переподключиться к устройству
     * 
     * Используется при потере соединения. Применяет exponential backoff.
     */
    suspend fun reconnect(): Result<String> {
        if (reconnectAttempts >= config.maxReconnectAttempts) {
            val error = ThicknessDeviceError.ConnectionError(
                "Max reconnect attempts ($reconnectAttempts) exceeded"
            )
            return Result.failure(error)
        }
        
        reconnectAttempts++
        val delayMs = config.reconnectDelay * reconnectAttempts
        
        logger.info(TAG, "Reconnecting (attempt $reconnectAttempts), delay: ${delayMs}ms")
        delay(delayMs)
        
        return connect(currentDeviceAddress)
    }
    
    /**
     * Проверить подключение к устройству
     */
    fun isConnected(): Boolean {
        return _connectionState.value is ConnectionState.Connected
    }
    
    /**
     * Записать данные в characteristic устройства
     * 
     * @param data Данные для отправки
     * @return Result с успехом или ошибкой
     */
    suspend fun writeData(data: ByteArray): Result<Unit> {
        val address = currentDeviceAddress
        if (address == null || !isConnected()) {
            return Result.failure(
                ThicknessDeviceError.ConnectionError("Device not connected")
            )
        }
        
        return try {
            bleConnectionManager.write(
                address,
                config.serviceUuid,
                config.characteristicUuid,
                data
            )
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to write data", e)
            Result.failure(
                ThicknessDeviceError.MeasurementError("Write failed: ${e.message}", e)
            )
        }
    }
    
    /**
     * Читать данные из characteristic устройства
     * 
     * @return Result с данными или ошибкой
     */
    suspend fun readData(): Result<ByteArray> {
        val address = currentDeviceAddress
        if (address == null || !isConnected()) {
            return Result.failure(
                ThicknessDeviceError.ConnectionError("Device not connected")
            )
        }
        
        return try {
            val data = bleConnectionManager.read(
                address,
                config.serviceUuid,
                config.characteristicUuid
            )
            Result.success(data)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to read data", e)
            Result.failure(
                ThicknessDeviceError.MeasurementError("Read failed: ${e.message}", e)
            )
        }
    }
    
    /**
     * Подписаться на уведомления от characteristic
     * 
     * @return Flow с данными от устройства
     */
    suspend fun subscribeToNotifications(): Flow<ByteArray> {
        val address = currentDeviceAddress
            ?: throw ThicknessDeviceError.ConnectionError("Device not connected")
        
        return bleConnectionManager.notifications(
            address,
            config.serviceUuid,
            config.characteristicUuid
        )
    }
    
    /**
     * Отписаться от уведомлений
     */
    suspend fun unsubscribeFromNotifications() {
        val address = currentDeviceAddress ?: return
        
        try {
            bleConnectionManager.unsubscribe(
                address,
                config.serviceUuid,
                config.characteristicUuid
            )
        } catch (e: Exception) {
            logger.error(TAG, "Failed to unsubscribe", e)
        }
    }
    
    /**
     * Сканировать BLE устройства и найти толщиномер
     * 
     * @return Адрес найденного устройства
     * @throws ThicknessDeviceError.DeviceNotFoundError если устройство не найдено
     * @throws ThicknessDeviceError.TimeoutError если превышен таймаут сканирования
     */
    private suspend fun scanForDevice(): String {
        logger.info(TAG, "Scanning for thickness gauge device")
        _connectionState.value = ConnectionState.Scanning
        
        return try {
            withTimeout(config.scanTimeout) {
                var foundDevice: String? = null
                
                bleScanner.scan().collect { device ->
                    val deviceName = device.name
                    if (deviceName != null && deviceName.contains(config.deviceNamePattern, ignoreCase = true)) {
                        logger.info(TAG, "Found device: $deviceName, address: ${device.address}")
                        foundDevice = device.address
                        return@collect
                    }
                }
                
                foundDevice ?: throw ThicknessDeviceError.DeviceNotFoundError()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.warn(TAG, "Scan timeout")
            throw ThicknessDeviceError.TimeoutError(
                "Device scan timeout after ${config.scanTimeout}ms",
                config.scanTimeout
            )
        } catch (e: ThicknessDeviceError) {
            throw e
        } catch (e: Exception) {
            logger.error(TAG, "Scan error", e)
            throw ThicknessDeviceError.ConnectionError("Scan failed: ${e.message}", e)
        }
    }
    
    companion object {
        private const val TAG = "ThicknessBleAdapter"
    }
}
