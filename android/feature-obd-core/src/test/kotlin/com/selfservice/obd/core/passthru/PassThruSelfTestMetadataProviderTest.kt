package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestId
import com.selfservice.obd.core.selftest.AdapterSelfTestRun
import com.selfservice.obd.core.selftest.AdapterSelfTestStep
import com.selfservice.obd.core.session.ConnectedAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PassThruSelfTestMetadataProviderTest {

    @Test
    fun provideReturnsMetadataWithIsoTimestamps() = runTest {
        val provider = PassThruSelfTestMetadataProvider()
        val adapter =
                ConnectedAdapter(
                        device = BleDevice(address = "AA:BB:CC", name = "PassThru", rssi = -42),
                        protocol = "J2534"
                )
        val run =
                AdapterSelfTestRun(
                        executions =
                                listOf(
                                        AdapterSelfTestExecution(
                                                step =
                                                        AdapterSelfTestStep(
                                                                id = AdapterSelfTestId.FT01,
                                                                timeoutMs = 1_000,
                                                                requiresVehicle = false,
                                                                retries = 0
                                                        ),
                                                outcome = AdapterSelfTestExecution.Outcome.SUCCESS,
                                                attempts = 1,
                                                durationMs = 500,
                                                message = null
                                        )
                                )
                )
        val started = 1_000L
        val completed = 2_000L

        val metadata = provider.provide(run, adapter, started, completed)

        assertNotNull(metadata)
        assertEquals("AA:BB:CC", metadata.adapterSerial)
        assertEquals("1970-01-01T00:00:01Z", metadata.startedAtIso)
        assertEquals("1970-01-01T00:00:02Z", metadata.completedAtIso)
        assertTrue(metadata.sessionId.startsWith("pst-AABBCC-"))
    }

    @Test
    fun provideReturnsNullWhenSerialBlank() = runTest {
        val provider = PassThruSelfTestMetadataProvider()
        val adapter =
                ConnectedAdapter(
                        device = BleDevice(address = "", name = "PassThru", rssi = -42),
                        protocol = "J2534"
                )
        val run = AdapterSelfTestRun(emptyList())

        val metadata = provider.provide(run, adapter, startedAtMillis = 0L, completedAtMillis = 0L)

        assertNull(metadata)
    }
}
