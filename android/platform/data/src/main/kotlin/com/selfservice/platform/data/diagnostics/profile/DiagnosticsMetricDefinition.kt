package com.selfservice.platform.data.diagnostics.profile

import java.util.Locale

/**
 * Описание вычисляемой метрики диагностики с порогами и рекомендациями.
 */
data class DiagnosticsMetricDefinition(
    val id: String,
    val mode: String,
    val pid: String,
    val label: String,
    val unit: String?,
    val thresholds: DiagnosticsMetricThresholds,
    val advice: DiagnosticsMetricAdvice,
    val category: DiagnosticsMetricCategory = DiagnosticsMetricCategory.GENERAL
) {
    val normalizedKey: String = normalizeKey(mode, pid)

    companion object {
        fun normalizeKey(mode: String, pid: String): String {
            fun normalizeToken(raw: String): String {
                val trimmed = raw.trim()
                val withoutPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) {
                    trimmed.substring(2)
                } else {
                    trimmed
                }
                return withoutPrefix.uppercase(Locale.US).padStart(2, '0')
            }
            val normalizedMode = normalizeToken(mode)
            val normalizedPid = normalizeToken(pid)
            return "$normalizedMode:$normalizedPid"
        }
    }
}

enum class DiagnosticsMetricCategory {
    ELECTRICAL,
    THERMAL,
    FUEL_SYSTEM,
    AIRFLOW,
    PRESSURE,
    GENERAL;

    companion object {
        fun fromSlug(raw: String?): DiagnosticsMetricCategory {
            val slug = raw?.trim()?.lowercase() ?: return GENERAL
            return when (slug) {
                "electrical" -> ELECTRICAL
                "thermal" -> THERMAL
                "fuel", "fuel_system" -> FUEL_SYSTEM
                "airflow", "intake" -> AIRFLOW
                "pressure" -> PRESSURE
                else -> GENERAL
            }
        }
    }
}

data class DiagnosticsMetricThresholds(
    val warningLow: Double? = null,
    val criticalLow: Double? = null,
    val warningHigh: Double? = null,
    val criticalHigh: Double? = null
) {
    fun evaluate(value: Double?): DiagnosticsMetricStatus {
        if (value == null) {
            return DiagnosticsMetricStatus.NO_DATA
        }
        criticalLow?.let { if (value <= it) return DiagnosticsMetricStatus.CRITICAL_LOW }
        warningLow?.let { if (value <= it) return DiagnosticsMetricStatus.WARNING_LOW }
        criticalHigh?.let { if (value >= it) return DiagnosticsMetricStatus.CRITICAL_HIGH }
        warningHigh?.let { if (value >= it) return DiagnosticsMetricStatus.WARNING_HIGH }
        return DiagnosticsMetricStatus.OK
    }
}

data class DiagnosticsMetricAdvice(
    private val messages: Map<DiagnosticsMetricStatus, String>,
    private val defaultMessage: String,
    private val noDataMessage: String = "Нет данных"
) {
    fun message(status: DiagnosticsMetricStatus): String = when (status) {
        DiagnosticsMetricStatus.NO_DATA -> noDataMessage
        else -> messages[status] ?: defaultMessage
    }
}

enum class DiagnosticsMetricStatus {
    NO_DATA,
    CRITICAL_LOW,
    WARNING_LOW,
    OK,
    WARNING_HIGH,
    CRITICAL_HIGH
}
