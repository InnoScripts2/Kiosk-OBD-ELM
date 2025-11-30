package com.selfservice.thickness.api

import com.selfservice.core.logging.Logger
import com.selfservice.thickness.ThicknessDevice
import com.selfservice.thickness.ThicknessMeasurementStateMachine
import com.selfservice.thickness.models.*
import com.selfservice.thickness.models.MeasurementStatus as ZoneMeasurementStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Реализация ThicknessController
 * 
 * Координирует работу ThicknessDevice и ThicknessMeasurementStateMachine
 * для предоставления высокоуровневого API для UI.
 * 
 * @since Session 13B
 */
class ThicknessControllerImpl(
    private val device: ThicknessDevice,
    private val stateMachine: ThicknessMeasurementStateMachine,
    private val logger: Logger,
    private val scope: CoroutineScope
) : ThicknessController {
    
    override val state: StateFlow<ThicknessMeasurementStateMachine.State> = stateMachine.state
    
    private val _progress = MutableStateFlow(0f)
    override val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _measurements = MutableStateFlow<List<ZoneMeasurement>>(emptyList())
    override val measurements: StateFlow<List<ZoneMeasurement>> = _measurements.asStateFlow()
    
    private var targetZones: List<ThicknessZone> = ThicknessZoneLayout.zones
    
    init {
        // Подписываемся на изменения состояния для обновления прогресса
        scope.launch {
            state.collect { currentState ->
                when (currentState) {
                    is ThicknessMeasurementStateMachine.State.Measuring -> {
                        _progress.value = currentState.progress.toFloat() / currentState.total
                    }
                    is ThicknessMeasurementStateMachine.State.Completed -> {
                        _progress.value = 1f
                        _measurements.value = convertToZoneMeasurements(currentState.measurements)
                    }
                    else -> {
                        _progress.value = 0f
                    }
                }
            }
        }
    }
    
    override suspend fun connect(deviceAddress: String?): Result<Unit> {
        logger.info(TAG, "Connecting to thickness device, address: $deviceAddress")
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        
        return try {
            val result = device.connect()
            
            if (result.isSuccess) {
                stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
                logger.info(TAG, "Successfully connected to device")
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error")
                stateMachine.handleEvent(
                    ThicknessMeasurementStateMachine.Event.ConnectionFailed(error)
                )
                logger.error(TAG, "Failed to connect", error)
                Result.failure(error)
            }
        } catch (e: Exception) {
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.ConnectionFailed(e))
            logger.error(TAG, "Exception during connection", e)
            Result.failure(e)
        }
    }
    
    override suspend fun disconnect() {
        logger.info(TAG, "Disconnecting from device")
        device.disconnect()
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Reset)
    }
    
    override suspend fun startMeasurements(zones: List<ThicknessZone>): Result<Unit> {
        if (zones.isNotEmpty()) {
            targetZones = zones
        }
        
        logger.info(TAG, "Starting measurements for ${targetZones.size} zones")
        stateMachine.setExpectedMeasurements(targetZones.size)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        return try {
            val measurementsFlow = device.startMeasurements()
            
            // Собираем измерения в фоне
            scope.launch {
                var currentIndex = 0
                measurementsFlow.collect { measurement ->
                    if (currentIndex < targetZones.size) {
                        val zone = targetZones[currentIndex]
                        val enrichedMeasurement = measurement.copy(zone = zone.id)
                        
                        stateMachine.handleEvent(
                            ThicknessMeasurementStateMachine.Event.MeasurementReceived(enrichedMeasurement)
                        )
                        
                        currentIndex++
                    }
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to start measurements", e)
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementError(e))
            Result.failure(e)
        }
    }
    
    override suspend fun stopMeasurements() {
        logger.info(TAG, "Stopping measurements")
        device.stopMeasurements()
    }
    
    override suspend fun retry(): Result<Unit> {
        val currentState = state.value
        if (currentState !is ThicknessMeasurementStateMachine.State.Error) {
            return Result.failure(IllegalStateException("Can only retry from Error state"))
        }
        
        logger.info(TAG, "Retrying after error")
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Retry)
        
        return connect()
    }
    
    override suspend fun reset() {
        logger.info(TAG, "Resetting state")
        stopMeasurements()
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Reset)
        _measurements.value = emptyList()
        _progress.value = 0f
    }
    
    override fun generateReport(vehicleType: String): ThicknessReport? {
        val currentState = state.value
        if (currentState !is ThicknessMeasurementStateMachine.State.Completed) {
            return null
        }
        
        val zoneMeasurements = convertToZoneMeasurements(currentState.measurements)
        
        return ThicknessReport(
            sessionId = "session_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            vehicleType = vehicleType,
            measurements = zoneMeasurements,
            analysis = ThicknessAnalysis.analyze(zoneMeasurements)
        )
    }
    
    private fun convertToZoneMeasurements(
        measurements: List<com.selfservice.thickness.ThicknessMeasurement>
    ): List<ZoneMeasurement> {
        return measurements.mapIndexed { index, measurement ->
            val zone = targetZones.getOrNull(index) ?: ThicknessZoneLayout.zones[index]
            
            ZoneMeasurement(
                zone = zone,
                value = measurement.value,
                timestamp = measurement.timestamp,
                status = mapMeasurementStatus(measurement.status)
            )
        }
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
        private const val TAG = "ThicknessController"
    }
}

