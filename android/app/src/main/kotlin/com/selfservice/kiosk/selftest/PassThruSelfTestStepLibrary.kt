package com.selfservice.kiosk.selftest

import com.selfservice.obd.core.passthru.PassThruSelfTestExecutor
import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestId
import com.selfservice.obd.core.selftest.AdapterSelfTestStep
import com.selfservice.obd.core.transport.ObdTransport
import com.selfservice.obd.core.transport.TransportConnectionResult
import com.selfservice.obd.core.transport.TransportFrame
import com.selfservice.obd.core.transport.TransportSendResult
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Provides a reusable [PassThruSelfTestExecutor.StepHandler] that performs lightweight loopback
 * checks against a [ObdTransport]. Hardware intensive steps (FT-04…FT-06) are left to the
 * diagnostics flow once vehicle connectivity is available.
 */
class PassThruSelfTestStepLibrary(
        private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MS,
        private val payloadSupplier: (AdapterSelfTestStep) -> ByteArray = { DEFAULT_LOOPBACK_PAYLOAD }
) {

    fun createStepHandler(): PassThruSelfTestExecutor.StepHandler =
            PassThruSelfTestExecutor.StepHandler { step, transport ->
                when (step.id) {
                    AdapterSelfTestId.FT01 -> runPowerRailCheck(transport)
                    AdapterSelfTestId.FT02 -> runLoopbackTransmit(step, transport)
                    AdapterSelfTestId.FT03 -> runLoopbackReceive(transport)
                    AdapterSelfTestId.FT04,
                    AdapterSelfTestId.FT05,
                    AdapterSelfTestId.FT06 -> AdapterSelfTestExecution.Outcome.FAILED
                }
            }

    private suspend fun runPowerRailCheck(transport: ObdTransport): AdapterSelfTestExecution.Outcome {
        return when (transport.connect()) {
            is TransportConnectionResult.Success -> AdapterSelfTestExecution.Outcome.SUCCESS
            is TransportConnectionResult.Failure -> AdapterSelfTestExecution.Outcome.FAILED
        }
    }

    private suspend fun runLoopbackTransmit(
            step: AdapterSelfTestStep,
            transport: ObdTransport
    ): AdapterSelfTestExecution.Outcome {
        if (transport.connect() !is TransportConnectionResult.Success) {
            return AdapterSelfTestExecution.Outcome.FAILED
        }
        val payload = payloadSupplier(step)
        val frame = TransportFrame(payload = payload)
        return when (transport.send(frame)) {
            is TransportSendResult.Delivered -> AdapterSelfTestExecution.Outcome.SUCCESS
            is TransportSendResult.Failed -> AdapterSelfTestExecution.Outcome.FAILED
        }
    }

    private suspend fun runLoopbackReceive(transport: ObdTransport): AdapterSelfTestExecution.Outcome {
        if (transport.connect() !is TransportConnectionResult.Success) {
            return AdapterSelfTestExecution.Outcome.FAILED
        }
        val frame = withTimeoutOrNull(readTimeoutMillis) { transport.frames.firstOrNull() }
        return if (frame != null) AdapterSelfTestExecution.Outcome.SUCCESS
        else AdapterSelfTestExecution.Outcome.FAILED
    }

    companion object {
        private val DEFAULT_LOOPBACK_PAYLOAD = byteArrayOf(0x02, 0x01, 0x3E, 0x00)
        private const val DEFAULT_READ_TIMEOUT_MS: Long = 750L
    }
}
