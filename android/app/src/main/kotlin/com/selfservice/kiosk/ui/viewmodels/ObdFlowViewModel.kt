package com.selfservice.kiosk.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfservice.kiosk.ui.state.ObdFlowState
import com.selfservice.kiosk.ui.state.AdapterStatus
import com.selfservice.kiosk.ui.state.DtcCode
import com.selfservice.kiosk.ui.state.DtcSeverity
import com.selfservice.kiosk.ui.state.SystemStatus
import com.selfservice.kiosk.ui.state.ClearResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for OBD diagnostics flow
 * Manages adapter connection and scanning state
 */
class ObdFlowViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(ObdFlowState())
    val state: StateFlow<ObdFlowState> = _state.asStateFlow()
    
    /**
     * Connect to OBD adapter
     */
    fun connectAdapter() {
        viewModelScope.launch {
            _state.update { it.copy(adapterStatus = AdapterStatus.Connecting) }
            delay(2000) // Simulate connection
            _state.update { it.copy(adapterStatus = AdapterStatus.Connected) }
        }
    }
    
    /**
     * Start diagnostic scan
     */
    fun startScan() {
        viewModelScope.launch {
            _state.update { it.copy(adapterStatus = AdapterStatus.Scanning, scanProgress = 0f) }
            
            val startTime = System.currentTimeMillis()
            
            // Simulate scanning progress
            for (i in 1..10) {
                delay(3000) // 3 seconds per step
                val progress = i / 10f
                _state.update { it.copy(scanProgress = progress) }
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            // Simulate scan complete with some mock DTCs
            val dtcs = emptyList<DtcCode>() // Real implementation would read from OBD
            val systemStatus = if (dtcs.isEmpty()) SystemStatus.AllGood else SystemStatus.HasErrors
            
            _state.update {
                it.copy(
                    adapterStatus = AdapterStatus.Complete,
                    scanProgress = 1f,
                    dtcCodes = dtcs,
                    systemStatus = systemStatus,
                    scanDuration = duration
                )
            }
        }
    }
    
    /**
     * Add DTC codes (for testing)
     */
    fun addDtcCodes(codes: List<DtcCode>) {
        _state.update { current ->
            val allCodes = current.dtcCodes + codes
            val systemStatus = when {
                allCodes.any { it.severity == DtcSeverity.Critical } -> SystemStatus.HasErrors
                allCodes.any { it.severity == DtcSeverity.Warning } -> SystemStatus.HasWarnings
                allCodes.isNotEmpty() -> SystemStatus.HasWarnings
                else -> SystemStatus.AllGood
            }
            current.copy(
                dtcCodes = allCodes,
                systemStatus = systemStatus
            )
        }
    }
    
    /**
     * Clear DTC codes
     */
    fun clearDtcCodes() {
        viewModelScope.launch {
            _state.update { it.copy(clearRequested = true) }
            delay(2000) // Simulate clear operation
            
            // In real implementation, this would send clear command to OBD
            _state.update {
                it.copy(
                    clearRequested = false,
                    clearResult = ClearResult.Success,
                    dtcCodes = emptyList(),
                    systemStatus = SystemStatus.AllGood
                )
            }
        }
    }
    
    /**
     * Disconnect adapter
     */
    fun disconnectAdapter() {
        _state.update { it.copy(adapterStatus = AdapterStatus.Disconnected) }
    }
    
    /**
     * Reset flow state
     */
    fun reset() {
        _state.value = ObdFlowState()
    }
}

