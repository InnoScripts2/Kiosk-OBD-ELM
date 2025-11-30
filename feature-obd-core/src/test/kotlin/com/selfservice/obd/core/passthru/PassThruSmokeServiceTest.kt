package com.selfservice.obd.core.passthru

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PassThruSmokeServiceTest {

    private val bridge = FakePassThruBridge()

    @AfterTest
    fun tearDown() {
        PassThruNativeBridgeRegistry.reset()
    }

    @Test
    fun runDelegatesToExecutor() = runTest {
        val plan = PassThruSmokePlan.default()
        val payload = byteArrayOf(0x01, 0x02)
        val result = PassThruSmokeRunner.Result(emptyList(), failure = null)
        var capturedBridge: PassThruNativeBridge? = null
        var capturedPlan: PassThruSmokePlan? = null
        val payloads = mutableListOf<ByteArray>()
        val service =
                PassThruSmokeService(
                        bridgeProvider = { bridge },
                        executor = { nativeBridge, nativePlan, supplier ->
                            capturedBridge = nativeBridge
                            capturedPlan = nativePlan
                            payloads += supplier.invoke()
                            result
                        }
                )

    val executed = service.run(plan) { payload }

        assertSame(bridge, capturedBridge)
        assertSame(plan, capturedPlan)
        assertTrue(payloads.single().contentEquals(payload))
        assertSame(result, executed)
    }

    @Test
    fun runUsesRegistryBridgeByDefault() = runTest {
        val plan = PassThruSmokePlan.default()
        PassThruNativeBridgeRegistry.register { bridge }
        val service =
                PassThruSmokeService(
                        executor = { nativeBridge, _, _ ->
                            assertSame(bridge, nativeBridge)
                            PassThruSmokeRunner.Result(emptyList(), failure = null)
                        }
                )

        val result = service.run(plan)

        assertTrue(result.succeeded)
    }
}
