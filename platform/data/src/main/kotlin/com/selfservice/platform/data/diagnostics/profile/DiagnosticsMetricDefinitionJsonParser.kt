package com.selfservice.platform.data.diagnostics.profile

import java.util.EnumMap
import org.json.JSONObject

/**
 * Парсер JSON-описания расчётных метрик диагностики.
 */
internal object DiagnosticsMetricDefinitionJsonParser {

    fun parse(json: String): List<DiagnosticsMetricDefinition> {
        if (json.isBlank()) {
            return emptyList()
        }
        return runCatching { parseInternal(JSONObject(json)) }
            .getOrElse { emptyList() }
    }

    private fun parseInternal(root: JSONObject): List<DiagnosticsMetricDefinition> {
        val metricsArray = root.optJSONArray("metrics") ?: return emptyList()
        val result = mutableListOf<DiagnosticsMetricDefinition>()
        for (index in 0 until metricsArray.length()) {
            val item = metricsArray.optJSONObject(index) ?: continue
            parseDefinition(item)?.let(result::add)
        }
        return result
    }

    private fun parseDefinition(item: JSONObject): DiagnosticsMetricDefinition? {
        val id = item.optString("id").trim()
        val mode = item.optString("mode").trim()
        val pid = item.optString("pid").trim()
        val label = item.optString("label").trim()
        if (id.isEmpty() || mode.isEmpty() || pid.isEmpty() || label.isEmpty()) {
            return null
        }
        val unit = item.optString("unit").trim().ifEmpty { null }
        val thresholds = parseThresholds(item.optJSONObject("thresholds"))
        val advice = parseAdvice(item.optJSONObject("advice"))
        val category = DiagnosticsMetricCategory.fromSlug(item.optString("category"))
        return DiagnosticsMetricDefinition(
            id = id,
            mode = mode,
            pid = pid,
            label = label,
            unit = unit,
            thresholds = thresholds,
            advice = advice,
            category = category
        )
    }

    private fun parseThresholds(value: JSONObject?): DiagnosticsMetricThresholds {
        if (value == null) {
            return DiagnosticsMetricThresholds()
        }
        return DiagnosticsMetricThresholds(
            warningLow = value.optDoubleOrNull("warningLow"),
            criticalLow = value.optDoubleOrNull("criticalLow"),
            warningHigh = value.optDoubleOrNull("warningHigh"),
            criticalHigh = value.optDoubleOrNull("criticalHigh")
        )
    }

    private fun parseAdvice(value: JSONObject?): DiagnosticsMetricAdvice {
        if (value == null) {
            return DiagnosticsMetricAdvice(emptyMap(), defaultMessage = "Нет рекомендаций")
        }
        val messagesObject = value.optJSONObject("messages")
        val messages = EnumMap<DiagnosticsMetricStatus, String>(DiagnosticsMetricStatus::class.java)
        messagesObject?.let { mapMessages(it, messages) }
        val defaultMessage = value.optString("default").takeIf { it.isNotBlank() }
            ?: "Нет рекомендаций"
        val noDataMessage = value.optString("noData").takeIf { it.isNotBlank() }
            ?: "Нет данных"
        return DiagnosticsMetricAdvice(messages, defaultMessage, noDataMessage)
    }

    private fun mapMessages(source: JSONObject, target: EnumMap<DiagnosticsMetricStatus, String>) {
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val message = source.optString(key).takeIf { it.isNotBlank() } ?: continue
            val status = runCatching { DiagnosticsMetricStatus.valueOf(key.uppercase()) }.getOrNull()
                ?: continue
            target[status] = message
        }
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) {
            return null
        }
        return runCatching { getDouble(name) }.getOrNull()
    }
}
