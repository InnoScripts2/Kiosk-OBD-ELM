package com.selfservice.platform.data.diagnostics.profile

/**
 * Выполняет расчёт профиля метрик на основе снятых PID-семплов.
 */
class DiagnosticsMetricCalculator(
    private val definitions: List<DiagnosticsMetricDefinition>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun calculate(samples: Collection<DiagnosticsMetricSample>): DiagnosticsProfileSnapshot {
        val byKey = samples.associateBy { DiagnosticsMetricDefinition.normalizeKey(it.mode, it.pid) }
        val metrics = definitions.map { definition ->
            val sample = byKey[definition.normalizedKey]
            val value = sample?.value
            val unit = sample?.unit ?: definition.unit
            val status = definition.thresholds.evaluate(value)
            val advice = definition.advice.message(status)
            DiagnosticsMetricInsight(
                definition = definition,
                value = value,
                unit = unit,
                status = status,
                advice = advice,
                sourceTimestampMillis = sample?.timestampMillis
            )
        }
        val timestamp = samples.maxOfOrNull { it.timestampMillis } ?: clock()
        return DiagnosticsProfileSnapshot(timestampMillis = timestamp, metrics = metrics)
    }
}

data class DiagnosticsMetricInsight(
    val definition: DiagnosticsMetricDefinition,
    val value: Double?,
    val unit: String?,
    val status: DiagnosticsMetricStatus,
    val advice: String,
    val sourceTimestampMillis: Long?
)

data class DiagnosticsMetricSample(
    val mode: String,
    val pid: String,
    val value: Double?,
    val unit: String?,
    val timestampMillis: Long
)

data class DiagnosticsProfileSnapshot(
    val timestampMillis: Long,
    val metrics: List<DiagnosticsMetricInsight>
) {
    fun metricById(id: String): DiagnosticsMetricInsight? = metrics.firstOrNull { it.definition.id == id }
}
