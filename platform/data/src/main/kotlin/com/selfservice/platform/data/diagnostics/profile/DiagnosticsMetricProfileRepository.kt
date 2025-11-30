package com.selfservice.platform.data.diagnostics.profile

/**
 * Репозиторий профиля метрик диагностики. Хранит каталог определений и выполняет расчёт срезов.
 */
class DiagnosticsMetricProfileRepository(
    private val definitions: List<DiagnosticsMetricDefinition>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val calculator = DiagnosticsMetricCalculator(definitions, clock)

    fun definitions(): List<DiagnosticsMetricDefinition> = definitions

    fun evaluate(samples: Collection<DiagnosticsMetricSample>): DiagnosticsProfileSnapshot {
        return calculator.calculate(samples)
    }
}
