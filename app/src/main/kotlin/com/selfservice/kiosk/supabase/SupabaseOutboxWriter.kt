package com.selfservice.kiosk.supabase

import com.selfservice.obd.core.selftest.AdapterSelfTestSchema
import com.selfservice.obd.core.selftest.SelfTestResultDao
import com.selfservice.platform.logging.DiagnosticsLogOutboxWriter
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists pending Supabase operations to a JSONL file so the uploader can replay them when
 * connectivity becomes available. Each entry contains the target table, operation type and the
 * payload already shaped for Supabase REST.
 */
open class SupabaseOutboxWriter(
    private val directory: File,
    private val fileName: String = DEFAULT_FILE_NAME,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val onEnqueued: (() -> Unit)? = null
) : DiagnosticsLogOutboxWriter {

    private val ensureDirectoryOnce = AtomicBoolean(false)

    @Synchronized
    override fun enqueue(
        table: String,
        payload: Map<String, Any?>,
        operation: String
    ) {
        ensureDirectory()
        val envelope =
                JSONObject().apply {
                    put(KEY_WRITTEN_AT, clock())
                    put(KEY_TABLE, table)
                    put(KEY_OPERATION, operation)
                    put(KEY_PAYLOAD, toJsonValue(payload))
                }
        appendLine(envelope.toString())
        onEnqueued?.invoke()
    }

    fun enqueue(table: String, payload: Map<String, Any?>) {
        enqueue(table, payload, OPERATION_UPSERT)
    }

    private fun ensureDirectory() {
        if (ensureDirectoryOnce.compareAndSet(false, true)) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException(
                        String.format(Locale.US, "Unable to create outbox directory %s", directory)
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
    const val DEFAULT_FILE_NAME = "supabase_outbox.jsonl"
        private const val KEY_WRITTEN_AT = "written_at_ms"
        private const val KEY_TABLE = "table"
        private const val KEY_OPERATION = "operation"
        private const val KEY_PAYLOAD = "payload"
        const val OPERATION_UPSERT = "upsert"
    }
}

/**
 * [SelfTestResultDao] implementation that writes adapter self-test rows into the Supabase outbox.
 */
class SupabaseOutboxSelfTestResultDao(
        private val writer: SupabaseOutboxWriter,
        private val table: String = AdapterSelfTestSchema.TABLE_NAME
) : SelfTestResultDao {

    override suspend fun upsert(row: Map<String, Any?>) {
        writer.enqueue(table = table, payload = row, operation = SupabaseOutboxWriter.OPERATION_UPSERT)
    }
}
