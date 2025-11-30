package com.selfservice.obd.elm.port.adapter

import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.elm.port.models.ElmDtcEntry

/**
 * Converts donor DTC entries into canonical [ObdDtcDefinition] objects for parity checks.
 */
object ElmDtcDefinitionAdapter {
    private val systemMap = mapOf(
        'P' to ObdDtcDefinition.System.POWERTRAIN,
        'B' to ObdDtcDefinition.System.BODY,
        'C' to ObdDtcDefinition.System.CHASSIS,
        'U' to ObdDtcDefinition.System.NETWORK
    )

    fun asDefinition(entry: ElmDtcEntry): ObdDtcDefinition {
        val normalizedCode = normalizeCode(entry.code)
        return ObdDtcDefinition(
            code = normalizedCode,
            system = systemFor(normalizedCode),
            label = entry.description.trim().ifBlank { "${normalizedCode} description missing" }
        )
    }

    private fun systemFor(code: String): ObdDtcDefinition.System {
        val key = code.firstOrNull()
            ?: error("Empty code encountered for donor dtc entry")
        return systemMap[key]
            ?: error("Unsupported DTC prefix '$key' for code $code")
    }

    private fun normalizeCode(raw: String): String {
        val trimmed = raw.trim().uppercase()
        val withoutPrefix = if (trimmed.startsWith("0X")) trimmed.substring(2) else trimmed
        require(withoutPrefix.length == 5) { "Unexpected DTC code length for '$raw'" }
        return withoutPrefix
    }
}