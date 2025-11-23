package com.selfservice.kiosk.ui.state

/**
 * Thickness measurement flow state
 */
data class ThicknessFlowState(
    val deviceStatus: DeviceStatus = DeviceStatus.Disconnected,
    val measurements: Map<String, Measurement> = emptyMap(),
    val totalPoints: Int = 60,
    val completedPoints: Int = 0,
    val currentZone: String? = null
) {
    val progress: Float get() = if (totalPoints > 0) completedPoints.toFloat() / totalPoints else 0f
    val isComplete: Boolean get() = completedPoints >= totalPoints
}

/**
 * Single thickness measurement
 */
data class Measurement(
    val zone: String,
    val value: Float, // микроны (μm)
    val timestamp: Long = System.currentTimeMillis(),
    val status: MeasurementStatus = MeasurementStatus.Normal
) {
    companion object {
        const val MIN_VALUE = 0f
        const val MAX_VALUE = 2000f
        const val NORMAL_MIN = 80f
        const val NORMAL_MAX = 200f
    }
}

/**
 * Measurement status classification
 */
enum class MeasurementStatus {
    Normal,      // В норме
    Warning,     // Предупреждение (слишком тонкий или толстый слой)
    Critical,    // Критический (возможна перекраска)
    Invalid      // Недействительное измерение
}

/**
 * Device connection status
 */
sealed class DeviceStatus {
    object Disconnected : DeviceStatus()
    object Connecting : DeviceStatus()
    object Connected : DeviceStatus()
    object Ready : DeviceStatus()
    object Measuring : DeviceStatus()
    data class Error(val message: String) : DeviceStatus()
}
