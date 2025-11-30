package com.selfservice.feature.reports

import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation

/**
 * Входные данные для генерации диагностического отчёта.
 */
data class DiagnosticsReportInput(
    val sessionId: String,
    val generatedAtMillis: Long,
    val snapshot: DiagnosticsProfileSnapshot,
    val recommendations: List<DiagnosticsRecommendation>,
    val vehicle: DiagnosticsReportVehicle?,
    val customer: DiagnosticsReportCustomer?
)

/**
 * Сведения о транспортном средстве для заголовка отчёта.
 */
data class DiagnosticsReportVehicle(
    val make: String,
    val model: String?,
    val year: Int?,
    val vin: String?
)

/**
 * Контактные данные клиента для блока доставки отчёта.
 */
data class DiagnosticsReportCustomer(
    val phone: String?,
    val email: String?
)

/**
 * Итог отчёта: HTML-версия для предпросмотра и PDF-бинарь для отправки.
 */
data class DiagnosticsReport(
    val html: String,
    val pdfBytes: ByteArray
)
