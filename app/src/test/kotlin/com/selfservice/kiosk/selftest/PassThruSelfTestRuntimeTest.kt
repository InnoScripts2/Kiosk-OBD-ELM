package com.selfservice.kiosk.selftest

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.selfservice.kiosk.PassThruSelfTestEntryPoint
import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.kiosk.supabase.SupabaseOutboxWriter
import com.selfservice.kiosk.supabase.SupabaseOutboxUploader
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionarySyncState
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruTransport
import com.selfservice.obd.core.passthru.PassThruSelfTestExecutor
import com.selfservice.obd.core.passthru.PassThruSmokePlan
import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestId
import com.selfservice.obd.core.selftest.AdapterSelfTestPlan
import com.selfservice.obd.core.selftest.AdapterSelfTestSchema
import com.selfservice.obd.core.selftest.SelfTestResultDao
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionConfig
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.ObdTransport
import com.selfservice.obd.core.transport.TransportFrame
import com.selfservice.obd.core.recovery.BleReconnectCoordinator
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.test.assertTrue

class PassThruSelfTestRuntimeTest {

    private val storageRoot: File = Files.createTempDirectory("selftest-runtime").toFile()
    private val context: Context = TestContext(storageRoot)
    private val adapter: ConnectedAdapter =
            ConnectedAdapter(
                    device = BleDevice(address = "AA:BB:CC:DD", name = "PassThru", rssi = -40),
                    protocol = "J2534"
            )
    private val stepHandler =
            PassThruSelfTestExecutor.StepHandler { _, _ ->
                AdapterSelfTestExecution.Outcome.SUCCESS
            }
        private val deterministicStepHandler =
                        PassThruSelfTestExecutor.StepHandler { step, transport ->
                                transport.connect()
                                when (step.id) {
                                        AdapterSelfTestId.FT01,
                                        AdapterSelfTestId.FT02,
                                        AdapterSelfTestId.FT03 -> AdapterSelfTestExecution.Outcome.SUCCESS
                                        else -> AdapterSelfTestExecution.Outcome.FAILED
                                }
                        }
    private val daoDirectory = AtomicReference<File?>()

    @After
    fun tearDown() {
        daoDirectory.getAndSet(null)
        storageRoot.deleteRecursively()
                PassThruSelfTestEntryPoint.resetOverride()
                ManufacturerDictionaryInstaller.resetForTests()
    }

    @Test
    fun `createHandle wires storage and dictionary sync`() {
        val runtime =
                PassThruSelfTestRuntime(
                        context = context,
                        dictionarySchedule =
                                DictionarySyncSchedule(
                                        intervalMillis = Long.MAX_VALUE,
                                        initialDelayMillis = Long.MAX_VALUE
                                ),
                                                resultDaoFactory = { dir, _ ->
                            daoDirectory.set(dir)
                            SelfTestResultDaoStub()
                        },
                        bridgeProvider = { FakeBridge }
                )

        val handle = runtime.createHandle(adapterProvider = { adapter }, stepHandler = stepHandler)

        val recordedDir = daoDirectory.get()
        assertNotNull(recordedDir)
        assertEquals(File(context.filesDir, "selftests/outbox"), recordedDir)
        assertEquals(DictionarySyncState.Idle, handle.dictionarySyncCoordinator.state().value)
                assertEquals(DictionarySyncState.Idle, runtime.dictionarySyncState().value)

        handle.shutdown()
                runtime.shutdown()
    }

        @Test
                fun `createOutboxUploader provides access to Supabase queue`() = runBlocking {
                        val runtime = PassThruSelfTestRuntime(context = context, bridgeProvider = { FakeBridge })
                        val outboxDirectory = File(context.filesDir, "selftests/outbox")
                        val writer = SupabaseOutboxWriter(outboxDirectory)
                        writer.enqueue("adapter_selftests", mapOf("session_id" to "session-test"))
                        val client = RecordingClient()

                        val uploader = runtime.createOutboxUploader(client)
                        val result = uploader.flush()

                        assertTrue(result.isSuccess)
                        assertEquals(1, result.processed)
                        val payload = client.operations.single().payload
                        assertEquals("session-test", payload.getString("session_id"))
                        val outboxFile = File(outboxDirectory, SupabaseOutboxWriter.DEFAULT_FILE_NAME)
                        assertTrue(!outboxFile.exists() || outboxFile.length() == 0L)
        runtime.shutdown()
        }

