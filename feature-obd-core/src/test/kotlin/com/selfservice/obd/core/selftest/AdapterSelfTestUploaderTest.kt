package com.selfservice.obd.core.selftest

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

private class RecordingSelfTestResultDao : SelfTestResultDao {
    val rows = mutableListOf<Map<String, Any?>>()

    override suspend fun upsert(row: Map<String, Any?>) {
        rows += row
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AdapterSelfTestUploaderTest {

    @Test
    fun uploadDelegatesToDaoWithSchemaPayload() = runTest {
        val dao = RecordingSelfTestResultDao()
        val uploader = AdapterSelfTestUploader(dao)
        val metadata =
                AdapterSelfTestUploadMetadata(
                        sessionId = "session-123",
                        adapterSerial = "PT-001",
                        startedAtIso = "2025-11-15T10:00:00Z",
                        completedAtIso = "2025-11-15T10:01:00Z"
                )
        val step =
                AdapterSelfTestStep(
                        id = AdapterSelfTestId.FT01,
                        timeoutMs = 1_000L,
                        requiresVehicle = false,
                        retries = 0
                )
        val run =
                AdapterSelfTestRun(
                        executions =
                                listOf(
                                        AdapterSelfTestExecution(
                                                step = step,
                                                outcome = AdapterSelfTestExecution.Outcome.SUCCESS,
                                                attempts = 1,
                                                durationMs = 250L,
                                                message = null
                                        )
                                )
                )

        uploader.upload(run, metadata)

        val payload = dao.rows.single()
        val expected =
                AdapterSelfTestSchema.toRowPayload(
                        run = run,
                        sessionId = metadata.sessionId,
                        adapterSerial = metadata.adapterSerial,
                        startedAtIso = metadata.startedAtIso,
                        completedAtIso = metadata.completedAtIso
                )
        assertEquals(expected, payload)
    }
}
