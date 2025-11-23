package com.selfservice.feature.payments

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists audit events to an NDJSON file for subsequent ingestion (e.g. Supabase audit tables).
 */
class FilePaymentAuditSink(
    private val file: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val json: Json = DEFAULT_JSON,
    private val errorReporter: ((Throwable) -> Unit)? = null,
) : PaymentAuditSink {

    private val lock = ReentrantLock()

    override fun record(event: PaymentAuditEvent) {
        val line = runCatching { json.encodeToString(event) + "\n" }
            .getOrElse { error ->
                errorReporter?.invoke(error)
                return
            }
        lock.withLock {
            try {
                ensureFile()
                file.appendText(line)
                enforceLimit()
            } catch (error: Throwable) {
                errorReporter?.invoke(error) ?: System.err.println("[FilePaymentAuditSink] $error")
            }
        }
    }

    private fun ensureFile() {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (!file.exists()) {
            file.createNewFile()
        }
    }

    private fun enforceLimit() {
        if (maxBytes <= 0) {
            return
        }
        val length = file.length()
        if (length <= maxBytes) {
            return
        }
        val keep = maxBytes
            .coerceAtMost(length)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        if (keep <= 0) {
            file.writeText("")
            return
        }
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(length - keep)
            val buffer = ByteArray(keep)
            raf.readFully(buffer)
            raf.setLength(0)
            raf.seek(0)
            raf.write(buffer)
        }
    }

    companion object {
        private const val DEFAULT_MAX_BYTES: Long = 512 * 1024 // 512 KiB
        private val DEFAULT_JSON = Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }
    }
}
