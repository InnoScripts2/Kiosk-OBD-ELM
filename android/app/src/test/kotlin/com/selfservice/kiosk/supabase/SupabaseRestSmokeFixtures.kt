package com.selfservice.kiosk.supabase

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

/**
 * Минимальный тестовый REST-endpoint Supabase, фиксирующий переданные payload и метаданные запроса.
 */
class FakeSupabaseEndpoint {
    data class RecordedRequest(
        val table: String,
        val payload: JSONObject,
        val url: URL,
        val headers: Map<String, String>
    )

    val rows: MutableList<JSONObject> = mutableListOf()
    val tableNames: MutableList<String> = mutableListOf()
    val requests: MutableList<RecordedRequest> = mutableListOf()
    var lastConnection: CapturingHttpConnection? = null

    fun createConnection(url: URL): HttpURLConnection {
        val connection = CapturingHttpConnection(url, this)
        lastConnection = connection
        return connection
    }

    internal fun record(connection: CapturingHttpConnection, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val payload = JSONObject(String(bytes, StandardCharsets.UTF_8))
        rows += payload
        val table = connection.url.path.substringAfter("/rest/v1/").substringBefore('?')
        tableNames += table
        requests += RecordedRequest(
            table = table,
            payload = payload,
            url = connection.url,
            headers = connection.headers.toMap()
        )
    }

    fun fetchFirst(): JSONObject {
        require(rows.isNotEmpty()) { "No rows recorded" }
        val array = JSONArray()
        rows.forEach { array.put(it) }
        return array.getJSONObject(0)
    }
}

/**
 * Реализация [HttpURLConnection], собирающая тело запроса и реплеящая успешный ответ.
 */
class CapturingHttpConnection(
    url: URL,
    private val endpoint: FakeSupabaseEndpoint
) : HttpURLConnection(url) {

    val headers: MutableMap<String, String> = mutableMapOf()
    private val output = ByteArrayOutputStream()
    private var recorded = false

    override fun getOutputStream(): OutputStream {
        return object : OutputStream() {
            override fun write(b: Int) {
                output.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                output.write(b, off, len)
            }

            override fun flush() {
                output.flush()
            }

            override fun close() {
                recordIfNeeded()
            }
        }
    }

    override fun setRequestProperty(key: String?, value: String?) {
        if (key != null && value != null) {
            headers[key] = value
        }
    }

    override fun getInputStream(): InputStream {
        recordIfNeeded()
        return ByteArrayInputStream("""{"status":"ok"}""".toByteArray(StandardCharsets.UTF_8))
    }

    override fun getErrorStream(): InputStream? = null

    override fun getResponseCode(): Int {
        recordIfNeeded()
        return 201
    }

    override fun disconnect() {}

    override fun connect() {}

    override fun usingProxy(): Boolean = false

    override fun setFixedLengthStreamingMode(contentLength: Int) {}

    override fun setFixedLengthStreamingMode(contentLength: Long) {}

    private fun recordIfNeeded() {
        if (!recorded) {
            recorded = true
            endpoint.record(this, output.toByteArray())
        }
    }
}

val HEX_64_PATTERN: Regex = Regex("[0-9a-f]{64}")