package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestId
import com.selfservice.obd.core.selftest.AdapterSelfTestStep
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.transport.ObdTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

private class SelfTestDispatchers(private val dispatcher: CoroutineDispatcher) :
        DispatchersProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val computation: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class PassThruSelfTestExecutorTest {

    @Test
    fun executeReturnsSuccessWhenHandlerCompletes() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = SelfTestDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller = TestRecordingObdSessionController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller }
                )
        val capturedTransports = mutableListOf<ObdTransport>()
        val executor =
                PassThruSelfTestExecutor(
                        environmentProvider = { environment },
                        adapterProvider = { adapter() },
                        handler =
                                object : PassThruSelfTestExecutor.StepHandler {
                                    override suspend fun perform(
                                            step: AdapterSelfTestStep,
                                            transport: ObdTransport
                                    ): AdapterSelfTestExecution.Outcome {
                                        capturedTransports += transport
                                        return AdapterSelfTestExecution.Outcome.SUCCESS
                                    }
                                }
                )
        val step =
                AdapterSelfTestStep(
                        AdapterSelfTestId.FT01,
                        timeoutMs = 1000L,
                        requiresVehicle = false,
                        retries = 0
                )

        val result = executor.execute(step)

        assertEquals(AdapterSelfTestExecution.Outcome.SUCCESS, result.outcome)
        assertEquals(1, capturedTransports.size)
        assertTrue(capturedTransports.first() is PassThruTransport)
        assertTrue(controller.startedWith != null)
    }

    @Test
    fun executeReturnsFailureWhenHandlerThrows() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = SelfTestDispatchers(dispatcher)
        val bridge = FakePassThruBridge()
        val controller = TestRecordingObdSessionController()
        val environment =
                PassThruSessionEnvironment(
                        dispatchers = dispatchers,
                        bridgeProvider = { bridge },
                        controllerBuilder = { controller }
                )
        val executor =
                PassThruSelfTestExecutor(
                        environmentProvider = { environment },
                        adapterProvider = { adapter() },
                        handler =
                                object : PassThruSelfTestExecutor.StepHandler {
                                    override suspend fun perform(
                                            step: AdapterSelfTestStep,
                                            transport: ObdTransport
                                    ): AdapterSelfTestExecution.Outcome {
                                        throw IllegalStateException("boom")
                                    }
                                }
                )
        val step =
                AdapterSelfTestStep(
                        AdapterSelfTestId.FT02,
                        timeoutMs = 500L,
                        requiresVehicle = false,
                        retries = 0
                )

        val result = executor.execute(step)

        assertEquals(AdapterSelfTestExecution.Outcome.FAILED, result.outcome)
        assertEquals("boom", result.message)
        assertEquals(1, controller.failCount)
    }

    private fun adapter(): ConnectedAdapter =
            ConnectedAdapter(
                    device =
                            BleDevice(
                                    address = "11:22:33:44:55:66",
                                    name = "Passthru",
                                    rssi = null
                            ),
                    protocol = "J2534"
            )
}
