package com.selfservice.obd.core.selftest

/**
 * Persists adapter self-test runs into Supabase (or another backend) via the provided DAO. This
 * keeps serialization confined to a single place so transport layers only deal with payloads.
 */
open class AdapterSelfTestUploader(private val dao: SelfTestResultDao) {

    open suspend fun upload(run: AdapterSelfTestRun, metadata: AdapterSelfTestUploadMetadata) {
        val payload =
                AdapterSelfTestSchema.toRowPayload(
                        run = run,
                        sessionId = metadata.sessionId,
                        adapterSerial = metadata.adapterSerial,
                        startedAtIso = metadata.startedAtIso,
                        completedAtIso = metadata.completedAtIso
                )
        dao.upsert(payload)
    }
}

interface SelfTestResultDao {
    suspend fun upsert(row: Map<String, Any?>)
}

data class AdapterSelfTestUploadMetadata(
        val sessionId: String,
        val adapterSerial: String,
        val startedAtIso: String,
        val completedAtIso: String
)
