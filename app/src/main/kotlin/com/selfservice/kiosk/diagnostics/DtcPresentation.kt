package com.selfservice.kiosk.diagnostics

import com.selfservice.obd.core.dtc.ObdDtcDefinition
import java.util.LinkedHashMap
import java.util.Locale

internal fun preferredDescription(definition: ObdDtcDefinition?): String? {
    return definition?.details?.preferred?.takeIf { it.isNotBlank() }
        ?: definition?.label?.takeIf { it.isNotBlank() }
}

internal fun descriptionSource(definition: ObdDtcDefinition?): String {
    return when {
        definition?.details?.preferred?.isNotBlank() == true -> "preferred"
        definition?.label?.isNotBlank() == true -> "label"
        else -> "missing"
    }
}

internal fun inferDtcSeverity(code: String, definition: ObdDtcDefinition?): String {
    val normalized = code.trim().uppercase(Locale.ROOT)
    val first = normalized.firstOrNull()
    val second = normalized.getOrNull(1)
    val thirdDigit = normalized.getOrNull(2)?.digitToIntOrNull()
    return when (first) {
        'P' -> {
            if (second == '0' || second == '1') {
                if (thirdDigit != null && thirdDigit <= 3) "critical" else "warning"
            } else {
                "warning"
            }
        }
        'B', 'C' -> "warning"
        'U' -> "info"
        else -> when (definition?.system) {
            ObdDtcDefinition.System.POWERTRAIN,
            ObdDtcDefinition.System.CHASSIS -> "warning"
            else -> "info"
        }
    }
}

internal fun ObdDtcDefinition.Details.toMetadataMap(): Map<String, String> {
    val map = LinkedHashMap<String, String>(3)
    preferred?.takeIf { it.isNotBlank() }?.let { map["preferred"] = it }
    base?.takeIf { it.isNotBlank() }?.let { map["base"] = it }
    app?.takeIf { it.isNotBlank() }?.let { map["app"] = it }
    return map
}
