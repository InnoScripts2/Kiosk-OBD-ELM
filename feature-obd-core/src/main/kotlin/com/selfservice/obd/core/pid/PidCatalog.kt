package com.selfservice.obd.core.pid

import java.util.Locale
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

/**
 * Provides access to the canonical list of OBD-II PID definitions backed by the JSON catalog
 * migrated from the legacy TypeScript implementation.
 */
object PidCatalog {
    private val V2_RESOURCE_PATHS = listOf(
        "/com/selfservice/obd/core/pids/pids-mode1-v2.json",
        "/com/selfservice/obd/core/pids/pids-mode4-v2.json",
        "/com/selfservice/obd/core/pids/pids-mode9-v2.json"
    )
    private const val LEGACY_RESOURCE_PATH = "/com/selfservice/obd/core/pids/pids.json"

    private val definitions: List<ObdPidDefinition> by lazy { loadDefinitions() }

    fun definitions(): List<ObdPidDefinition> = definitions.map { it.copy() }

    fun find(mode: String, pid: String): ObdPidDefinition? {
        val normalizedMode = normalizeHex(mode)
        val normalizedPid = normalizeHex(pid)
        return definitions.firstOrNull { it.mode == normalizedMode && it.pid == normalizedPid }?.copy()
    }

    fun listModes(): List<String> = definitions
        .map { it.mode }
        .distinct()
        .sortedBy { toModeNumber(it) }

    fun listPidsByMode(mode: String): List<ObdPidDefinition> {
        val normalizedMode = normalizeHex(mode)
        return definitions
            .asSequence()
            .filter { it.mode == normalizedMode }
            .sortedBy { toPidNumber(it.pid) }
            .map { it.copy() }
            .toList()
    }

    private fun loadDefinitions(): List<ObdPidDefinition> {
        val modern = V2_RESOURCE_PATHS
            .flatMap { loadDefinitionsFromResource(it, required = false) }

        val source = if (modern.isNotEmpty()) {
            modern
        } else {
            loadDefinitionsFromResource(LEGACY_RESOURCE_PATH, required = true)
        }

        return source.sortedWith(compareBy({ toModeNumber(it.mode) }, { toPidNumber(it.pid) }))
    }

    private fun loadDefinitionsFromResource(resourcePath: String, required: Boolean): List<ObdPidDefinition> {
        val stream = PidCatalog::class.java.getResourceAsStream(resourcePath)
        if (stream == null) {
            if (required) {
                error("Resource $resourcePath is missing from classpath")
            }
            return emptyList()
        }
        val json = stream.bufferedReader().use { it.readText() }
        return parse(JSONArray(json))
    }

    private fun parse(array: JSONArray): List<ObdPidDefinition> = buildList(array.length()) {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            add(item.toDefinition())
        }
    }

    private fun JSONObject.toDefinition(): ObdPidDefinition {
        val range = optJSONObject("range")
        return ObdPidDefinition(
            mode = normalizeHexValue(opt("mode"), minDigits = 2),
            pid = normalizeHexValue(opt("pid"), minDigits = 2),
            label = optStringOrNull("label") ?: optStringOrNull("description")
                ?: error("PID entry is missing label/description: $this"),
            min = range?.optDoubleOrNull("min") ?: optDoubleOrNull("min"),
            max = range?.optDoubleOrNull("max") ?: optDoubleOrNull("max"),
        unit = optStringOrNull("unit"),
        conversion = optStringOrNull("conversion") ?: optStringOrNull("scaling"),
        formula = optStringOrNull("formula"),
        pollIntervalMs = optLongOrNull("pollIntervalMs"),
        notes = optStringOrNull("notes"),
        payloadLengthBytes = optIntOrNull("payloadLengthBytes") ?: optIntOrNull("bytes"),
        status = optStringOrNull("status"),
        sources = optStringList("sources")
        )
    }

    private fun normalizeHexValue(raw: Any?, minDigits: Int = 2): String {
        requireNotNull(raw) { "PID entry must include hex value" }
        return when (raw) {
            is Number -> formatHexFromNumber(raw, minDigits)
            is String -> normalizeHex(raw, minDigits)
            else -> normalizeHex(raw.toString(), minDigits)
        }
    }

    private fun formatHexFromNumber(value: Number, minDigits: Int): String {
        val asLong = value.toLong()
        val hex = asLong.toString(16).uppercase(Locale.US)
        val evenLength = if (hex.length % 2 == 0) hex.length else hex.length + 1
        val targetLength = max(minDigits, evenLength)
        val padded = hex.padStart(targetLength, '0')
        return "0x$padded"
    }

    private fun normalizeHex(value: String, minDigits: Int = 2): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty()) { "Hex value cannot be blank" }
        val withoutPrefix = if (trimmed.startsWith("0x", ignoreCase = true)) {
            trimmed.substring(2)
        } else {
            trimmed
        }
        var normalized = withoutPrefix.uppercase(Locale.US)
        if (normalized.isEmpty()) normalized = "0"
        val evenLength = if (normalized.length % 2 == 0) normalized.length else normalized.length + 1
        val targetLength = max(minDigits, evenLength)
        val padded = normalized.padStart(targetLength, '0')
        return "0x$padded"
    }

    private fun toPidNumber(pid: String): Int = pid.substring(2).toInt(radix = 16)

    private fun toModeNumber(mode: String): Int = mode.substring(2).toInt(radix = 16)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.optStringList(key: String): List<String> {
        if (!has(key) || isNull(key)) return emptyList()
        val array = getJSONArray(key)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }
}
