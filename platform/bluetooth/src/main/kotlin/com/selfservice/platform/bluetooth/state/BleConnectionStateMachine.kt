package com.selfservice.platform.bluetooth.state

import com.selfservice.platform.bluetooth.ble.BleConnectionState
import com.selfservice.platform.bluetooth.ble.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State machine для управления жизненным циклом BLE соединения
 * 
 * Управляет переходами между состояниями:
 * DISCONNECTED -> CONNECTING -> DISCOVERING_SERVICES -> CONNECTED
 * CONNECTED -> DISCONNECTING -> DISCONNECTED
 * 
 * @since Session 07B
 */
class BleConnectionStateMachine {
    
    private val _state = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val state: StateFlow<BleConnectionState> = _state.asStateFlow()
    
    private val _device = MutableStateFlow<BleDevice?>(null)
    val device: StateFlow<BleDevice?> = _device.asStateFlow()
    
    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()
    
    private val stateHistory = mutableListOf<StateTransition>()
    
    /**
     * Получить текущее состояние
     */
    fun getCurrentState(): BleConnectionState = _state.value
    
    /**
     * Получить подключенное устройство
     */
    fun getConnectedDevice(): BleDevice? = _device.value
    
    /**
     * Начать подключение к устройству
     */
    fun startConnecting(device: BleDevice) {
        require(_state.value == BleConnectionState.DISCONNECTED) {
            "Невозможно начать подключение из состояния ${_state.value}"
        }
        
        _device.value = device
        transitionTo(BleConnectionState.CONNECTING)
    }
    
    /**
     * Подключение установлено, начать обнаружение сервисов
     */
    fun onConnected() {
        require(_state.value == BleConnectionState.CONNECTING) {
            "Невозможно перейти в DISCOVERING_SERVICES из состояния ${_state.value}"
        }
        
        transitionTo(BleConnectionState.DISCOVERING_SERVICES)
    }
    
    /**
     * Сервисы обнаружены, соединение готово
     */
    fun onServicesDiscovered() {
        require(_state.value == BleConnectionState.DISCOVERING_SERVICES) {
            "Невозможно перейти в CONNECTED из состояния ${_state.value}"
        }
        
        transitionTo(BleConnectionState.CONNECTED)
    }
    
    /**
     * Начать отключение
     */
    fun startDisconnecting() {
        require(_state.value in setOf(
            BleConnectionState.CONNECTING,
            BleConnectionState.DISCOVERING_SERVICES,
            BleConnectionState.CONNECTED
        )) {
            "Невозможно начать отключение из состояния ${_state.value}"
        }
        
        transitionTo(BleConnectionState.DISCONNECTING)
    }
    
    /**
     * Отключение завершено
     */
    fun onDisconnected() {
        _device.value = null
        _lastError.value = null
        transitionTo(BleConnectionState.DISCONNECTED)
    }
    
    /**
     * Ошибка подключения
     */
    fun onError(error: Throwable) {
        _lastError.value = error
        
        // При ошибке всегда возвращаемся в DISCONNECTED
        _device.value = null
        transitionTo(BleConnectionState.DISCONNECTED)
    }
    
    /**
     * Сбросить state machine
     */
    fun reset() {
        _state.value = BleConnectionState.DISCONNECTED
        _device.value = null
        _lastError.value = null
        stateHistory.clear()
    }
    
    /**
     * Получить историю переходов состояний
     */
    fun getStateHistory(): List<StateTransition> = stateHistory.toList()
    
    /**
     * Проверить, можно ли выполнить операцию в текущем состоянии
     */
    fun canPerformOperation(operation: BleOperation): Boolean {
        return when (operation) {
            BleOperation.CONNECT -> _state.value == BleConnectionState.DISCONNECTED
            BleOperation.DISCONNECT -> _state.value != BleConnectionState.DISCONNECTED
            BleOperation.READ, BleOperation.WRITE, BleOperation.SUBSCRIBE -> 
                _state.value == BleConnectionState.CONNECTED
            BleOperation.DISCOVER_SERVICES -> 
                _state.value == BleConnectionState.DISCOVERING_SERVICES
        }
    }
    
    private fun transitionTo(newState: BleConnectionState) {
        val oldState = _state.value
        _state.value = newState
        
        stateHistory.add(
            StateTransition(
                from = oldState,
                to = newState,
                timestamp = System.currentTimeMillis(),
                device = _device.value
            )
        )
        
        // Ограничиваем историю последними 100 переходами
        if (stateHistory.size > 100) {
            stateHistory.removeAt(0)
        }
    }
}

/**
 * Запись перехода между состояниями
 */
data class StateTransition(
    val from: BleConnectionState,
    val to: BleConnectionState,
    val timestamp: Long,
    val device: BleDevice?
)

/**
 * Тип BLE операции
 */
enum class BleOperation {
    CONNECT,
    DISCONNECT,
    DISCOVER_SERVICES,
    READ,
    WRITE,
    SUBSCRIBE
}

/**
 * Валидатор состояний для безопасных переходов
 */
object BleStateValidator {
    
    /**
     * Проверить, является ли переход допустимым
     */
    fun isValidTransition(from: BleConnectionState, to: BleConnectionState): Boolean {
        return when (from) {
            BleConnectionState.DISCONNECTED -> to == BleConnectionState.CONNECTING
            BleConnectionState.CONNECTING -> to in setOf(
                BleConnectionState.DISCOVERING_SERVICES,
                BleConnectionState.DISCONNECTING,
                BleConnectionState.DISCONNECTED
            )
            BleConnectionState.DISCOVERING_SERVICES -> to in setOf(
                BleConnectionState.CONNECTED,
                BleConnectionState.DISCONNECTING,
                BleConnectionState.DISCONNECTED
            )
            BleConnectionState.CONNECTED -> to in setOf(
                BleConnectionState.DISCONNECTING,
                BleConnectionState.DISCONNECTED
            )
            BleConnectionState.DISCONNECTING -> to == BleConnectionState.DISCONNECTED
        }
    }
    
    /**
     * Получить список допустимых переходов из текущего состояния
     */
    fun getAllowedTransitions(from: BleConnectionState): Set<BleConnectionState> {
        return when (from) {
            BleConnectionState.DISCONNECTED -> setOf(BleConnectionState.CONNECTING)
            BleConnectionState.CONNECTING -> setOf(
                BleConnectionState.DISCOVERING_SERVICES,
                BleConnectionState.DISCONNECTING,
                BleConnectionState.DISCONNECTED
            )
            BleConnectionState.DISCOVERING_SERVICES -> setOf(
                BleConnectionState.CONNECTED,
                BleConnectionState.DISCONNECTING,
                BleConnectionState.DISCONNECTED
            )
            BleConnectionState.CONNECTED -> setOf(
                BleConnectionState.DISCONNECTING,
                BleConnectionState.DISCONNECTED
            )
            BleConnectionState.DISCONNECTING -> setOf(BleConnectionState.DISCONNECTED)
        }
    }
}