        @Test
                fun `outbox observers receive notifications on enqueue`() = runBlocking {
                        val runtime = PassThruSelfTestRuntime(context = context, bridgeProvider = { FakeBridge })
                        val notifications = AtomicInteger(0)
                        val observer: () -> Unit = {
                                notifications.incrementAndGet()
                                Unit
                        }
                        runtime.registerOutboxObserver(observer)

                        var capturedConfig: PassThruSelfTestEntryPoint.Config? = null
                        PassThruSelfTestEntryPoint.override { config ->
                                capturedConfig = config
                                null
                        }

                        runtime.createHandle(adapterProvider = { adapter }, stepHandler = stepHandler)

                        val config = capturedConfig ?: error("Self-test config not captured")
                        val dao = config.resultDaoFactory(config.storageDirectory)
                        dao.upsert(mapOf("session_id" to "notify-test"))

                        assertEquals(1, notifications.get())

                        runtime.unregisterOutboxObserver(observer)
        runtime.shutdown()
        }

                        @Test
                        fun `runSmokeTest uses shared bridge provider`() {
                                val bridge = RecordingBridge()
                                val runtime = PassThruSelfTestRuntime(context = context, bridgeProvider = { bridge })

                                val result = runtime.runSmokeTest(
                                        plan = PassThruSmokePlan.default(),
                                        payloadSupplier = { byteArrayOf(0x02, 0x3E, 0x00) }
                                )

                                assertTrue(result.succeeded)
                                assertEquals(listOf("openChannel", "setReferenceVoltage", "writeMessage", "readMessage", "closeChannel"), bridge.calls)

                                runtime.shutdown()
                        }

                @Test
                fun `self-test run uses mock passthru bridge and persists Supabase outbox`() = runBlocking {
                        val bridge = DevMockPassThruBridge()
                        val runtime =
                                PassThruSelfTestRuntime(
                                        context = context,
                                        bridgeProvider = { bridge },
                                        controllerBuilder = { StubObdSessionController() }
                                )
                        val handle =
                                runtime.createHandle(
                                        adapterProvider = { adapter },
                                        stepHandler = deterministicStepHandler
                                )

                        val run = handle.service.run(AdapterSelfTestPlan.default())

                        assertEquals(4, run.executions.size)
                        assertTrue(run.executions.take(3).all { it.outcome == AdapterSelfTestExecution.Outcome.SUCCESS })
                        assertEquals(AdapterSelfTestExecution.Outcome.FAILED, run.executions.last().outcome)

                        val outboxDirectory = runtime.outboxDirectory()
                        val outboxFile = File(outboxDirectory, SupabaseOutboxWriter.DEFAULT_FILE_NAME)
                        assertTrue(outboxFile.exists())
                        val envelope = JSONObject(outboxFile.readLines().single())
                        assertEquals(AdapterSelfTestSchema.TABLE_NAME, envelope.getString("table"))
                        val payload = envelope.getJSONObject("payload")
                        assertTrue(payload.has(AdapterSelfTestSchema.Columns.EXECUTIONS))
                        assertEquals(run.executions.size, payload.getJSONArray(AdapterSelfTestSchema.Columns.EXECUTIONS).length())
                        assertEquals(run.succeeded, payload.getBoolean(AdapterSelfTestSchema.Columns.SUCCEEDED))

                        handle.shutdown()
                        runtime.shutdown()
                }

    private class TestContext(root: File) : ContextWrapper(Application()) {
        private val directory: File = root

        override fun getFilesDir(): File = directory

