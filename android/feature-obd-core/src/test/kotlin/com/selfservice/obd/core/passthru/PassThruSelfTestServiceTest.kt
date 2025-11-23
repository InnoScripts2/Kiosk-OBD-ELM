package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestId
import com.selfservice.obd.core.selftest.AdapterSelfTestPlan
import com.selfservice.obd.core.selftest.AdapterSelfTestRun
import com.selfservice.obd.core.selftest.AdapterSelfTestStep
import com.selfservice.obd.core.selftest.AdapterSelfTestUploader
import com.selfservice.obd.core.selftest.AdapterSelfTestUploadMetadata
import com.selfservice.obd.core.session.ConnectedAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private class SelfTestServiceDispatchers(private val dispatcher: CoroutineDispatcher) :
        DispatchersProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val computation: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class RecordingUploader(
        private val dao: SelfTestResultDaoStub = SelfTestResultDaoStub()
) : AdapterSelfTestUploader(dao) {
    val runs = mutableListOf<Pair<AdapterSelfTestRun, AdapterSelfTestUploadMetadata>>()

    override suspend fun upload(run: AdapterSelfTestRun, metadata: AdapterSelfTestUploadMetadata) {
        runs += run to metadata
        super.upload(run, metadata)
    }

    val payloads: List<Map<String, Any?>>
        get() = dao.payloads
}

private class SelfTestResultDaoStub : com.selfservice.obd.core.selftest.SelfTestResultDao {
    val payloads = mutableListOf<Map<String, Any?>>()
    override suspend fun upsert(row: Map<String, Any?>) {
        payloads += row
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PassThruSelfTestServiceTest {

    @Test
    fun runUploadsResultsWhenMetadataProvided() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = SelfTestServiceDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller = TestRecordingObdSessionController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller },
                        dictionarySyncSchedule = null
                )
        var currentTime = 1_000L
        val clock = {
            val now = currentTime
            currentTime += 100L
            now
        }
        val uploader = RecordingUploader()
        val plan = AdapterSelfTestPlan.default()
        val recordedMetadata = mutableListOf<AdapterSelfTestUploadMetadata>()
        val service =
                PassThruSelfTestService(
                        environmentProvider = { environment },
                        adapterProvider = { adapter() },
                        stepHandler =
                                PassThruSelfTestExecutor.StepHandler { _, _ ->
                                    AdapterSelfTestExecution.Outcome.SUCCESS
                                },
                        metadataProvider =
                                PassThruSelfTestService.MetadataProvider { run, adapter, started, completed ->
                                    val metadata =
                                            AdapterSelfTestUploadMetadata(
                                                    sessionId = "session-${stepCodes(run)}",
                                                    adapterSerial = adapter.device.address,
                                                    startedAtIso = started.toString(),
                                                    completedAtIso = completed.toString()
                                            )
                                    recordedMetadata += metadata
                                    metadata
                                },
                        uploader = uploader,
                        clock = clock
                )

        val result = service.run(plan)

        assertTrue(result.succeeded)
        assertEquals(plan.steps.size, result.executions.size)
        val upload = uploader.runs.single()
        assertSameRun(plan, upload.first)
        val metadata = upload.second
        assertEquals("AA:BB:CC", metadata.adapterSerial)
        assertEquals(recordedMetadata.single(), metadata)
        val persisted = uploader.payloads.single()
        assertTrue(
                persisted.containsKey(
                        com.selfservice.obd.core.selftest.AdapterSelfTestSchema.Columns.EXECUTIONS
                )
        )
        assertNotNull(controller.startedWith)
        environment.shutdown()
    }

    @Test
    fun runSkipsUploadWhenMetadataProviderReturnsNull() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = SelfTestServiceDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller = TestRecordingObdSessionController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller },
                        dictionarySyncSchedule = null
                )
        val uploader = RecordingUploader()
        val plan = AdapterSelfTestPlan.default()
        val service =
                PassThruSelfTestService(
                        environmentProvider = { environment },
                        adapterProvider = { adapter() },
                        stepHandler =
                                PassThruSelfTestExecutor.StepHandler { _, _ ->
                                    AdapterSelfTestExecution.Outcome.SUCCESS
                                },
                        metadataProvider =
                                PassThruSelfTestService.MetadataProvider { _, _, _, _ -> null },
                        uploader = uploader,
                        clock = { 0L }
                )

        val result = service.run(plan)

        assertTrue(result.succeeded)
        assertTrue(uploader.runs.isEmpty())
        assertTrue(uploader.payloads.isEmpty())
        environment.shutdown()
    }

    @Test
    fun runDoesNotInvokeMetadataProviderWhenUploaderMissing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = SelfTestServiceDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller = TestRecordingObdSessionController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller },
                        dictionarySyncSchedule = null
                )
        var invoked = false
        val plan = AdapterSelfTestPlan.default()
        val service =
                PassThruSelfTestService(
                        environmentProvider = { environment },
                        adapterProvider = { adapter() },
                        stepHandler =
                                PassThruSelfTestExecutor.StepHandler { _, _ ->
                                    AdapterSelfTestExecution.Outcome.SUCCESS
                                },
                        metadataProvider =
                                PassThruSelfTestService.MetadataProvider { _, _, _, _ ->
                                    invoked = true
                                    AdapterSelfTestUploadMetadata(
                                            sessionId = "session",
                                            adapterSerial = "serial",
                                            startedAtIso = "start",
                                            completedAtIso = "end"
                                    )
                                },
                        uploader = null,
                        clock = { 0L }
                )

        val result = service.run(plan)

        assertTrue(result.succeeded)
                assertFalse(invoked)
        environment.shutdown()
    }

    private fun adapter(): ConnectedAdapter =
            ConnectedAdapter(
                    device = BleDevice(address = "AA:BB:CC", name = "PassThru", rssi = -30),
                    protocol = "J2534"
            )

    private fun stepCodes(run: AdapterSelfTestRun): String {
        return run.executions.joinToString(separator = "-") { it.step.id.code }
    }

    private fun assertSameRun(plan: AdapterSelfTestPlan, run: AdapterSelfTestRun) {
        assertEquals(plan.steps.size, run.executions.size)
        val executedIds = run.executions.map { it.step.id }
        assertEquals(plan.steps.map { it.id }, executedIds)
    }
}
