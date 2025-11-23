package com.selfservice.kiosk.diagnostics

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.dictionary.ManufacturerDictionaryInstaller
import com.selfservice.obd.core.connection.BleAdapterManager
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScanResult
import com.selfservice.obd.core.connection.BleScanner
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.connection.TransportFactory
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionConfig
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import com.selfservice.obd.core.transport.ObdTransport
import com.selfservice.obd.core.transport.TransportConnectionResult
import com.selfservice.obd.core.transport.TransportFrame
import com.selfservice.obd.core.transport.TransportSendResult
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class PassThruDiagnosticsRuntimeTest {

    private val storageRoot: File = Files.createTempDirectory("diagnostics-runtime").toFile()
    private val context: Context = TestContext(storageRoot)
    private val dispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatchersProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val computation: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }
    private val adapter = ConnectedAdapter(
            device = BleDevice(address = "00:11:22:33:44:55", name = "PassThru", rssi = -40),
            protocol = "J2534"
    )

    @AfterTest
    fun tearDown() {
        ManufacturerDictionaryInstaller.resetForTests()
        storageRoot.deleteRecursively()
    }

    @Test
    fun runDiagnosticsBootstrapsSessionAndDelegatesToExecutor() = runTest(dispatcher) {
        val controller = RecordingController()
        val captured = CapturingExecutor()
        val transportFactory = RecordingTransportFactory()
        val runtime = PassThruDiagnosticsRuntime(
                context = context,
                dispatchers = dispatchers,
                dictionaryUpdateProvider = DictionaryUpdateProvider { null },
                dictionarySchedule = null,
                diagnosticsExecutorFactory = { captured },
                bridgeProvider = { FakeBridge },
                telemetry = ObdSessionTelemetry.NoOp,
        controllerBuilder = { controller },
        adapterManagerFactory = { BleAdapterManager(NoopBleScanner(), dispatchers) },
        transportFactoryOverride = transportFactory
        )

        val request = ObdDiagnosticsRequest()
        val result = runtime.runDiagnostics(adapter, request)

        val started = controller.startedWith
        assertNotNull(started, "Controller should receive start call")
        assertSame(captured.transport, started.second)
        assertEquals(adapter, started.first.initialAdapter)
        assertSame(controller.state.value, result.sessionState)
        assertEquals(captured.result, result.diagnostics)
        assertSame(transportFactory.transports.firstOrNull(), captured.transport)

        runtime.shutdown()
    }

    private class RecordingController : ObdSessionController {
        private val backingState = MutableStateFlow<ObdSessionState>(ObdSessionState.Idle)
        var startedWith: Pair<ObdSessionConfig, ObdTransport>? = null

        override val state: StateFlow<ObdSessionState> = backingState

        override suspend fun start(
                config: ObdSessionConfig,
                transport: ObdTransport
        ): ObdSessionState {
            startedWith = config to transport
            val adapter = config.initialAdapter
            backingState.value = adapter?.let { ObdSessionState.Ready(it.device) } ?: ObdSessionState.Idle
            return backingState.value
        }

        override suspend fun onTransportReady(adapter: ConnectedAdapter) {}

        override suspend fun onHandshakeCompleted() {}

        override suspend fun beginDiagnostics() {}

        override suspend fun recordDiagnosticsHeartbeat() {}

        override suspend fun completeSuccessfully() {
            backingState.value = ObdSessionState.Completed
        }

        override suspend fun fail(cause: Throwable) {
            backingState.value = ObdSessionState.Failed(cause)
        }

        override suspend fun onConnectionIssue(
                issue: com.selfservice.obd.core.recovery.BleReconnectCoordinator.ConnectionIssue,
                cause: Throwable?,
                adapter: ConnectedAdapter?
        ): com.selfservice.obd.core.recovery.BleReconnectCoordinator.RecoveryDecision {
            backingState.value = ObdSessionState.Failed(cause ?: IllegalStateException("issue"))
            return com.selfservice.obd.core.recovery.BleReconnectCoordinator.RecoveryDecision.Abort
        }

        override suspend fun cancel() {}
    }

    private class CapturingExecutor : PassThruDiagnosticsCoordinator.DiagnosticsExecutor {
        var transport: ObdTransport? = null
        val result: ObdDiagnosticsResult = ObdDiagnosticsResult(
                pidSamples = emptyList(),
                troubleCodes = null,
                clearPerformed = null,
                failures = emptyList(),
                durationMillis = 0L
        )

        override suspend fun execute(
                transport: ObdTransport,
                request: ObdDiagnosticsRequest
        ): ObdDiagnosticsResult {
            this.transport = transport
            return result
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

    private class TestContext(root: File) : ContextWrapper(Application()) {
        private val directory: File = root

        override fun getFilesDir(): File = directory

        override fun getApplicationContext(): Context = this
    }

    private class NoopBleScanner : BleScanner {
        private val flow = MutableSharedFlow<BleScanResult>(extraBufferCapacity = 8)

        override val results = flow

        override suspend fun start(config: BleScannerConfig) {}

        override suspend fun stop() {}
    }

    private class RecordingTransportFactory : TransportFactory {
        val transports = mutableListOf<ObdTransport>()

        override suspend fun create(device: BleDevice): ObdTransport {
            val transport = FakeObdTransport()
            transports += transport
            return transport
        }
    }

    private class FakeObdTransport : ObdTransport {
        override val frames = MutableSharedFlow<TransportFrame>(extraBufferCapacity = 8)

        override suspend fun connect(): TransportConnectionResult = TransportConnectionResult.Success

        override suspend fun send(frame: TransportFrame): TransportSendResult = TransportSendResult.Delivered

        override suspend fun disconnect() {}
    }
}
