package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.diagnostics.ObdDiagnosticsProcessor
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.session.ConnectedAdapter

/** High-level entry point for running diagnostics against PassThru adapters. */
class PassThruDiagnosticsService(
        private val environmentProvider: () -> PassThruSessionEnvironment,
        private val executorProvider: () -> PassThruDiagnosticsCoordinator.DiagnosticsExecutor
) {

    suspend fun runDiagnostics(
            adapter: ConnectedAdapter,
            request: ObdDiagnosticsRequest
    ): PassThruDiagnosticsCoordinator.DiagnosticsRun {
        val environment = environmentProvider()
        val coordinator = PassThruDiagnosticsCoordinator(environment, executorProvider())
        return coordinator.runDiagnostics(adapter, request)
    }

    companion object {
        fun withProcessor(
                environmentProvider: () -> PassThruSessionEnvironment,
                processorProvider: () -> ObdDiagnosticsProcessor
        ): PassThruDiagnosticsService {
            return PassThruDiagnosticsService(environmentProvider) {
                val processor = processorProvider()
                PassThruDiagnosticsCoordinator.DiagnosticsExecutor { transport, request ->
                    processor.run(transport, request)
                }
            }
        }
    }
}
