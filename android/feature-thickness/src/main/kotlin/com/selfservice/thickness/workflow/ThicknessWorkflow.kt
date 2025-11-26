package com.selfservice.thickness.workflow

import com.selfservice.core.logging.Logger
import com.selfservice.thickness.ThicknessDevice
import com.selfservice.thickness.ThicknessMeasurementStateMachine
import com.selfservice.thickness.models.ThicknessAnalysis
import com.selfservice.thickness.models.ThicknessReport
import com.selfservice.thickness.models.ThicknessZoneLayout
import com.selfservice.thickness.models.ZoneMeasurement
import com.selfservice.thickness.models.MeasurementStatus as ZoneMeasurementStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * Workflow управления процессом измерений толщиномером
 * 
 * Оркестрирует:
 * - Подключение к устройству
 * - Процесс измерений 60 точек
 * - State machine transitions
 * - Таймауты
 * - Генерация отчёта
 * 
 * @since Session 12B
 */
class ThicknessWorkflow(
    private val device: ThicknessDevice,
    private val stateMachine: ThicknessMeasurementStateMachine,
    private val logger: Logger,
    private val connectionTimeoutMs: Long = 5000L,
    private val measurementTimeoutMs: Long = 30000L
) {
    
    private val _workflowState = MutableStateFlow<WorkflowState>(WorkflowState.Idle)
    val workflowState: StateFlow<WorkflowState> = _workflowState.asStateFlow()
    
    private var currentZoneIndex = 0
    private val zoneMeasurements = mutableMapOf<Int, ZoneMeasurement>()
    
    /**
     * Состояния workflow
     */
    sealed class WorkflowState {
        object Idle : WorkflowState()
        object Connecting : WorkflowState()
        object Ready : WorkflowState()
        data class Measuring(val progress: Int, val total: Int) : WorkflowState()
        data class Completed(val report: ThicknessReport) : WorkflowState()
        data class Error(val message: String, val cause: Throwable?) : WorkflowState()
    }
    
    /**
     * Начать процесс измерений
     * 
     * @param sessionId ID сессии
     * @param vehicleType Тип автомобиля (sedan, suv, minivan)
     * @return Result с отчётом или ошибкой
     */
    suspend fun startMeasurementProcess(
        sessionId: String,
        vehicleType: String
    ): Result<ThicknessReport> {
        logger.info(TAG, "Starting thickness measurement process, session: $sessionId")
        
        return try {
            // Шаг 1: Подключение к устройству
            connectToDevice()
            
            // Шаг 2: Выполнение измерений
            performMeasurements()
            
            // Шаг 3: Генерация отчёта
            val report = generateReport(sessionId, vehicleType)
            
            _workflowState.value = WorkflowState.Completed(report)
            logger.info(TAG, "Thickness measurement process completed successfully")
            
            Result.success(report)
        } catch (e: Exception) {
            logger.error(TAG, "Thickness measurement process failed", e)
            _workflowState.value = WorkflowState.Error(e.message ?: "Unknown error", e)
            Result.failure(e)
        } finally {
            // Всегда отключаемся от устройства
            disconnectFromDevice()
        }
    }
    
    /**
     * Подключиться к устройству
     */
    private suspend fun connectToDevice() {
        logger.info(TAG, "Connecting to thickness gauge device")
        _workflowState.value = WorkflowState.Connecting
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        
        val result = withTimeout(connectionTimeoutMs) {
            device.connect()
        }
        
        if (result.isFailure) {
            stateMachine.handleEvent(
                ThicknessMeasurementStateMachine.Event.ConnectionFailed(
                    result.exceptionOrNull() ?: Exception("Unknown connection error")
                )
            )
            throw result.exceptionOrNull() ?: Exception("Failed to connect to device")
        }
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        _workflowState.value = WorkflowState.Ready
        
        logger.info(TAG, "Connected to thickness gauge device")
    }
    
    /**
     * Выполнить измерения 60 точек
     */
    private suspend fun performMeasurements() {
        logger.info(TAG, "Starting measurements")
        
        currentZoneIndex = 0
        zoneMeasurements.clear()
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Подписываемся на поток измерений от устройства
        val measurementsFlow = device.startMeasurements()
        
        try {
            measurementsFlow.collect { measurement ->
                // Обрабатываем измерение
                processIncomingMeasurement(measurement)
                
                // Если собрали все 60 точек - завершаем
                if (currentZoneIndex >= 60) {
                    device.stopMeasurements()
                    stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.AllMeasurementsCompleted)
                    return@collect
                }
            }
        } catch (e: Exception) {
            logger.error(TAG, "Error during measurements", e)
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementError(e))
            throw e
        }
        
        logger.info(TAG, "Measurements completed, collected ${zoneMeasurements.size} zones")
    }
    
    /**
     * Обработать входящее измерение
     */
    private fun processIncomingMeasurement(measurement: com.selfservice.thickness.ThicknessMeasurement) {
        if (currentZoneIndex >= 60) {
            return // Игнорируем лишние измерения
        }
        
        val zone = ThicknessZoneLayout.getZone(currentZoneIndex)
            ?: throw IllegalStateException("Invalid zone index: $currentZoneIndex")
        
        // Создаём ZoneMeasurement с привязкой к зоне кузова
        val zoneMeasurement = ZoneMeasurement(
            zone = zone,
            value = measurement.value,
            status = mapMeasurementStatus(measurement.status),
            timestamp = measurement.timestamp
        )
        
        zoneMeasurements[currentZoneIndex] = zoneMeasurement
        currentZoneIndex++
        
        // Обновляем state machine
        stateMachine.handleEvent(
            ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement)
        )
        
        // Обновляем workflow state
        _workflowState.value = WorkflowState.Measuring(currentZoneIndex, 60)
        
        logger.debug(TAG, "Processed measurement $currentZoneIndex/60, value: ${measurement.value}")
    }
    
    /**
     * Генерировать отчёт на основе собранных измерений
     */
    private fun generateReport(sessionId: String, vehicleType: String): ThicknessReport {
        logger.info(TAG, "Generating thickness report")
        
        // Сортируем измерения по индексу зоны
        val sortedMeasurements = zoneMeasurements.entries
            .sortedBy { it.key }
            .map { it.value }
        
        if (sortedMeasurements.isEmpty()) {
            throw IllegalStateException("No measurements collected")
        }
        
        // Анализируем измерения
        val analysis = ThicknessAnalysis.analyze(sortedMeasurements)
        
        val report = ThicknessReport(
            sessionId = sessionId,
            measurements = sortedMeasurements,
            timestamp = System.currentTimeMillis(),
            vehicleType = vehicleType,
            analysis = analysis
        )
        
        logger.info(TAG, "Report generated: ${sortedMeasurements.size} measurements, " +
                "avg: ${analysis.avgValue}, deviations: ${analysis.deviations}")
        
        return report
    }
    
    /**
     * Отключиться от устройства
     */
    private suspend fun disconnectFromDevice() {
        try {
            logger.info(TAG, "Disconnecting from device")
            device.disconnect()
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Reset)
        } catch (e: Exception) {
            logger.error(TAG, "Error during disconnect", e)
        }
    }
    
    /**
     * Отменить текущий процесс измерений
     */
    suspend fun cancel() {
        logger.info(TAG, "Cancelling thickness measurement process")
        
        try {
            device.stopMeasurements()
            disconnectFromDevice()
        } catch (e: Exception) {
            logger.error(TAG, "Error during cancellation", e)
        }
        
        _workflowState.value = WorkflowState.Idle
    }
    
    /**
     * Повторить процесс с начала
     */
    suspend fun retry(sessionId: String, vehicleType: String): Result<ThicknessReport> {
        logger.info(TAG, "Retrying thickness measurement process")
        
        // Сбрасываем состояние
        cancel()
        
        // Запускаем заново
        return startMeasurementProcess(sessionId, vehicleType)
    }
    
    /**
     * Получить текущий прогресс измерений
     */
    fun getProgress(): Pair<Int, Int> {
        return Pair(currentZoneIndex, 60)
    }
    
    /**
     * Получить текущие собранные измерения
     */
    fun getCurrentMeasurements(): List<ZoneMeasurement> {
        return zoneMeasurements.entries
            .sortedBy { it.key }
            .map { it.value }
    }
    
    private fun mapMeasurementStatus(
        status: com.selfservice.thickness.MeasurementStatus
    ): ZoneMeasurementStatus {
        return when (status) {
            com.selfservice.thickness.MeasurementStatus.VALID -> ZoneMeasurementStatus.VALID
            com.selfservice.thickness.MeasurementStatus.ERROR -> ZoneMeasurementStatus.ERROR
            com.selfservice.thickness.MeasurementStatus.TIMEOUT -> ZoneMeasurementStatus.TIMEOUT
            com.selfservice.thickness.MeasurementStatus.OUT_OF_RANGE -> ZoneMeasurementStatus.OUT_OF_RANGE
        }
    }
    
    companion object {
        private const val TAG = "ThicknessWorkflow"
    }
}
