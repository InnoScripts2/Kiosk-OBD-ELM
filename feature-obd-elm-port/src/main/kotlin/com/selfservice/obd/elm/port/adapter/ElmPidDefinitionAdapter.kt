package com.selfservice.obd.elm.port.adapter

import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.elm.port.models.PID

/**
 * Converts PID entries from the legacy AndroidOBD dataset into the canonical [ObdPidDefinition]
 * used by `feature-obd-core`. Intended for parity tests and future migration tools.
 */
object ElmPidDefinitionAdapter {
    private val UNIT_MAPPING = mapOf(
        "%" to "percent",
        "% LOAD" to "percent",
        "°C" to "degC",
        "°F" to "degF",
        "KPA (ABSOLUTE)" to "kPa",
        "KPA (GAUGE)" to "kPa_gauge",
        "KPA" to "kPa",
        "PSI (GAUGE)" to "psi_gauge",
        "PSI (ABSOLUTE)" to "psi_absolute",
        "PSI" to "psi",
        "KM/H" to "km_per_h",
        "KM" to "km",
        "RPM" to "rpm",
        "VOLTS" to "volt",
        "V" to "volt",
        "MAF" to "grams_per_sec",
        "°" to "deg",
        "SECONDS" to "seconds"
    )

    fun asObdDefinition(pid: PID): ObdPidDefinition {
        val normalizedMode = normalizeHex(pid.mode)
        val normalizedPid = normalizeHex(pid.PID)
        return ObdPidDefinition(
            mode = normalizedMode,
            pid = normalizedPid,
            label = pid.description.ifBlank { "PID ${normalizedPid}" },
            min = pid.min.toDoubleOrNullSafe(),
            max = pid.max.toDoubleOrNullSafe(),
            unit = normalizeUnit(pid.units),
            formula = pid.formula?.trim().orEmpty().ifBlank { null },
            notes = buildNotes(pid),
            payloadLengthBytes = pid.payloadLengthBytes()
        )
    }

    private fun buildNotes(pid: PID): String? {
        val extras = mutableListOf<String>()
        if (pid.bytes.isNotBlank()) extras += "Bytes:${pid.bytes.trim()}"
        if (!pid.imperialUnits.isNullOrBlank()) extras += "ImperialUnits:${pid.imperialUnits!!.trim()}"
        if (!pid.imperialFormula.isNullOrBlank()) extras += "ImperialFormula:${pid.imperialFormula!!.trim()}"
        return extras.takeIf { it.isNotEmpty() }?.joinToString(separator = "; ")
    }

    private fun String?.toDoubleOrNullSafe(): Double? {
        val cleaned = this?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        return cleaned.toDoubleOrNull()
    }

    private fun normalizeUnit(rawUnit: String?): String? {
        val candidate = rawUnit?.trim().orEmpty()
        if (candidate.isEmpty()) return null
        val normalized = candidate.uppercase()
        UNIT_MAPPING[normalized]?.let { return it }
        return candidate
    }

    private fun normalizeHex(value: String): String {
        val trimmed = value.trim()
        val withoutPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2)
        } else {
            trimmed
        }
        val normalized = withoutPrefix.uppercase()
        val padded = normalized.padStart(2, '0')
        return "0x$padded"
    }

    private fun PID.payloadLengthBytes(): Int? {
        val rawBytes = bytes.trim()
        if (rawBytes.isEmpty()) return null
        return rawBytes.toIntOrNull()
    }
}
