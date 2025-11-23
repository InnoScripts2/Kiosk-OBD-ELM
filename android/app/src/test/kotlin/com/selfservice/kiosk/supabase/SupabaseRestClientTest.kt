package com.selfservice.kiosk.supabase

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlin.text.Charsets

class SupabaseRestClientTest {

    @Test
    fun executePostsPayloadAndHeaders() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://example.supabase.co/rest/v1/adapter_selftests"))
        connection.responseCodeStub = 201
        val credentials = SupabaseCredentials(
                restUrl = "https://example.supabase.co",
                serviceKey = "test-service-key"
        )
        val client = SupabaseRestClient(credentials, connectionFactory = { connection }, logger = {})
        val payload = JSONObject(mapOf("session_id" to "abc"))
        val operation = SupabaseOutboxUploader.Operation(
                table = "adapter_selftests",
                operation = SupabaseOutboxWriter.OPERATION_UPSERT,
                payload = payload,
                writtenAtMillis = 0L
        )

        client.execute(operation)

        assertEquals("POST", connection.requestMethod)
    assertEquals("https://example.supabase.co/rest/v1/adapter_selftests", connection.url.toString())
    assertEquals("application/json", connection.headerValue("Content-Type"))
    assertEquals("Bearer test-service-key", connection.headerValue("Authorization"))
    assertEquals("resolution=merge-duplicates,return=minimal", connection.headerValue("Prefer"))
        assertEquals("{\"session_id\":\"abc\"}", connection.sentBody)
    }

    @Test
    fun executeThrowsWhenSupabaseReturnsError() = runBlocking {
        val connection = FakeHttpURLConnection(URL("https://example.supabase.co/rest/v1/adapter_selftests"))
        connection.responseCodeStub = 500
        connection.errorResponse = "failure".toByteArray()
        val credentials = SupabaseCredentials(
                restUrl = "https://example.supabase.co",
                serviceKey = "service-key"
        )
        val client = SupabaseRestClient(credentials, connectionFactory = { connection }, logger = {})
        val operation = SupabaseOutboxUploader.Operation(
                table = "adapter_selftests",
                operation = SupabaseOutboxWriter.OPERATION_UPSERT,
                payload = JSONObject(mapOf("session_id" to "abc")),
                writtenAtMillis = 0L
        )

        val error = assertFailsWith<IOException> { client.execute(operation) }

    assertEquals(true, error.message?.lowercase(Locale.US)?.contains("failure"))
    assertEquals("{\"session_id\":\"abc\"}", connection.sentBody)
    }

    private class FakeHttpURLConnection(url: URL) : HttpURLConnection(url) {
        private val headers: MutableMap<String, String> = linkedMapOf()
        private val output = ByteArrayOutputStream()
        var errorResponse: ByteArray? = null
        var responseCodeStub: Int = HTTP_OK
        val sentBody: String?
            get() = if (output.size() == 0) null else output.toString(Charsets.UTF_8.name())

        override fun setRequestProperty(key: String?, value: String?) {
            if (key != null && value != null) {
                headers[key] = value
            }
        }

        fun headerValue(name: String): String? = headers[name]

        override fun connect() {
            connected = true
        }

        override fun disconnect() {
            connected = false
        }

        override fun usingProxy(): Boolean = false

        override fun getOutputStream(): OutputStream = output

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getErrorStream(): InputStream? {
            val bytes = errorResponse ?: return null
            return ByteArrayInputStream(bytes)
        }

        override fun getResponseCode(): Int = responseCodeStub

        override fun setFixedLengthStreamingMode(contentLength: Int) {
            // no-op for test
        }
    }
}