        override fun getApplicationContext(): Context = this
    }

    private class SelfTestResultDaoStub : SelfTestResultDao {
        override suspend fun upsert(row: Map<String, Any?>) {}
    }

                private class StubObdSessionController : ObdSessionController {
                        private val mutableState = MutableStateFlow<ObdSessionState>(ObdSessionState.Idle)
                        private var activeTransport: ObdTransport? = null
                        override val state: StateFlow<ObdSessionState> = mutableState

                        override suspend fun start(config: ObdSessionConfig, transport: ObdTransport): ObdSessionState {
                                activeTransport = transport
                                return mutableState.value
                        }

                        override suspend fun onTransportReady(adapter: ConnectedAdapter) {}

                        override suspend fun onHandshakeCompleted() {}

                        override suspend fun beginDiagnostics() {}

                        override suspend fun recordDiagnosticsHeartbeat() {}

                        override suspend fun completeSuccessfully() {
                                mutableState.value = ObdSessionState.Completed
                                disconnectActiveTransport()
                        }

                        override suspend fun fail(cause: Throwable) {
                                mutableState.value = ObdSessionState.Failed(cause)
                                disconnectActiveTransport()
                        }

                        override suspend fun onConnectionIssue(
                                        issue: BleReconnectCoordinator.ConnectionIssue,
                                        cause: Throwable?,
                                        adapter: ConnectedAdapter?
                        ): BleReconnectCoordinator.RecoveryDecision = BleReconnectCoordinator.RecoveryDecision.Abort

                        override suspend fun cancel() {
                                mutableState.value = ObdSessionState.Idle
                                disconnectActiveTransport()
                        }

                        private suspend fun disconnectActiveTransport() {
                                val transport = activeTransport ?: return
                                runCatching { transport.disconnect() }
                                activeTransport = null
                        }
                }

        private class RecordingClient : SupabaseOutboxUploader.Client {
                val operations: MutableList<SupabaseOutboxUploader.Operation> = mutableListOf()

                override suspend fun execute(operation: SupabaseOutboxUploader.Operation) {
                        operations += operation
                }
        }

    private object FakeBridge : PassThruNativeBridge {
        override fun openChannel(config: com.selfservice.obd.core.passthru.PassThruChannelConfig): Result<Int> =
                Result.success(1)

        override fun closeChannel(channelId: Int): Result<Unit> = Result.success(Unit)

        override fun setReferenceVoltage(channelId: Int, millivolts: Int): Result<Unit> =
                Result.success(Unit)

        override fun writeMessage(
                message: com.selfservice.obd.core.passthru.PassThruMessage,
                timeoutMs: Int
        ): Result<Unit> = Result.success(Unit)

        override fun readMessage(
                channelId: Int,
                timeoutMs: Int
        ): Result<com.selfservice.obd.core.passthru.PassThruMessage?> = Result.success(null)
    }

        private class RecordingBridge : PassThruNativeBridge {
                val calls: MutableList<String> = mutableListOf()

                override fun openChannel(config: com.selfservice.obd.core.passthru.PassThruChannelConfig): Result<Int> {
                        calls += "openChannel"
                        return Result.success(1)
                }

                override fun closeChannel(channelId: Int): Result<Unit> {
                        calls += "closeChannel"
                        return Result.success(Unit)
                }

                override fun setReferenceVoltage(channelId: Int, millivolts: Int): Result<Unit> {
                        calls += "setReferenceVoltage"
                        return Result.success(Unit)
                }

                override fun writeMessage(
                                message: com.selfservice.obd.core.passthru.PassThruMessage,
                                timeoutMs: Int
                ): Result<Unit> {
                        calls += "writeMessage"
                        return Result.success(Unit)
                }

                override fun readMessage(
                                channelId: Int,
                                timeoutMs: Int
                ): Result<com.selfservice.obd.core.passthru.PassThruMessage?> {
                        calls += "readMessage"
                        return Result.success(null)
                }
        }
}
