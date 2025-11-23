package com.selfservice.feature.reports

import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationPriority
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationSeverity
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThreshold
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThresholdType
import java.time.ZoneId
import java.util.Locale

internal fun createReportMapper(): DiagnosticsReportViewModelMapper {
    return DiagnosticsReportViewModelMapper(
        locale = Locale("ru", "RU"),
        zoneId = ZoneId.of("Europe/Moscow")
    )
}

internal fun sampleReportInput(): DiagnosticsReportInput {
    val snapshot = DiagnosticsProfileSnapshot(
        timestampMillis = 1_700_000_000_000,
        metrics = listOf(
            insight(
                id = "coolant_temp",
                label = "Температура охлаждающей жидкости",
                value = 112.4,
                unit = "°C",
                status = DiagnosticsMetricStatus.CRITICAL_HIGH,
                advice = "Снизьте нагрузку двигателя."
            ),
            insight(
                id = "rpm",
                label = "<RPM>",
                value = 800.0,
                unit = "об/мин",
                status = DiagnosticsMetricStatus.OK,
                advice = "Работа в норме\nбез отклонений."
            ),
            insight(
                id = "fuel_trim",
                label = "Топливная коррекция",
                value = null,
                unit = "%",
                status = DiagnosticsMetricStatus.NO_DATA,
                advice = "Данные не получены"
            )
        )
    )
    val coolantPidKey = DiagnosticsMetricDefinition.normalizeKey("01", "cool")
    val recommendations = listOf(
        DiagnosticsRecommendation(
            metricId = "coolant_temp",
            pidKey = coolantPidKey,
            title = "Температура охлаждающей жидкости",
            message = "Остановите автомобиль и проверьте уровень охлаждающей жидкости.",
            severity = DiagnosticsRecommendationSeverity.CRITICAL,
            status = DiagnosticsMetricStatus.CRITICAL_HIGH,
            priority = DiagnosticsRecommendationPriority.HIGH,
            threshold = DiagnosticsRecommendationThreshold(
                type = DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL,
                value = 110.0,
                unit = "°C"
            )
        )
    )
    return DiagnosticsReportInput(
        sessionId = "session-001",
        generatedAtMillis = 1_700_000_050_000,
        snapshot = snapshot,
        recommendations = recommendations,
        vehicle = DiagnosticsReportVehicle(
            make = "Toyota",
            model = "Camry",
            year = 2020,
            vin = "JT1234567890VIN"
        ),
        customer = DiagnosticsReportCustomer(
            phone = "+7 (900) 123-45-67",
            email = "owner@example.com"
        )
    )
}

private fun insight(
    id: String,
    label: String,
    value: Double?,
    unit: String?,
    status: DiagnosticsMetricStatus,
    advice: String
): DiagnosticsMetricInsight {
    val thresholds = DiagnosticsMetricThresholds(
        warningHigh = 100.0,
        criticalHigh = 110.0,
        warningLow = 10.0,
        criticalLow = 5.0
    )
    val adviceMessages = DiagnosticsMetricAdvice(
        messages = mapOf(
            DiagnosticsMetricStatus.CRITICAL_HIGH to "Снизьте нагрузку двигателя.",
            DiagnosticsMetricStatus.OK to "Работа в норме"
        ),
        defaultMessage = "Проверьте показатель",
        noDataMessage = "Данные не получены"
    )
    val definition = DiagnosticsMetricDefinition(
        id = id,
        mode = "01",
        pid = id.take(4),
        label = label,
        unit = unit,
        thresholds = thresholds,
        advice = adviceMessages
    )
    return DiagnosticsMetricInsight(
        definition = definition,
        value = value,
        unit = unit,
        status = status,
        advice = advice,
        sourceTimestampMillis = 1_700_000_000_000
    )
}
