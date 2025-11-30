package com.selfservice.kiosk.dictionary

import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.pid.ObdPidDefinition
import org.json.JSONArray
import org.json.JSONObject

/** Parses raw JSON payloads into strongly typed dictionary updates. */
class DictionaryUpdatePayloadParser {

    fun parse(
            root: JSONObject,
            fallbackSource: String,
            fallbackUpdatedAtMillis: Long
    ): DictionaryUpdatePayload? {
        val pidBlock = root.optJSONObject(KEY_PID)
        val pidVersion = pidBlock?.optStringOrNull(KEY_VERSION)
        val pidDefinitions = pidBlock?.optJSONArray(KEY_DEFINITIONS)?.let(::parsePidDefinitions) ?: emptyList()

        val dtcBlock = root.optJSONObject(KEY_DTC)
        val dtcVersion = dtcBlock?.optStringOrNull(KEY_VERSION)
        val dtcDefinitions = dtcBlock?.optJSONArray(KEY_DEFINITIONS)?.let(::parseDtcDefinitions) ?: emptyList()

        if (pidDefinitions.isEmpty() && dtcDefinitions.isEmpty()) {
            return null
        }

        val source = root.optStringOrNull(KEY_SOURCE) ?: fallbackSource
        val updatedAt = root.optLong(KEY_UPDATED_AT_MS, fallbackUpdatedAtMillis)

        return DictionaryUpdatePayload(
                pidDefinitions = pidDefinitions,
                dtcDefinitions = dtcDefinitions,
                pidVersion = pidVersion,
                dtcVersion = dtcVersion,
                source = source,
                updatedAtMillis = updatedAt
        )
    }

    private fun parsePidDefinitions(array: JSONArray): List<ObdPidDefinition> {
        val definitions = mutableListOf<ObdPidDefinition>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val mode = item.optStringOrNull(KEY_MODE)
            val pid = item.optStringOrNull(KEY_PID_VALUE)
            val label = item.optStringOrNull(KEY_LABEL)
            if (mode == null || pid == null || label == null) {
                continue
            }
            definitions +=
                    ObdPidDefinition(
                            mode = mode,
                            pid = pid,
                            label = label,
                            min = item.optDoubleOrNull(KEY_MIN),
                            max = item.optDoubleOrNull(KEY_MAX),
                            unit = item.optStringOrNull(KEY_UNIT),
                            conversion = item.optStringOrNull(KEY_CONVERSION),
                            formula = item.optStringOrNull(KEY_FORMULA),
                            pollIntervalMs = item.optLongOrNull(KEY_POLL_INTERVAL),
                            notes = item.optStringOrNull(KEY_NOTES)
                    )
        }
        return definitions
    }

    private fun parseDtcDefinitions(array: JSONArray): List<ObdDtcDefinition> {
        val definitions = mutableListOf<ObdDtcDefinition>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val code = item.optStringOrNull(KEY_CODE)
            val label = item.optStringOrNull(KEY_LABEL)
            val system = item.optStringOrNull(KEY_SYSTEM)
            if (code == null || label == null || system == null) {
                continue
            }
            val systemEnum = try {
                ObdDtcDefinition.System.fromValue(system)
            } catch (_: IllegalArgumentException) {
                continue
            } catch (_: IllegalStateException) {
                continue
            }
            definitions +=
                    ObdDtcDefinition(
                            code = code,
                            system = systemEnum,
                            label = label,
                            notes = item.optStringOrNull(KEY_NOTES)
                    )
        }
        return definitions
    }

    companion object {
        private const val KEY_SOURCE = "source"
        private const val KEY_UPDATED_AT_MS = "updated_at_ms"
        private const val KEY_PID = "pid"
        private const val KEY_DTC = "dtc"
        private const val KEY_VERSION = "version"
        private const val KEY_DEFINITIONS = "definitions"
        private const val KEY_MODE = "mode"
        private const val KEY_PID_VALUE = "pid"
        private const val KEY_LABEL = "label"
        private const val KEY_MIN = "min"
        private const val KEY_MAX = "max"
        private const val KEY_UNIT = "unit"
        private const val KEY_CONVERSION = "conversion"
        private const val KEY_FORMULA = "formula"
        private const val KEY_POLL_INTERVAL = "poll_interval_ms"
        private const val KEY_NOTES = "notes"
        private const val KEY_CODE = "code"
        private const val KEY_SYSTEM = "system"
    }
}

private fun JSONObject.optStringOrNull(name: String): String? {
    val value = optString(name, null)
    return value?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null
