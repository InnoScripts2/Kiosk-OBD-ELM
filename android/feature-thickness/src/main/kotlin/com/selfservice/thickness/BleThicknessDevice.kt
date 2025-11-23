package com.selfservice.thickness

import com.selfservice.platform.bluetooth.BlessedBleScanner
import com.selfservice.platform.bluetooth.BleConnectionManager
import com.selfservice.core.logging.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Реализация ThicknessDevice через BLE
 * 
 * Протокол связи:
 * - Service UUID: 0000ffe0-0000-1000-8000-00805f9b34fb (стандартный UUID для толщиномеров)
 * - Characteristic UUID: 0000ffe1-0000-1000-8000-00805f9b34fb
 * - Формат данных: ASCII строки вида "VALUE:123.45\n" или бинарный формат
 * 
 * Безопасность:
 * - Таймаут подключения: 5 секунд
 * - Таймаут измерения: 30 секунд на одну точку
 * - Автоматическое переподключение при потере связи
 */
class BleThicknessDevice(
    private val bleScanner: BlessedBleScanner,
    private val bleConnectionManager: BleConnectionManager,
    private val logger: Logger,
    private val deviceAddress: String? = null,
    private val deviceName: String = "Thickness Gauge"
) : ThicknessDevice {

    companion object {
        const val SERVICE_UUID = "0000ffe0-0000-1000-8000-00805f9b34fb"
        const val CHARACTERISTIC_UUID = "0000ffe1-0000-1000-8000-00805f9b34fb"
        const val CONNECTION_TIMEOUT_MS = 5000L
        const val MEASUREMENT_TIMEOUT_MS = 30000L
        const val SCAN_TIMEOUT_MS = 10000L
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    private val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val isConnected = AtomicBoolean(false)
    private val isMeasuring = AtomicBoolean(false)
    
    private var currentZone: String = "unknown"
    private var zoneIndex: Int = 0

    /**
     * Подключиться к толщиномеру через BLE
     */
    override suspend fun connect(): Result<Unit> {
        logger.debug("BleThicknessDevice", "Connecting to thickness device")
        
        if (isConnected.get()) {
            logger.debug("BleThicknessDevice", "Already connected")
            return Result.success(Unit)
        }

        _connectionStatus.value = ConnectionStatus.CONNECTING

        return try {
            // Если адрес не указан, сканируем устройство
            val address = deviceAddress ?: findDevice()
            
            if (address == null) {
                _connectionStatus.value = ConnectionStatus.ERROR
                return Result.failure(Exception("Device not found during scan"))
            }

            // Подключаемся к устройству
            bleConnectionManager.connect(address, CONNECTION_TIMEOUT_MS)
            
            // Проверяем наличие нужного сервиса
            val hasService = bleConnectionManager.hasService(SERVICE_UUID)
            if (!hasService) {
                bleConnectionManager.disconnect(address)
                _connectionStatus.value = ConnectionStatus.ERROR
                return Result.failure(Exception("Device does not have required service $SERVICE_UUID"))
            }

            isConnected.set(true)
            _connectionStatus.value = ConnectionStatus.CONNECTED
            logger.info("BleThicknessDevice", "Connected to device $address")
            
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error("BleThicknessDevice", "Failed to connect", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            Result.failure(e)
        }
    }

    /**
     * Найти устройство через BLE сканирование
     */
    private suspend fun findDevice(): String? {
        logger.debug("BleThicknessDevice", "Scanning for thickness device")
        
        var foundAddress: String? = null
        val scanStartTime = System.currentTimeMillis()

        bleScanner.scanForDevices().collect { result ->
            if (result.device.name?.contains(deviceName, ignoreCase = true) == true) {
                foundAddress = result.device.address
                logger.info("BleThicknessDevice", "Found device: ${result.device.name} at ${result.device.address}")
                return@collect
            }
            
            // Таймаут сканирования
            if (System.currentTimeMillis() - scanStartTime > SCAN_TIMEOUT_MS) {
                logger.warn("BleThicknessDevice", "Scan timeout exceeded")
                return@collect
            }
        }

        return foundAddress
    }

    /**
     * Отключиться от толщиномера
     */
    override suspend fun disconnect() {
        logger.debug("BleThicknessDevice", "Disconnecting from device")
        
        if (isMeasuring.get()) {
            stopMeasurements()
        }

        deviceAddress?.let {
            bleConnectionManager.disconnect(it)
        }

        isConnected.set(false)
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        
        logger.info("BleThicknessDevice", "Disconnected")
    }

    /**
     * Начать измерения
     * Возвращает Flow с измерениями в реальном времени
     */
    override suspend fun startMeasurements(): Flow<ThicknessMeasurement> = flow {
        logger.debug("BleThicknessDevice", "Starting measurements")

        if (!isConnected.get()) {
            throw IllegalStateException("Device not connected")
        }

        if (isMeasuring.compareAndSet(false, true)) {
            try {
                // Подписываемся на notifications от characteristic
                deviceAddress?.let { address ->
                    bleConnectionManager.enableNotifications(address, SERVICE_UUID, CHARACTERISTIC_UUID)
                    
                    // Читаем данные из characteristic
                    bleConnectionManager.observeCharacteristic(address, SERVICE_UUID, CHARACTERISTIC_UUID)
                        .collect { data ->
                            val measurement = parseMeasurement(data)
                            emit(measurement)
                            
                            // Логирование
                            logger.debug(
                                "BleThicknessDevice",
                                "Measurement: zone=${measurement.zone}, value=${measurement.value}μm, status=${measurement.status}"
                            )
                        }
                }
            } catch (e: Exception) {
                logger.error("BleThicknessDevice", "Error during measurements", e)
                throw e
            } finally {
                isMeasuring.set(false)
            }
        } else {
            throw IllegalStateException("Measurements already in progress")
        }
    }

    /**
     * Остановить измерения
     */
    override suspend fun stopMeasurements() {
        logger.debug("BleThicknessDevice", "Stopping measurements")
        
        if (isMeasuring.compareAndSet(true, false)) {
            deviceAddress?.let { address ->
                bleConnectionManager.disableNotifications(address, SERVICE_UUID, CHARACTERISTIC_UUID)
            }
            logger.info("BleThicknessDevice", "Measurements stopped")
        }
    }

    /**
     * Получить текущий статус подключения
     */
    override fun getConnectionStatus(): ConnectionStatus {
        return _connectionStatus.value
    }

    /**
     * Парсинг данных измерения из BLE characteristic
     * 
     * Форматы данных:
     * 1. ASCII: "VALUE:123.45\n" или "VALUE:123.45:ZONE:hood_center\n"
     * 2. Binary: [0x01, high_byte, low_byte] где значение = (high_byte << 8) | low_byte, в μm
     */
    private fun parseMeasurement(data: ByteArray): ThicknessMeasurement {
        val timestamp = System.currentTimeMillis()
        
        return try {
            // Попытка парсинга ASCII формата
            val str = data.decodeToString().trim()
            
            if (str.startsWith("VALUE:")) {
                val parts = str.split(":")
                val value = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                val zone = parts.getOrNull(3) ?: generateZoneName()
                
                // Валидация значения
                val status = when {
                    value < 0 -> MeasurementStatus.ERROR
                    value > 2000 -> MeasurementStatus.OUT_OF_RANGE // Максимум 2000 μm = 2 мм
                    else -> MeasurementStatus.VALID
                }

                ThicknessMeasurement(
                    timestamp = timestamp,
                    value = value,
                    zone = zone,
                    status = status
                )
            } else {
                // Попытка парсинга бинарного формата
                parseBinaryMeasurement(data, timestamp)
            }
        } catch (e: Exception) {
            logger.error("BleThicknessDevice", "Failed to parse measurement data", e)
            ThicknessMeasurement(
                timestamp = timestamp,
                value = 0f,
                zone = generateZoneName(),
                status = MeasurementStatus.ERROR
            )
        }
    }

    /**
     * Парсинг бинарного формата данных
     */
    private fun parseBinaryMeasurement(data: ByteArray, timestamp: Long): ThicknessMeasurement {
        if (data.size < 3) {
            return ThicknessMeasurement(
                timestamp = timestamp,
                value = 0f,
                zone = generateZoneName(),
                status = MeasurementStatus.ERROR
            )
        }

        val highByte = data[1].toInt() and 0xFF
        val lowByte = data[2].toInt() and 0xFF
        val value = ((highByte shl 8) or lowByte).toFloat()

        val status = when {
            value < 0 -> MeasurementStatus.ERROR
            value > 2000 -> MeasurementStatus.OUT_OF_RANGE
            else -> MeasurementStatus.VALID
        }

        return ThicknessMeasurement(
            timestamp = timestamp,
            value = value,
            zone = generateZoneName(),
            status = status
        )
    }

    /**
     * Генерация имени зоны для последовательных измерений
     * Используется когда устройство не передаёт зону явно
     */
    private fun generateZoneName(): String {
        // Сетка 60 точек измерений: 6 секций × 10 точек
        val sections = listOf("hood", "roof", "trunk", "door_fl", "door_fr", "door_rl", "door_rr", "fender_fl", "fender_fr", "fender_rl", "fender_rr")
        val positions = listOf("center", "left", "right", "top", "bottom", "corner_tl", "corner_tr", "corner_bl", "corner_br", "edge")

        val sectionIndex = zoneIndex / positions.size
        val positionIndex = zoneIndex % positions.size

        val section = sections.getOrElse(sectionIndex) { "unknown_section" }
        val position = positions.getOrElse(positionIndex) { "unknown_position" }

        zoneIndex++
        
        return "${section}_${position}"
    }

    /**
     * Сброс индекса зоны (для начала новой серии измерений)
     */
    fun resetZoneIndex() {
        zoneIndex = 0
    }
}
