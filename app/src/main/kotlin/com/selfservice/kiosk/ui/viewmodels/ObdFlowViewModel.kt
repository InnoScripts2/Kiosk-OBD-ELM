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
            val startTime = System.currentTimeMillis()
            _state.update {
                it.copy(
                    adapterStatus = AdapterStatus.Scanning,
                    scanProgress = 0f,
                    scanDuration = 0L
                )
            }

            // Simulate scanning progress
            for (i in 1..10) {
                delay(3000) // 3 seconds per step
                val progress = i / 10f
                val elapsed = System.currentTimeMillis() - startTime
                _state.update {
                    it.copy(
                        scanProgress = progress,
                        scanDuration = elapsed
                    )
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            // Simulate scan complete with some mock DTCs
            val dtcs = _state.value.dtcCodes // Real implementation would read from OBD
            val systemStatus = determineSystemStatus(dtcs)
            
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
            current.copy(
                dtcCodes = allCodes,
                systemStatus = determineSystemStatus(allCodes)
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
                val clearedCodes = emptyList<DtcCode>()
                it.copy(
                    clearRequested = false,
                    clearResult = ClearResult.Success,
                    dtcCodes = clearedCodes,
                    systemStatus = determineSystemStatus(clearedCodes)
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

    private fun determineSystemStatus(codes: List<DtcCode>): SystemStatus = when {
        codes.any { it.severity == DtcSeverity.Critical } -> SystemStatus.HasErrors
        codes.any { it.severity == DtcSeverity.Warning } -> SystemStatus.HasWarnings
        codes.isNotEmpty() -> SystemStatus.HasWarnings
        else -> SystemStatus.AllGood
    }
}