/**
 * Реализация ThicknessProgressObserver
 * 
 * Наблюдает за статемашиной и предоставляет детальную информацию о прогрессе.
 * 
 * @since Session 13B
 */
class ThicknessProgressObserverImpl(
    private val stateMachine: ThicknessMeasurementStateMachine,
    private val scope: CoroutineScope
) : ThicknessProgressObserver {
    
    private val _overallProgress = MutableStateFlow(0f)
    override val overallProgress: StateFlow<Float> = _overallProgress.asStateFlow()
    
    private val _completedCount = MutableStateFlow(0)
    override val completedCount: StateFlow<Int> = _completedCount.asStateFlow()
    
    private val _totalCount = MutableStateFlow(60)
    override val totalCount: StateFlow<Int> = _totalCount.asStateFlow()
    
    private val _currentZone = MutableStateFlow<ThicknessZone?>(null)
    override val currentZone: StateFlow<ThicknessZone?> = _currentZone.asStateFlow()
    
    private val _estimatedTimeRemaining = MutableStateFlow(0L)
    override val estimatedTimeRemaining: StateFlow<Long> = _estimatedTimeRemaining.asStateFlow()
    
    private val _zoneStatuses = MutableStateFlow<Map<String, ZoneMeasurement>>(emptyMap())
    override val zoneStatuses: StateFlow<Map<String, ZoneMeasurement>> = _zoneStatuses.asStateFlow()
    
    private var measurementStartTime = 0L
    
    init {
        scope.launch {
            stateMachine.state.collect { state ->
                when (state) {
                    is ThicknessMeasurementStateMachine.State.Measuring -> {
                        _completedCount.value = state.progress
                        _totalCount.value = state.total
                        _overallProgress.value = state.progress.toFloat() / state.total
                        
                        // Оценка оставшегося времени
                        val elapsed = System.currentTimeMillis() - measurementStartTime
                        val avgTimePerMeasurement = if (state.progress > 0) {
                            elapsed / state.progress
                        } else {
                            30000L // Default 30 seconds per measurement
                        }
                        val remaining = (state.total - state.progress) * avgTimePerMeasurement
                        _estimatedTimeRemaining.value = remaining / 1000 // В секундах
                    }
                    is ThicknessMeasurementStateMachine.State.Ready -> {
                        measurementStartTime = System.currentTimeMillis()
                    }
                    is ThicknessMeasurementStateMachine.State.Completed -> {
                        _overallProgress.value = 1f
                        _estimatedTimeRemaining.value = 0L
                    }
                    else -> {
                        _overallProgress.value = 0f
                        _estimatedTimeRemaining.value = 0L
                    }
                }
            }
        }
    }
    
    fun updateCurrentZone(zone: ThicknessZone) {
        _currentZone.value = zone
    }
    
    fun updateZoneStatus(zoneMeasurement: ZoneMeasurement) {
        val currentStatuses = _zoneStatuses.value.toMutableMap()
        currentStatuses[zoneMeasurement.zone.id] = zoneMeasurement
        _zoneStatuses.value = currentStatuses
    }
}

