package com.selfservice.kiosk.ui.state

/**
 * OBD diagnostics flow state
 */
data class ObdFlowState(
    val adapterStatus: AdapterStatus = AdapterStatus.Disconnected,
    val scanProgress: Float = 0f,
    val dtcCodes: List<DtcCode> = emptyList(),
    val systemStatus: SystemStatus = SystemStatus.Unknown,
    val scanDuration: Long = 0L,
    val clearRequested: Boolean = false,
    val clearResult: ClearResult? = null
) {
    val hasErrors: Boolean get() = dtcCodes.isNotEmpty()
    val criticalCount: Int get() = dtcCodes.count { it.severity == DtcSeverity.Critical }
    val warningCount: Int get() = dtcCodes.count { it.severity == DtcSeverity.Warning }
}

/**
 * DTC (Diagnostic Trouble Code)
 */
data class DtcCode(
    val code: String,
    val description: String,
    val severity: DtcSeverity,
    val system: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DTC severity levels
 */
enum class DtcSeverity {
    Info,        // Информационный
    Warning,     // Предупреждение
    Critical     // Критический
}

/**
 * System status summary
 */
enum class SystemStatus {
    Unknown,
    AllGood,     // Все системы в порядке
    HasWarnings, // Есть предупреждения
    HasErrors    // Есть ошибки
}

/**
 * OBD adapter connection status
 */
sealed class AdapterStatus {
    object Disconnected : AdapterStatus()
    object Connecting : AdapterStatus()
    object Connected : AdapterStatus()
    object Scanning : AdapterStatus()
    object Complete : AdapterStatus()
    data class Error(val message: String) : AdapterStatus()
}

/**
 * Result of clearing DTC codes
 */
sealed class ClearResult {
    object Success : ClearResult()
    data class Failure(val error: String) : ClearResult()
}
