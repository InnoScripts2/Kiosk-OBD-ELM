package com.selfservice.kiosk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfservice.kiosk.ui.state.ThicknessFlowState
import com.selfservice.kiosk.ui.state.DeviceStatus
import com.selfservice.kiosk.ui.state.Measurement
import com.selfservice.kiosk.ui.state.MeasurementStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for thickness measurement flow
 * Manages device connection and measurement state
 */
class ThicknessFlowViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(ThicknessFlowState())
    val state: StateFlow<ThicknessFlowState> = _state.asStateFlow()
    
    /**
     * Start device connection process
     */
    fun connectDevice() {
        viewModelScope.launch {
            _state.update { it.copy(deviceStatus = DeviceStatus.Connecting) }
            delay(2000) // Simulate connection
            _state.update { it.copy(deviceStatus = DeviceStatus.Connected) }
            delay(500)
            _state.update { it.copy(deviceStatus = DeviceStatus.Ready) }
        }
    }
    
    /**
     * Start measurements
     */
    fun startMeasurements() {
        _state.update { it.copy(deviceStatus = DeviceStatus.Measuring) }
    }
    
    /**
     * Add a single measurement
     */
    fun addMeasurement(zone: String, value: Float) {
        val status = classifyMeasurement(value)
        val measurement = Measurement(zone, value, System.currentTimeMillis(), status)
        
        _state.update { current ->
            val newMeasurements = current.measurements + (zone to measurement)
            current.copy(
                measurements = newMeasurements,
                completedPoints = newMeasurements.size,
                currentZone = zone
            )
        }
    }
    
    /**
     * Classify measurement value
     */
    private fun classifyMeasurement(value: Float): MeasurementStatus {
        return when {
            value < Measurement.MIN_VALUE || value > Measurement.MAX_VALUE -> MeasurementStatus.Invalid
            value < Measurement.NORMAL_MIN -> MeasurementStatus.Critical
            value > Measurement.NORMAL_MAX -> MeasurementStatus.Warning
            else -> MeasurementStatus.Normal
        }
    }
    
    /**
     * Disconnect device
     */
    fun disconnectDevice() {
        _state.update { it.copy(deviceStatus = DeviceStatus.Disconnected) }
    }
    
    /**
     * Reset flow state
     */
    fun reset() {
        _state.value = ThicknessFlowState()
    }
}

