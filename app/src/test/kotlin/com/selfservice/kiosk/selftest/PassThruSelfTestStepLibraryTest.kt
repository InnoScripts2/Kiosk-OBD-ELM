package com.selfservice.kiosk.selftest

import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestId
import com.selfservice.obd.core.selftest.AdapterSelfTestStep
import com.selfservice.obd.core.transport.ObdTransport
import com.selfservice.obd.core.transport.TransportConnectionResult
import com.selfservice.obd.core.transport.TransportFrame
import com.selfservice.obd.core.transport.TransportSendResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking

class PassThruSelfTestStepLibraryTest {

    @Test
    fun powerRailStepConnectsTransport() = runBlocking {
        val transport = FakeObdTransport()
        val handler = PassThruSelfTestStepLibrary().createStepHandler()

        val outcome = handler.perform(step(AdapterSelfTestId.FT01), transport)

        assertEquals(AdapterSelfTestExecution.Outcome.SUCCESS, outcome)
        assertEquals(1, transport.connectCalls)
    }

    @Test
    fun loopbackTransmitFailsWhenSendFails() = runBlocking {
        val transport = FakeObdTransport().apply {
            sendResult = TransportSendResult.Failed(IllegalStateException("loopback"))
        }
        val handler = PassThruSelfTestStepLibrary().createStepHandler()

        val outcome = handler.perform(step(AdapterSelfTestId.FT02), transport)

        assertEquals(AdapterSelfTestExecution.Outcome.FAILED, outcome)
        assertEquals(1, transport.sentFrames.size)
    }

    @Test
    fun loopbackReceiveSucceedsWhenFrameArrives() = runBlocking {
        val transport = FakeObdTransport()
        val handler = PassThruSelfTestStepLibrary().createStepHandler()
        transport.emitFrame(TransportFrame(payload = byteArrayOf(0x7F)))

        val outcome = handler.perform(step(AdapterSelfTestId.FT03), transport)

        assertEquals(AdapterSelfTestExecution.Outcome.SUCCESS, outcome)
    }

    private fun step(id: AdapterSelfTestId): AdapterSelfTestStep =
            AdapterSelfTestStep(id = id, timeoutMs = 1_000, requiresVehicle = false, retries = 0)

    private class FakeObdTransport : ObdTransport {
        private val framesFlow = MutableSharedFlow<TransportFrame>(replay = 1, extraBufferCapacity = 1)
        var connectResult: TransportConnectionResult = TransportConnectionResult.Success
        var sendResult: TransportSendResult = TransportSendResult.Delivered
        val sentFrames: MutableList<TransportFrame> = mutableListOf()
        var connectCalls: Int = 0

        override val frames: Flow<TransportFrame> = framesFlow

        override suspend fun connect(): TransportConnectionResult {
            connectCalls += 1
            return connectResult
        }

        override suspend fun send(frame: TransportFrame): TransportSendResult {
            sentFrames += frame
            return sendResult
        }

        override suspend fun disconnect() {}

        fun emitFrame(frame: TransportFrame) {
            framesFlow.tryEmit(frame)
        }
    }
}
