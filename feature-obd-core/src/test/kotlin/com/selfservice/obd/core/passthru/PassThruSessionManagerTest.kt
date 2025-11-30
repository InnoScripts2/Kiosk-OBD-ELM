package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.dictionary.DictionarySyncCoordinator
import com.selfservice.obd.core.dictionary.DictionarySyncExecutor
import com.selfservice.obd.core.dictionary.DictionarySyncResult
import com.selfservice.obd.core.dtc.DtcDictionaryRevision
import com.selfservice.obd.core.pid.PidDictionaryRevision
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.TransportConnectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

private class ManagerDispatchers(private val dispatcher: CoroutineDispatcher) :
        DispatchersProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val computation: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class PassThruSessionManagerTest {

    @Test
    fun startWithAdapterUsesPreparedBootstrap() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = ManagerDispatchers(dispatcher)
        val controller =
                TestRecordingObdSessionController().apply {
                    nextStartState =
                            ObdSessionState.Connecting(
                                    BleDevice(
                                            address = "00:11:22:33:44:55",
                                            name = "Passthru",
                                            rssi = null
                                    )
                            )
                }
        val bridge = FakePassThruBridge()
        val adapter =
                ConnectedAdapter(
                        device =
                                BleDevice(
                                        address = "00:11:22:33:44:55",
                                        name = "Passthru",
                                        rssi = null
                                ),
                        protocol = "J2534"
                )
        val configFactory =
                PassThruSessionConfigFactory(dispatchers = dispatchers, bridgeProvider = { bridge })
        val manager = PassThruSessionManager(controller = controller, configFactory = configFactory)

        val result = manager.startWithAdapter(adapter)

        assertTrue(result is ObdSessionState.Connecting)
        val (sessionConfig, transport) = requireNotNull(controller.startedWith)
        assertSame(configFactory.transportFactory(), sessionConfig.transportFactory)
        val passthruTransport = assertIs<PassThruTransport>(transport)
        passthruTransport.disconnect()
        passthruTransport.close()
    }

    @Test
    fun stateReferencesControllerState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = ManagerDispatchers(dispatcher)
        val controller = TestRecordingObdSessionController()
        val bridge = FakePassThruBridge()
        val manager =
                PassThruSessionManager(
                        controller = controller,
                        configFactory =
                                PassThruSessionConfigFactory(
                                        dispatchers = dispatchers,
                                        bridgeProvider = { bridge }
                                )
                )

        assertSame(controller.state, manager.state)
    }

    @Test
    fun exposedFactoryMethodsDelegateToConfigFactory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = ManagerDispatchers(dispatcher)
        val controller = TestRecordingObdSessionController()
        val bridge = FakePassThruBridge()
        val configFactory =
                PassThruSessionConfigFactory(dispatchers = dispatchers, bridgeProvider = { bridge })
        val manager = PassThruSessionManager(controller = controller, configFactory = configFactory)
        val adapter =
                ConnectedAdapter(
                        device =
                                BleDevice(
                                        address = "AA:BB:CC:DD:EE:FF",
                                        name = "Passthru",
                                        rssi = null
                                ),
                        protocol = "J2534"
                )
        val customScanner =
                BleScannerConfig(
                        targetSerialPattern = null,
                        serviceUuids = emptyList(),
                        timeoutMs = 10_000L
                )

        val config = manager.createConfig(initialAdapter = adapter, scannerConfig = customScanner)
        val transportFactory = manager.transportFactory()
        val transport = transportFactory.create(adapter.device)

        assertEquals(customScanner, config.scanner)
        assertSame(configFactory.transportFactory(), transportFactory)
        val passthruTransport = assertIs<PassThruTransport>(transport)
        val connectResult = passthruTransport.connect()
        assertTrue(connectResult is TransportConnectionResult.Success)
        passthruTransport.disconnect()
        passthruTransport.close()
    }

    @Test
    fun startTriggersDictionarySyncBeforeSessionStart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = ManagerDispatchers(dispatcher)
        val controller = TestRecordingObdSessionController()
        val bridge = FakePassThruBridge()
        val syncCalls = mutableListOf<Boolean>()
        val coordinator =
                DictionarySyncCoordinator(
                        dispatchers = dispatchers,
                        executor =
                                DictionarySyncExecutor { force ->
                                    syncCalls += force
                                    DictionarySyncResult.Performed(
                                            pidRevision =
                                                    PidDictionaryRevision(
                                                            source = "test",
                                                            versionLabel = "v1",
                                                            refreshedAtMillis = 1L,
                                                            entryCount = 0
                                                    ),
                                            dtcRevision =
                                                    DtcDictionaryRevision(
                                                            source = "test",
                                                            versionLabel = "v1",
                                                            refreshedAtMillis = 1L,
                                                            entryCount = 0
                                                    ),
                                            attemptedAtMillis = 2L
                                    )
                                }
                )
        val configFactory =
                PassThruSessionConfigFactory(dispatchers = dispatchers, bridgeProvider = { bridge })
        val manager =
                PassThruSessionManager(
                        controller = controller,
                        configFactory = configFactory,
                        dictionarySyncCoordinator = coordinator
                )
        val adapter =
                ConnectedAdapter(
                        device =
                                BleDevice(
                                        address = "FE:ED:FA:CE:00:01",
                                        name = "Passthru",
                                        rssi = null
                                ),
                        protocol = "J2534"
                )

        manager.startWithAdapter(adapter)

        assertEquals(listOf(false), syncCalls)
        coordinator.shutdown()
    }

    @Test
    fun syncDictionariesDelegatesToCoordinator() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = ManagerDispatchers(dispatcher)
        val controller = TestRecordingObdSessionController()
        val bridge = FakePassThruBridge()
        var forced: Boolean? = null
        val coordinator =
                DictionarySyncCoordinator(
                        dispatchers = dispatchers,
                        executor =
                                DictionarySyncExecutor { force ->
                                    forced = force
                                    DictionarySyncResult.Skipped(
                                            DictionarySyncResult.SkipReason.NO_UPDATES
                                    )
                                }
                )
        val manager =
                PassThruSessionManager(
                        controller = controller,
                        configFactory =
                                PassThruSessionConfigFactory(
                                        dispatchers = dispatchers,
                                        bridgeProvider = { bridge }
                                ),
                        dictionarySyncCoordinator = coordinator
                )

        val result = manager.syncDictionaries(force = true)

        assertEquals(true, forced)
        assertSame(result, manager.lastDictionarySync())
        coordinator.shutdown()
    }
}
