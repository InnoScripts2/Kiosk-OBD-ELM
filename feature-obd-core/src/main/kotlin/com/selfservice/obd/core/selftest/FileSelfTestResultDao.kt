package com.selfservice.obd.core.selftest

import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists adapter self-test runs to a local JSONL file so telemetry can be shipped later via
 * background sync. Keeps format close to Supabase schema while avoiding a hard dependency on the
 * network stack inside the core module.
 */
class FileSelfTestResultDao(
        private val directory: File,
        private val fileName: String = DEFAULT_FILE_NAME,
        private val clock: () -> Long = { System.currentTimeMillis() }
) : SelfTestResultDao {

    private val ensureDirectoryOnce = AtomicReference(false)

    override suspend fun upsert(row: Map<String, Any?>) {
        ensureDirectory()
        val payload = JSONObject().apply {
            put("written_at_ms", clock())
            put("payload", toJsonValue(row))
        }
        appendLine(payload.toString())
    }

    private fun ensureDirectory() {
        if (ensureDirectoryOnce.compareAndSet(false, true)) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException(
                        String.format(Locale.US, "Unable to create directory %s", directory)
                )
            }
        }
    }

    private fun appendLine(line: String) {
        val file = File(directory, fileName)
        file.appendText(line + System.lineSeparator())
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

    companion object {
        private const val DEFAULT_FILE_NAME = "adapter_selftests.jsonl"
    }
}
