package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.diagnostics.ObdDiagnosticsProcessor
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.core.transport.ObdTransport

/** Coordinates PassThru session bootstrap with diagnostic execution. */
class PassThruDiagnosticsCoordinator(
        private val environment: PassThruSessionEnvironment,
        private val diagnosticsExecutor: DiagnosticsExecutor
) {

    constructor(
            environment: PassThruSessionEnvironment,
            processor: ObdDiagnosticsProcessor
    ) : this(
            environment,
            DiagnosticsExecutor { transport, request -> processor.run(transport, request) }
    )

    suspend fun runDiagnostics(
            adapter: ConnectedAdapter,
            request: ObdDiagnosticsRequest
    ): DiagnosticsRun {
        val bootstrap = environment.configFactory.prepareSession(adapter)
        val sessionState = environment.manager.startWithBootstrap(bootstrap)
        val diagnostics = diagnosticsExecutor.execute(bootstrap.transport, request)
        return DiagnosticsRun(sessionState = sessionState, diagnostics = diagnostics)
    }

    fun interface DiagnosticsExecutor {
        suspend fun execute(
                transport: ObdTransport,
                request: ObdDiagnosticsRequest
        ): ObdDiagnosticsResult
    }

    data class DiagnosticsRun(
            val sessionState: ObdSessionState,
            val diagnostics: ObdDiagnosticsResult
    )
}
