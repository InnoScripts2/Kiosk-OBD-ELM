package com.autoservice.diagnostics.obd

import kotlin.coroutines.cancellation.CancellationException

/**
 * Produces lightweight telemetry snapshots without running the full PassThru workflow.
 */
class DiagnosticsPreviewRuntime @JvmOverloads constructor(
    private val connectionManager: ObdConnectionManager = ObdConnectionManager(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    data class Snapshot(
        val generatedAtMillis: Long,
        val protocol: ObdProtocol,
        val samples: List<ObdSample>
    )

    suspend fun capture(
        protocol: ObdProtocol = ObdProtocol.AUTO,
        pids: List<PID> = StandardPids.defaultSet
    ): Snapshot {
        if (!connectionManager.connect(protocol)) {
            error("Failed to connect to OBD adapter using ${protocol.name}")
        }
        val samples = mutableListOf<ObdSample>()
        for (pid in pids) {
            try {
                val sample = connectionManager.read(pid)
                samples += sample
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                samples += ObdSample(
                    pid = pid,
                    value = Double.NaN,
                    unit = pid.unit,
                    rawResponse = error.message ?: "${error.javaClass.simpleName}",
                    timestampMillis = clock()
                )
            }
        }
        return Snapshot(
            generatedAtMillis = clock(),
            protocol = protocol,
            samples = samples
        )
    }
}