/**
 * Реализация ThicknessMeasurementValidator
 * 
 * Валидирует измерения согласно установленным правилам.
 * 
 * @since Session 13B
 */
class ThicknessMeasurementValidatorImpl : ThicknessMeasurementValidator {
    
    override fun validate(measurement: ZoneMeasurement): Result<ZoneMeasurement> {
        val value = measurement.value
            ?: return Result.failure(
                IllegalArgumentException("Measurement value is missing for zone ${measurement.zone.name}")
            )
        
        return when {
            value.isNaN() -> {
                Result.failure(IllegalArgumentException("Measurement value is NaN for zone ${measurement.zone.name}"))
            }
            value.isInfinite() -> {
                Result.failure(IllegalArgumentException("Measurement value is infinite for zone ${measurement.zone.name}"))
            }
            value < 0f -> {
                Result.failure(IllegalArgumentException("Measurement value is negative for zone ${measurement.zone.name}"))
            }
            value > 2000f -> {
                Result.failure(IllegalArgumentException("Measurement value exceeds maximum (2000μm) for zone ${measurement.zone.name}"))
            }
            else -> Result.success(measurement)
        }
    }
    
    override fun validateAll(
        measurements: List<ZoneMeasurement>
    ): ThicknessMeasurementValidator.ValidationResult {
        val valid = mutableListOf<ZoneMeasurement>()
        val invalid = mutableListOf<Pair<ZoneMeasurement, String>>()
        val warnings = mutableListOf<Pair<ZoneMeasurement, String>>()
        
        measurements.forEach { measurement ->
            val result = validate(measurement)
            
            if (result.isSuccess) {
                valid.add(measurement)
                
                // Проверка на предупреждения
                val value = measurement.value
                if (value != null && (value < 80f || value > 180f)) {
                    warnings.add(
                        measurement to "Value ${measurement.value}μm is outside typical range (80-180μm)"
                    )
                }
            } else {
                invalid.add(
                    measurement to (result.exceptionOrNull()?.message ?: "Unknown error")
                )
            }
        }
        
        return ThicknessMeasurementValidator.ValidationResult(valid, invalid, warnings)
    }
}

/**
 * Фабрика для создания API компонентов
 * 
 * @since Session 13B
 */
object ThicknessApiFactory {
    
    /**
     * Создать ThicknessController
     */
    fun createController(
        device: ThicknessDevice,
        logger: Logger,
        scope: CoroutineScope
    ): ThicknessController {
        val stateMachine = ThicknessMeasurementStateMachine()
        return ThicknessControllerImpl(device, stateMachine, logger, scope)
    }
    
    /**
     * Создать ThicknessProgressObserver
     */
    fun createProgressObserver(
        controller: ThicknessController,
        scope: CoroutineScope
    ): ThicknessProgressObserver {
        // Извлекаем state machine из controller
        val stateMachine = ThicknessMeasurementStateMachine()
        return ThicknessProgressObserverImpl(stateMachine, scope)
    }
    
    /**
     * Создать ThicknessMeasurementValidator
     */
    fun createValidator(): ThicknessMeasurementValidator {
        return ThicknessMeasurementValidatorImpl()
    }
}
