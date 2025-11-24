package com.selfservice.thickness.mock

import com.selfservice.thickness.ThicknessDevice
import com.selfservice.thickness.ThicknessMeasurement
import com.selfservice.thickness.MeasurementStatus
import com.selfservice.thickness.ConnectionStatus
import com.selfservice.thickness.models.MockDeviceConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Мок-реализация толщиномера для DEV/QA режима
 * 
 * Генерирует реалистичные данные измерений с настраиваемыми параметрами.
 * 
 * **ВАЖНО**: Данный класс используется ТОЛЬКО в DEV режиме!
 * В UI отображается метка [MOCK MODE] при использовании мок-устройства.
 * 
 * @since Session 12B
 */
class MockThicknessDevice(
    private val config: MockDeviceConfig = MockDeviceConfig()
) : ThicknessDevice {
    
    private var isConnected = false
    private var isMeasuring = false
    private var measurementCount = 0
    
    /**
     * Подключиться к мок-устройству
     */
    override suspend fun connect(): Result<Unit> {
        if (config.simulateDelay) {
            delay(500) // Имитация задержки подключения
        }
        
        isConnected = true
        return Result.success(Unit)
    }
    
    /**
     * Отключиться от мок-устройства
     */
    override suspend fun disconnect() {
        if (config.simulateDelay) {
            delay(200)
        }
        
        isConnected = false
        isMeasuring = false
        measurementCount = 0
    }
    
    /**
     * Начать измерения (генерация мок-данных)
     * 
     * Генерирует поток измерений с настраиваемыми параметрами:
     * - baseValue: базовое значение
     * - variance: разброс значений
     * - errorRate: вероятность ошибки
     */
    override suspend fun startMeasurements(): Flow<ThicknessMeasurement> = flow {
        if (!isConnected) {
            throw IllegalStateException("[MOCK MODE] Device not connected")
        }
        
        isMeasuring = true
        measurementCount = 0
        
        // Генерируем бесконечный поток измерений
        while (isMeasuring) {
            measurementCount++
            
            if (config.simulateDelay) {
                delay(config.measurementDelay)
            }
            
            val measurement = generateMeasurement()
            emit(measurement)
        }
    }
    
    /**
     * Остановить измерения
     */
    override suspend fun stopMeasurements() {
        isMeasuring = false
    }
    
    /**
     * Получить статус подключения
     */
    override fun getConnectionStatus(): ConnectionStatus {
        return if (isConnected) {
            ConnectionStatus.CONNECTED
        } else {
            ConnectionStatus.DISCONNECTED
        }
    }
    
    /**
     * Сгенерировать одно мок-измерение
     */
    private fun generateMeasurement(): ThicknessMeasurement {
        val zone = "zone_$measurementCount"
        
        // Проверка на ошибку
        if (config.errorRate > 0 && Random.nextFloat() < config.errorRate) {
            return ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 0f,
                zone = zone,
                status = MeasurementStatus.ERROR
            )
        }
        
        // Генерация реалистичного значения
        val value = if (config.randomize) {
            generateRealisticValue()
        } else {
            config.baseValue
        }
        
        return ThicknessMeasurement(
            timestamp = System.currentTimeMillis(),
            value = value,
            zone = zone,
            status = MeasurementStatus.VALID
        )
    }
    
    /**
     * Сгенерировать реалистичное значение измерения
     * 
     * Распределение:
     * - 60% - заводская покраска (60-120 мкм)
     * - 25% - незначительная перекраска (120-180 мкм)
     * - 10% - значительная перекраска (180-300 мкм)
     * - 5% - кузовной ремонт (>300 мкм)
     */
    private fun generateRealisticValue(): Float {
        val roll = Random.nextFloat()
        
        return when {
            roll < 0.60f -> Random.nextFloat() * 60f + 60f  // 60-120
            roll < 0.85f -> Random.nextFloat() * 60f + 120f // 120-180
            roll < 0.95f -> Random.nextFloat() * 120f + 180f // 180-300
            else -> Random.nextFloat() * 200f + 300f // 300-500
        }.let { baseValue ->
            // Добавляем небольшой шум
            baseValue + (Random.nextFloat() - 0.5f) * 10f
        }.coerceIn(50f, 600f)
    }
}
