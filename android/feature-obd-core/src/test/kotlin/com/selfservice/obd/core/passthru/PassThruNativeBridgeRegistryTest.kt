package com.selfservice.obd.core.passthru

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PassThruNativeBridgeRegistryTest {

    @AfterTest
    fun tearDown() {
        PassThruNativeBridgeRegistry.reset()
    }

    @Test
    fun resolveReturnsRegisteredBridge() {
        val bridge = FakePassThruBridge()
        PassThruNativeBridgeRegistry.register { bridge }

        val resolved = PassThruNativeBridgeRegistry.resolve()

        assertSame(bridge, resolved)
    }

    @Test
    fun resolveWithoutRegistrationFails() {
        PassThruNativeBridgeRegistry.reset()

        assertFailsWith<IllegalStateException> { PassThruNativeBridgeRegistry.resolve() }
    }
}
