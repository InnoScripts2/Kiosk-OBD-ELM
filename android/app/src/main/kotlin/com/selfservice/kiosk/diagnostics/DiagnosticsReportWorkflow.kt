package com.selfservice.kiosk.diagnostics

import com.selfservice.feature.reports.DiagnosticsReport
import com.selfservice.feature.reports.DiagnosticsReportCustomer
import com.selfservice.feature.reports.DiagnosticsReportInput
import com.selfservice.feature.reports.DiagnosticsReportVehicle
import com.selfservice.kiosk.KioskApp
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.protocol.ObdPidSample
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import java.util.UUID

/**
 * Оркестровка полного диагностического цикла с генерацией HTML/PDF отчёта.
 */
class DiagnosticsReportWorkflow(
    private val diagnosticsRunner: suspend (
        ConnectedAdapter,
        ObdDiagnosticsRequest
    ) -> PassThruDiagnosticsCoordinator.DiagnosticsRun,
    private val metricEvaluator: (Collection<ObdPidSample>) -> KioskApp.DiagnosticsMetricEvaluation?,
    private val reportProducer: ReportProducer,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val sessionIdProvider: () -> String = { UUID.randomUUID().toString() }
) {

    data class Metadata(
        val vehicle: DiagnosticsReportVehicle? = null,
        val customer: DiagnosticsReportCustomer? = null
    )

    data class Result(
        val sessionId: String,
        val diagnosticsRun: PassThruDiagnosticsCoordinator.DiagnosticsRun,
        val evaluation: KioskApp.DiagnosticsMetricEvaluation?,
        val reportInput: DiagnosticsReportInput,
        val report: DiagnosticsReport
    )

    fun interface ReportProducer {
        fun generate(input: DiagnosticsReportInput): DiagnosticsReport
    }

    suspend fun run(
        adapter: ConnectedAdapter,
        request: ObdDiagnosticsRequest,
        metadata: Metadata = Metadata()
    ): Result {
        val diagnosticsRun = diagnosticsRunner(adapter, request)
        val generatedAtMillis = timeProvider()
        val evaluation = metricEvaluator(diagnosticsRun.diagnostics.pidSamples)
        val snapshot = evaluation?.snapshot
            ?: DiagnosticsProfileSnapshot(
                timestampMillis = generatedAtMillis,
                metrics = emptyList()
            )
        val recommendations = evaluation?.recommendations ?: emptyList()
        val input = DiagnosticsReportInput(
            sessionId = sessionIdProvider(),
            generatedAtMillis = generatedAtMillis,
            snapshot = snapshot,
            recommendations = recommendations,
            vehicle = metadata.vehicle,
            customer = metadata.customer
        )
        val report = reportProducer.generate(input)
        return Result(
            sessionId = input.sessionId,
            diagnosticsRun = diagnosticsRun,
            evaluation = evaluation,
            reportInput = input,
            report = report
        )
    }
}
