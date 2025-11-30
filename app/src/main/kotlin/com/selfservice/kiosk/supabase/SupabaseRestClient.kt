package com.selfservice.kiosk.supabase

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Minimal Supabase REST client for posting self-test results from the kiosk. Uses raw
 * [HttpURLConnection] to avoid introducing additional HTTP dependencies.
 */
class SupabaseRestClient(
        private val credentials: SupabaseCredentials,
        private val connectionFactory: (URL) -> HttpURLConnection = { url ->
            (url.openConnection() as HttpURLConnection)
        },
        private val logger: (String) -> Unit = { message ->
            Log.d(TAG, message)
        }
) : SupabaseOutboxUploader.Client {

    override suspend fun execute(operation: SupabaseOutboxUploader.Operation) {
        val endpoint = credentials.tableEndpoint(operation.table)
        val url = URL(endpoint)
        val connection = connectionFactory(url)
        try {
            configureConnection(connection, operation)
            writePayload(connection, operation)
            val code = connection.responseCode
            if (code !in HTTP_SUCCESS_RANGE) {
                val errorBody = readStream(connection.errorStream) ?: readStream(connection.inputStream)
                throw IOException("Supabase responded with ${code}: ${errorBody ?: "<empty>"}")
            }
            logger("Supabase request succeeded for table=${operation.table}, processed payload")
        } finally {
            connection.disconnect()
        }
    }

    private fun configureConnection(
            connection: HttpURLConnection,
            operation: SupabaseOutboxUploader.Operation
    ) {
        connection.requestMethod = when (operation.operation.lowercase(Locale.US)) {
            SupabaseOutboxWriter.OPERATION_UPSERT -> "POST"
            else -> "POST"
        }
        connection.doInput = true
        connection.doOutput = true
        connection.useCaches = false
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("apikey", credentials.serviceKey)
        connection.setRequestProperty("Authorization", "Bearer ${credentials.serviceKey}")
        val preferHeader = buildPreferHeader(operation)
        if (preferHeader != null) {
            connection.setRequestProperty("Prefer", preferHeader)
        }
    }

    private fun writePayload(connection: HttpURLConnection, operation: SupabaseOutboxUploader.Operation) {
        val bytes = operation.payload.toString().toByteArray(StandardCharsets.UTF_8)
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { stream: OutputStream ->
            stream.write(bytes)
            stream.flush()
        }
    }

    private fun readStream(stream: InputStream?): String? {
        if (stream == null) return null
        return stream.use { input ->
            input.bufferedReader(StandardCharsets.UTF_8).readText().ifBlank { null }
        }
    }

    private fun buildPreferHeader(operation: SupabaseOutboxUploader.Operation): String? {
        return when (operation.operation.lowercase(Locale.US)) {
            SupabaseOutboxWriter.OPERATION_UPSERT -> "resolution=merge-duplicates,return=minimal"
            else -> null
        }
    }

    companion object {
        private const val TAG = "SupabaseRestClient"
        private val HTTP_SUCCESS_RANGE = 200..299
    }
}
