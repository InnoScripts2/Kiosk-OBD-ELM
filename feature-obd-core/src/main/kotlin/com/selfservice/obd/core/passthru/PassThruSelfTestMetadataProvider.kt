package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.selftest.AdapterSelfTestRun
import com.selfservice.obd.core.selftest.AdapterSelfTestUploadMetadata
import com.selfservice.obd.core.session.ConnectedAdapter
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Default metadata provider for passthru adapter self-tests. Generates a Supabase-compatible
 * payload that captures the adapter serial and timestamps in ISO-8601 form.
 */
class PassThruSelfTestMetadataProvider(
        private val sessionIdFactory: (ConnectedAdapter, Long) -> String = { adapter, startedAt ->
            val sanitizedSerial = sanitizeSerial(adapter)
            "pst-${sanitizedSerial}-${startedAt}"
        },
        private val serialExtractor: (ConnectedAdapter) -> String? = { adapter ->
            adapter.device.address.takeIf { it.isNotBlank() }
        },
        private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
) : PassThruSelfTestService.MetadataProvider {

    override suspend fun provide(
            run: AdapterSelfTestRun,
            adapter: ConnectedAdapter,
            startedAtMillis: Long,
            completedAtMillis: Long
    ): AdapterSelfTestUploadMetadata? {
        val serial = serialExtractor(adapter) ?: return null
        val sessionId = sessionIdFactory(adapter, startedAtMillis)
        val startedIso = isoFormatter.format(Instant.ofEpochMilli(startedAtMillis))
        val completedIso = isoFormatter.format(Instant.ofEpochMilli(completedAtMillis))
        return AdapterSelfTestUploadMetadata(
                sessionId = sessionId,
                adapterSerial = serial,
                startedAtIso = startedIso,
                completedAtIso = completedIso
        )
    }

    companion object {
        private fun sanitizeSerial(adapter: ConnectedAdapter): String {
            val raw = adapter.device.address
            return raw
                    .replace(":", "")
                    .uppercase(Locale.US)
                    .ifEmpty { "UNKNOWN" }
        }
    }
}
