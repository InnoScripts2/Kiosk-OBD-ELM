package com.selfservice.thickness

/**
 * Интерфейс для взаимодействия с толщиномером через BLE.
 * Измерения приходят в режиме реального времени.
 * Все значения — только фактические с устройства.
 */
interface ThicknessDevice {
    /**
     * Подключиться к устройству
     * @return Result с успешным подключением или ошибкой
     */
    suspend fun connect(): Result<Unit>
    
    /**
     * Отключиться от устройства
     */
    suspend fun disconnect()
    
    /**
     * Начать измерения
     * @return Flow с измерениями в реальном времени
     */
    suspend fun startMeasurements(): kotlinx.coroutines.flow.Flow<ThicknessMeasurement>
    
    /**
     * Остановить измерения
     */
    suspend fun stopMeasurements()
    
    /**
     * Получить статус подключения
     */
    fun getConnectionStatus(): ConnectionStatus
}

/**
 * Результат одного измерения толщины покрытия
 */
data class ThicknessMeasurement(
    val timestamp: Long,
    val value: Float, // в микронах (μm)
    val zone: String, // зона кузова
    val status: MeasurementStatus
)

enum class MeasurementStatus {
    VALID,      // измерение корректно
    ERROR,      // ошибка измерения
    TIMEOUT,    // превышен таймаут
    OUT_OF_RANGE // значение за пределами диапазона
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}
