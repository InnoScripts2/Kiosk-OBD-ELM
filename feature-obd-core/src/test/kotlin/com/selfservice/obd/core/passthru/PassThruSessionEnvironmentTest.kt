package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.dictionary.DictionarySyncCoordinator
import com.selfservice.obd.core.dictionary.DictionarySyncExecutor
import com.selfservice.obd.core.dictionary.DictionarySyncResult
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dtc.DtcDictionaryRevision
import com.selfservice.obd.core.pid.PidDictionaryRevision
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.DefaultObdSessionController
import com.selfservice.obd.core.session.ObdSessionConfig
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.ObdTransport
import com.selfservice.obd.core.transport.TransportConnectionResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest

private class EnvironmentDispatchers(private val dispatcher: CoroutineDispatcher) :
        DispatchersProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val computation: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

class PassThruSessionEnvironmentTest {
    private val bridge = FakePassThruBridge()

    @AfterTest
    fun tearDown() {
        PassThruNativeBridgeRegistry.reset()
    }

        private fun dispatchersFor(scheduler: TestCoroutineScheduler): DispatchersProvider {
                return EnvironmentDispatchers(StandardTestDispatcher(scheduler))
        }

    @Test
    fun managerUsesControllerFromBuilder() = runTest {
                val dispatchers = dispatchersFor(testScheduler)
        val controller = RecordingController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller }
                )
        val adapter = ConnectedAdapter(device = device(), protocol = "J2534")

        val bootstrap = environment.configFactory.prepareSession(adapter)
        environment.manager.startWithBootstrap(bootstrap)

        assertSame(controller.state, environment.manager.state)
        assertEquals(bootstrap.config, controller.startedWith?.first)
        assertSame(bootstrap.transport, controller.startedWith?.second)
    }

    @Test
    fun defaultBridgeProviderUsesRegistry() = runTest {
                val dispatchers = dispatchersFor(testScheduler)
        PassThruNativeBridgeRegistry.register { bridge }
        val environment = PassThruSessionEnvironment(dispatchers = dispatchers)
        val adapter = ConnectedAdapter(device = device(address = "22:33:44"), protocol = "J2534")

        val bootstrap = environment.configFactory.prepareSession(adapter)
        val result = bootstrap.transport.connect()

        assertTrue(result is TransportConnectionResult.Success)
        assertEquals(1, bridge.openCalls.size)
        bootstrap.transport.disconnect()
    }

    @Test
    fun configFactoryRespectsOverrides() = runTest {
        val dispatchers = dispatchersFor(testScheduler)
        val scanner =
                BleScannerConfig(
                        targetSerialPattern = Regex("PT-.*"),
                        serviceUuids = emptyList(),
                        timeoutMs = 3_000L
                )
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        defaultScanner = scanner,
                        retryCount = 5,
                        reconnectDelayMs = 1_250L
                )
        val adapter = ConnectedAdapter(device = device(address = "11:22:33"), protocol = "J2534")

        val config = environment.manager.createConfig(initialAdapter = adapter)

        assertEquals(scanner, config.scanner)
        assertEquals(5, config.retryCount)
        assertEquals(1_250L, config.reconnectDelayMs)
        assertEquals(adapter, config.initialAdapter)
    }

    @Test
    fun defaultControllerIsUsedWhenBuilderNotProvided() {
        val dispatchers = EnvironmentDispatchers(StandardTestDispatcher())
        val environment =
                PassThruSessionEnvironment(dispatchers = dispatchers, bridgeProvider = { bridge })

        assertIs<DefaultObdSessionController>(environment.controller)
    }

    @Test
    fun dictionarySyncCoordinatorIsInvokedViaManager() = runTest {
        val dispatchers = dispatchersFor(testScheduler)
        val syncFlags = mutableListOf<Boolean>()
        val coordinator =
                DictionarySyncCoordinator(
                        dispatchers = dispatchers,
                        executor =
                                DictionarySyncExecutor { force ->
                                    syncFlags += force
                                    DictionarySyncResult.Performed(
                                            pidRevision =
                                                    PidDictionaryRevision(
                                                            source = "test",
                                                            versionLabel = "v1",
                                                            refreshedAtMillis = 10L,
                                                            entryCount = 0
                                                    ),
                                            dtcRevision =
                                                    DtcDictionaryRevision(
                                                            source = "test",
                                                            versionLabel = "v1",
                                                            refreshedAtMillis = 10L,
                                                            entryCount = 0
                                                    ),
                                            attemptedAtMillis = 11L
                                    )
                                }
                )
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        dictionarySyncCoordinator = coordinator
                )
        val adapter = ConnectedAdapter(device = device(), protocol = "J2534")

        val bootstrap = environment.configFactory.prepareSession(adapter)
        environment.manager.startWithBootstrap(bootstrap)

        assertEquals(listOf(false), syncFlags)
        environment.shutdown()
    }

    @Test
    fun dictionarySyncScheduleRegistersJob() = runTest {
        val dispatchers = dispatchersFor(testScheduler)
        val coordinator =
                DictionarySyncCoordinator(
                        dispatchers = dispatchers,
                        executor =
                                DictionarySyncExecutor {
                                    DictionarySyncResult.Skipped(
                                            DictionarySyncResult.SkipReason.NO_UPDATES
                                    )
                                }
                )
        val job = Job()
        val schedules = mutableListOf<DictionarySyncSchedule>()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        dictionarySyncCoordinator = coordinator,
                        dictionarySyncSchedule = DictionarySyncSchedule(
                                intervalMillis = 60_000L,
                                initialDelayMillis = 5_000L
                        ),
                        dictionarySyncScheduler = { _, schedule ->
                            schedules += schedule
                            job
                        }
                )

        assertEquals(1, schedules.size)
        val recorded = schedules.single()
        assertEquals(60_000L, recorded.intervalMillis)
        assertEquals(5_000L, recorded.initialDelayMillis)
        assertFalse(recorded.forceOnSchedule)

        environment.shutdown()

        assertTrue(job.isCancelled)
    }

    private fun device(address: String = "AA:BB:CC"): BleDevice =
            BleDevice(address = address, name = "Passthru", rssi = -42)

    private class RecordingController : ObdSessionController {
        private val backingState = MutableStateFlow<ObdSessionState>(ObdSessionState.Idle)
        var startedWith: Pair<ObdSessionConfig, ObdTransport>? = null

        override val state: StateFlow<ObdSessionState> = backingState

        override suspend fun start(
                config: ObdSessionConfig,
                transport: ObdTransport
        ): ObdSessionState {
            startedWith = config to transport
            return backingState.value
        }

        override suspend fun onTransportReady(adapter: ConnectedAdapter) {}

        override suspend fun onHandshakeCompleted() {}

        override suspend fun beginDiagnostics() {}

        override suspend fun recordDiagnosticsHeartbeat() {}

        override suspend fun completeSuccessfully() {}

        override suspend fun fail(cause: Throwable) {}

        override suspend fun onConnectionIssue(
                issue: com.selfservice.obd.core.recovery.BleReconnectCoordinator.ConnectionIssue,
                cause: Throwable?,
                adapter: ConnectedAdapter?
        ): com.selfservice.obd.core.recovery.BleReconnectCoordinator.RecoveryDecision {
            return com.selfservice.obd.core.recovery.BleReconnectCoordinator.RecoveryDecision.Abort
        }

        override suspend fun cancel() {}
    }
}
