package com.selfservice.thickness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State machine для управления процессом измерений толщины ЛКП
 * 
 * Состояния:
 * - IDLE: начальное состояние, устройство не подключено
 * - CONNECTING: подключение к толщиномеру
 * - READY: готов к измерениям
 * - MEASURING: идёт процесс измерений
 * - COMPLETED: все измерения выполнены
 * - ERROR: ошибка в процессе
 * 
 * Переходы:
 * IDLE -> CONNECTING (connect)
 * CONNECTING -> READY (onConnected)
 * CONNECTING -> ERROR (onConnectionFailed)
 * READY -> MEASURING (startMeasurements)
 * MEASURING -> COMPLETED (allMeasurementsCompleted)
 * MEASURING -> ERROR (onMeasurementError)
 * ERROR -> READY (retry)
 * COMPLETED -> IDLE (reset)
 */
class ThicknessMeasurementStateMachine {

    /**
     * Состояния процесса измерений
     */
    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Ready : State()
        data class Measuring(val progress: Int, val total: Int) : State()
        data class Completed(val measurements: List<ThicknessMeasurement>) : State()
        data class Error(val message: String, val cause: Throwable? = null) : State()
    }

    /**
     * События, вызывающие переходы
     */
    sealed class Event {
        object Connect : Event()
        object Connected : Event()
        data class ConnectionFailed(val error: Throwable) : Event()
        object StartMeasurements : Event()
        data class MeasurementReceived(val measurement: ThicknessMeasurement) : Event()
        object AllMeasurementsCompleted : Event()
        data class MeasurementError(val error: Throwable) : Event()
        object Retry : Event()
        object Reset : Event()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val measurements = mutableListOf<ThicknessMeasurement>()
    private var expectedMeasurements = 60 // 60 точек по умолчанию

    /**
     * Обработка события
     */
    fun handleEvent(event: Event) {
        val currentState = _state.value
        val newState = when (event) {
            is Event.Connect -> handleConnect(currentState)
            is Event.Connected -> handleConnected(currentState)
            is Event.ConnectionFailed -> handleConnectionFailed(currentState, event)
            is Event.StartMeasurements -> handleStartMeasurements(currentState)
            is Event.MeasurementReceived -> handleMeasurementReceived(currentState, event)
            is Event.AllMeasurementsCompleted -> handleAllMeasurementsCompleted(currentState)
            is Event.MeasurementError -> handleMeasurementError(currentState, event)
            is Event.Retry -> handleRetry(currentState)
            is Event.Reset -> handleReset(currentState)
        }

        if (newState != null) {
            _state.value = newState
        }
    }

    /**
     * Установить количество ожидаемых измерений
     */
    fun setExpectedMeasurements(count: Int) {
        expectedMeasurements = count
    }

    /**
     * Получить текущее состояние
     */
    fun getCurrentState(): State = _state.value

    /**
     * Получить собранные измерения
     */
    fun getMeasurements(): List<ThicknessMeasurement> = measurements.toList()

    // === Обработчики переходов ===

    private fun handleConnect(current: State): State? {
        return when (current) {
            is State.Idle -> State.Connecting
            else -> null // Игнорируем событие в других состояниях
        }
    }

    private fun handleConnected(current: State): State? {
        return when (current) {
            is State.Connecting -> State.Ready
            else -> null
        }
    }

    private fun handleConnectionFailed(current: State, event: Event.ConnectionFailed): State? {
        return when (current) {
            is State.Connecting -> State.Error("Connection failed: ${event.error.message}", event.error)
            else -> null
        }
    }

    private fun handleStartMeasurements(current: State): State? {
        return when (current) {
            is State.Ready -> {
                measurements.clear()
                State.Measuring(0, expectedMeasurements)
            }
            else -> null
        }
    }

    private fun handleMeasurementReceived(current: State, event: Event.MeasurementReceived): State? {
        return when (current) {
            is State.Measuring -> {
                measurements.add(event.measurement)
                val progress = measurements.size
                
                if (progress >= expectedMeasurements) {
                    // Автоматически переходим в Completed если достигли целевого количества
                    State.Completed(measurements.toList())
                } else {
                    State.Measuring(progress, expectedMeasurements)
                }
            }
            else -> null
        }
    }

    private fun handleAllMeasurementsCompleted(current: State): State? {
        return when (current) {
            is State.Measuring -> State.Completed(measurements.toList())
            else -> null
        }
    }

    private fun handleMeasurementError(current: State, event: Event.MeasurementError): State? {
        return when (current) {
            is State.Measuring -> State.Error("Measurement error: ${event.error.message}", event.error)
            else -> null
        }
    }

    private fun handleRetry(current: State): State? {
        return when (current) {
            is State.Error -> State.Ready
            else -> null
        }
    }

    private fun handleReset(current: State): State? {
        measurements.clear()
        return State.Idle
    }
}
