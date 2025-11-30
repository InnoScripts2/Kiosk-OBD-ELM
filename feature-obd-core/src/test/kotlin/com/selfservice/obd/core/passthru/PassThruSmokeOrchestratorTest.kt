package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

private fun dispatcherFrom(context: CoroutineContext): CoroutineDispatcher =
        context[ContinuationInterceptor] as? CoroutineDispatcher
                ?: error("Test coroutine context missing dispatcher")

private class TestDispatchers(private val dispatcher: CoroutineDispatcher) : DispatchersProvider {
    override val io = dispatcher
    override val computation = dispatcher
    override val main = dispatcher
}

@OptIn(ExperimentalCoroutinesApi::class)
class PassThruSmokeOrchestratorTest {

    @Test
    fun orchestratorDelegatesToExecutor() = runTest {
        val dispatchers = TestDispatchers(dispatcherFrom(coroutineContext))
        val plan = PassThruSmokePlan.default()
        val payload = byteArrayOf(17, 34)
        val expected = PassThruSmokeRunner.Result(emptyList(), failure = null)
    var suppliedPayload: ByteArray? = null
    val orchestrator =
        PassThruSmokeOrchestrator(
            dispatchers = dispatchers,
            executor = { receivedPlan, supplier ->
                assertSame(plan, receivedPlan)
                suppliedPayload = supplier.invoke()
                expected
            }
        )

    val result = orchestrator.run(plan, payloadSupplier = { payload })

        assertSame(expected, result)
        val actual = suppliedPayload ?: error("Payload supplier was not invoked")
        assertTrue(actual.contentEquals(payload))
    }

    @Test
    fun orchestratorReportsTimeoutAsFailure() = runTest {
        val dispatchers = TestDispatchers(dispatcherFrom(coroutineContext))
    val plan = PassThruSmokePlan.default()
    val orchestrator =
        PassThruSmokeOrchestrator(
            dispatchers = dispatchers,
            executor = { _, _ ->
                delay(2_000)
                PassThruSmokeRunner.Result(emptyList(), failure = null)
            }
        )

        val result = orchestrator.run(plan, timeoutMillis = 1_000)

        assertFalse(result.succeeded)
        val failure = result.failure ?: error("Expected timeout failure")
        assertEquals(PassThruSmokeCommandType.WATCHDOG_TIMEOUT, failure.command.type)
    }

    @Test
    fun runDelegatesToExecutorWithinTimeout() = runTest {
        val dispatchers = TestDispatchers(dispatcherFrom(coroutineContext))
        val plan = PassThruSmokePlan(
                channelConfig = PassThruChannelConfig(protocolId = 0x01, baudRate = 500_000),
                commands = emptyList()
        )
        val orchestrator = PassThruSmokeOrchestrator(
                dispatchers = dispatchers,
                executor = { _, _ ->
                    PassThruSmokeRunner.Result(executed = emptyList(), failure = null)
                }
        )

        val result = orchestrator.run(plan, timeoutMillis = 1_000L)

        assertTrue(result.failure == null)
        assertEquals(0, result.executed.size)
    }

    @Test
    fun runRejectsNonPositiveTimeout() = runTest {
        val dispatchers = TestDispatchers(dispatcherFrom(coroutineContext))
        val plan = PassThruSmokePlan(
                channelConfig = PassThruChannelConfig(protocolId = 0x01, baudRate = 500_000),
                commands = emptyList()
        )
        val orchestrator = PassThruSmokeOrchestrator(dispatchers)

        assertFailsWith<IllegalArgumentException> {
            orchestrator.run(plan, timeoutMillis = 0L)
        }
    }
}
