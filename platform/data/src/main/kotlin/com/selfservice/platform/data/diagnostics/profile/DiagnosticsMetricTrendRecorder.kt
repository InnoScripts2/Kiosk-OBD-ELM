package com.selfservice.platform.data.diagnostics.profile

import java.util.ArrayDeque

private const val DEFAULT_CAPACITY = 24

/**
 * Хранит последние значения метрик диагностики для построения временных рядов в UI.
 */
class DiagnosticsMetricTrendRecorder(
    private val capacity: Int = DEFAULT_CAPACITY
) {

    private val buffers: MutableMap<String, ArrayDeque<DiagnosticsMetricTrendPoint>> = LinkedHashMap()

    fun record(snapshot: DiagnosticsProfileSnapshot): Map<String, DiagnosticsMetricTrend> {
        snapshot.metrics.forEach { insight ->
            val value = insight.value ?: return@forEach
            val timestamp = insight.sourceTimestampMillis ?: snapshot.timestampMillis
            val metricId = insight.definition.id
            val buffer = buffers.getOrPut(metricId) { ArrayDeque(capacity) }
            if (buffer.size == capacity) {
                buffer.removeFirst()
            }
            buffer.addLast(DiagnosticsMetricTrendPoint(timestampMillis = timestamp, value = value))
        }
        return trends()
    }

    fun trends(): Map<String, DiagnosticsMetricTrend> {
        return buffers.entries.associate { (metricId, buffer) ->
            metricId to DiagnosticsMetricTrend(metricId, buffer.toList())
        }
    }

    fun reset() {
        buffers.clear()
    }
}

data class DiagnosticsMetricTrend(
    val metricId: String,
    val points: List<DiagnosticsMetricTrendPoint>
)

data class DiagnosticsMetricTrendPoint(
    val timestampMillis: Long,
    val value: Double
)
