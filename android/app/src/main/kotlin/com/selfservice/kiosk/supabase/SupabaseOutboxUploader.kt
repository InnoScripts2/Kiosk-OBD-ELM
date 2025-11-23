package com.selfservice.kiosk.supabase

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject

/**
 * Replays queued Supabase operations stored by [SupabaseOutboxWriter]. Keeps delivery
 * idempotent by preserving the JSONL file until remote persistence succeeds.
 */
class SupabaseOutboxUploader(
        private val directory: File,
        private val client: Client,
        private val fileName: String = SupabaseOutboxWriter.DEFAULT_FILE_NAME,
        private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val mutex = Mutex()

    suspend fun flush(maxEntries: Int = Int.MAX_VALUE): FlushResult =
        withContext(dispatcher) { mutex.withLock { flushLocked(maxEntries) } }

    private suspend fun flushLocked(maxEntries: Int): FlushResult {
        if (maxEntries <= 0) {
            val remaining = existingLineCount()
            return FlushResult(processed = 0, remaining = remaining, discarded = 0)
        }

        ensureDirectory()
        val file = File(directory, fileName)
        if (!file.exists()) {
            return FlushResult(processed = 0, remaining = 0, discarded = 0)
        }

        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            file.delete()
            return FlushResult(processed = 0, remaining = 0, discarded = 0)
        }

        var processed = 0
        var discarded = 0
        val remainder = ArrayList<String>()
        var failure: Throwable? = null
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            val operation = parseOperation(line)
            if (operation == null) {
                discarded += 1
                index += 1
                continue
            }

            if (processed >= maxEntries) {
                remainder.addAll(lines.subList(index, lines.size))
                break
            }

            try {
                client.execute(operation)
                processed += 1
            } catch (error: Throwable) {
                failure = error
                remainder.addAll(lines.subList(index, lines.size))
                break
            }

            index += 1
        }

        writeRemainder(file, remainder)
        val remaining = remainder.count { parseOperation(it) != null }
        return FlushResult(processed = processed, remaining = remaining, discarded = discarded, failure = failure)
    }

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create Supabase outbox directory: $directory")
        }
    }

    private fun existingLineCount(): Int {
        val file = File(directory, fileName)
        if (!file.exists()) {
            return 0
        }
        return file.readLines().count { it.isNotBlank() }
    }

    private fun writeRemainder(file: File, remainder: List<String>) {
        if (remainder.isEmpty()) {
            if (file.exists()) {
                file.delete()
            }
            return
        }
        val separator = System.lineSeparator()
        val content = buildString {
            remainder.forEachIndexed { index, line ->
                append(line)
                if (index < remainder.lastIndex) {
                    append(separator)
                }
            }
        }
        file.writeText(content + separator)
    }

    private fun parseOperation(line: String): Operation? {
        return try {
            val json = JSONObject(line)
            val table = json.optString(KEY_TABLE, null) ?: return null
            val operation = json.optString(KEY_OPERATION, DEFAULT_OPERATION)
            val payloadObject = json.optJSONObject(KEY_PAYLOAD) ?: return null
            val writtenAt = json.optLong(KEY_WRITTEN_AT, 0L)
            Operation(
                    table = table,
                    operation = operation,
                    payload = payloadObject,
                    writtenAtMillis = writtenAt
            )
        } catch (_: JSONException) {
            null
        }
    }

    interface Client {
        suspend fun execute(operation: Operation)
    }

    data class Operation(
            val table: String,
            val operation: String,
            val payload: JSONObject,
            val writtenAtMillis: Long
    )

    data class FlushResult(
            val processed: Int,
            val remaining: Int,
            val discarded: Int,
            val failure: Throwable? = null
    ) {
        val isSuccess: Boolean
            get() = failure == null
    }

    companion object {
        private const val KEY_WRITTEN_AT = "written_at_ms"
        private const val KEY_TABLE = "table"
        private const val KEY_OPERATION = "operation"
        private const val KEY_PAYLOAD = "payload"
        private val DEFAULT_OPERATION = SupabaseOutboxWriter.OPERATION_UPSERT
    }
}
