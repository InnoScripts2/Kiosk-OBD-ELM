package com.selfservice.core.logging

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists diagnostics log entries to a JSONL file so they can be replayed or uploaded later.
 * Designed for kiosk environments where log shipping might be delayed during offline periods.
 */
class DiagnosticsLogFileSink(
        private val directory: File,
        private val fileName: String = DEFAULT_FILE_NAME,
        private val clock: () -> Long = { System.currentTimeMillis() }
) : DiagnosticsLogSink {

    private val directoryPrepared = AtomicBoolean(false)
    private val lock = Any()

    override fun persist(entry: DiagnosticsLogEntry) {
        val serialised = serialize(entry)
        synchronized(lock) {
            ensureDirectory()
            val file = File(directory, fileName)
            file.appendText(serialised + System.lineSeparator())
        }
    }

    override fun clearOlderThan(thresholdMillis: Long) {
        val file = File(directory, fileName)
        synchronized(lock) {
            if (!file.exists()) {
                return
            }
            val retained = buildList {
                file.forEachLine { line ->
                    if (line.isBlank()) {
                        return@forEachLine
                    }
                    val keep = runCatching {
                        val json = JSONObject(line)
                        json.optLong(KEY_TIMESTAMP, Long.MIN_VALUE) >= thresholdMillis
                    }.getOrElse { true }
                    if (keep) {
                        add(line)
                    }
                }
            }
            if (retained.isEmpty()) {
                file.delete()
            } else {
                val separator = System.lineSeparator()
                val content = retained.joinToString(separator = separator)
                file.writeText(content + separator)
            }
        }
    }

    fun snapshot(): List<DiagnosticsLogEntry> {
        val file = File(directory, fileName)
        if (!file.exists()) {
            return emptyList()
        }
        return synchronized(lock) {
            if (!file.exists()) {
                return@synchronized emptyList()
            }
            buildList {
                file.forEachLine { line ->
                    if (line.isBlank()) {
                        return@forEachLine
                    }
                    runCatching { JSONObject(line) }
                            .onSuccess { json -> serialize(json)?.let(::add) }
                }
            }
        }
    }

    private fun ensureDirectory() {
        if (directoryPrepared.compareAndSet(false, true)) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException(
                        String.format(Locale.US, "Unable to create diagnostics log directory %s", directory)
                )
            }
        }
    }

    private fun serialize(entry: DiagnosticsLogEntry): String {
        val json = JSONObject()
        json.put(KEY_CATEGORY, entry.category)
        json.put(KEY_MESSAGE, entry.message)
        json.put(KEY_TIMESTAMP, entry.timestampMillis)
        json.put(KEY_METADATA, toJsonValue(entry.metadata))
        return json.toString()
    }

    private fun serialize(json: JSONObject): DiagnosticsLogEntry? {
    val category = json.optString(KEY_CATEGORY).takeIf { it.isNotEmpty() } ?: return null
    val message = json.optString(KEY_MESSAGE).takeIf { it.isNotEmpty() } ?: return null
        val timestamp = json.optLong(KEY_TIMESTAMP, Long.MIN_VALUE)
        if (timestamp == Long.MIN_VALUE) {
            return null
        }
    val metadataValue = json.opt(KEY_METADATA)
    val metadata = (metadataValue as? JSONObject)?.let(::fromJsonObject) ?: emptyMap()
        return DiagnosticsLogEntry(
                category = category,
                message = message,
                timestampMillis = timestamp,
                metadata = metadata
        )
    }

    private fun toJsonValue(value: Any?): Any? =
            when (value) {
                null -> JSONObject.NULL
                is JSONObject, is JSONArray, is String, is Number, is Boolean -> value
                is Map<*, *> -> JSONObject().apply {
                    value.forEach { (key, element) ->
                        if (key != null) {
                            put(key.toString(), toJsonValue(element))
                        }
                    }
                }
                is Iterable<*> -> JSONArray().apply {
                    value.forEach { element -> put(toJsonValue(element)) }
                }
                is Array<*> -> JSONArray().apply {
                    value.forEach { element -> put(toJsonValue(element)) }
                }
                is Enum<*> -> value.name
                else -> value.toString()
            }

    private fun fromJsonObject(objectValue: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val keys = objectValue.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = fromJsonValue(objectValue.get(key))
        }
        return result
    }

    private fun fromJsonValue(value: Any?): Any? =
            when (value) {
                JSONObject.NULL -> null
                is JSONObject -> fromJsonObject(value)
                is JSONArray -> buildList {
                    for (index in 0 until value.length()) {
                        add(fromJsonValue(value.get(index)))
                    }
                }
                else -> value
            }

    companion object {
        const val DEFAULT_FILE_NAME = "diagnostics_logs.jsonl"
        private const val KEY_CATEGORY = "category"
        private const val KEY_MESSAGE = "message"
        private const val KEY_TIMESTAMP = "timestamp_ms"
        private const val KEY_METADATA = "metadata"
    }
}
