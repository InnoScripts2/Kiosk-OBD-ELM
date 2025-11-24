package com.selfservice.feature.reports

import java.io.File

/**
 * Унифицированный экспортер отчётов в HTML/PDF.
 * 
 * Предоставляет единый интерфейс для экспорта отчётов обоих типов
 * (толщиномер и диагностика) в различные форматы.
 * 
 * Особенности:
 * - Централизованная валидация входных данных
 * - Поддержка DEV/QA/PROD режимов
 * - Автоматическая генерация метаданных
 * - Интеграция с ReportStorageManager
 */
class HtmlReportExporter(
    private val thicknessHtmlFormatter: ThicknessReportHtmlFormatter,
    private val diagnosticsHtmlFormatter: DiagnosticsReportHtmlFormatter,
    private val pdfGenerator: PdfGenerator,
    private val devMode: Boolean = false
) {
    
    /**
     * Экспортирует отчёт толщиномера в HTML.
     * 
     * @param input входные данные отчёта
     * @return HTML строка
     * @throws IllegalArgumentException если входные данные невалидны
     */
    fun exportThicknessToHtml(input: ThicknessReportInput): String {
        validateThicknessInput(input)
        return thicknessHtmlFormatter.format(input, devMode)
    }
    
    /**
     * Экспортирует отчёт толщиномера в PDF.
     * 
     * @param input входные данные отчёта
     * @return байты PDF файла
     * @throws IllegalArgumentException если входные данные невалидны
     */
    suspend fun exportThicknessToPdf(input: ThicknessReportInput): ByteArray {
        validateThicknessInput(input)
        val html = thicknessHtmlFormatter.format(input, devMode)
        return pdfGenerator.generateFromHtml(html, ReportType.THICKNESS)
    }
    
    /**
     * Экспортирует отчёт диагностики в HTML.
     * 
     * @param input входные данные отчёта
     * @return HTML строка
     * @throws IllegalArgumentException если входные данные невалидны
     */
    fun exportDiagnosticsToHtml(input: DiagnosticsReportInput): String {
        validateDiagnosticsInput(input)
        return diagnosticsHtmlFormatter.format(input, devMode)
    }
    
    /**
     * Экспортирует отчёт диагностики в PDF.
     * 
     * @param input входные данные отчёта
     * @return байты PDF файла
     * @throws IllegalArgumentException если входные данные невалидны
     */
    suspend fun exportDiagnosticsToPdf(input: DiagnosticsReportInput): ByteArray {
        validateDiagnosticsInput(input)
        val html = diagnosticsHtmlFormatter.format(input, devMode)
        return pdfGenerator.generateFromHtml(html, ReportType.DIAGNOSTICS)
    }
    
    /**
     * Экспортирует отчёт в файл.
     * 
     * @param input входные данные отчёта
     * @param format формат экспорта (HTML или PDF)
     * @param outputFile файл для сохранения
     * @throws IllegalArgumentException если входные данные невалидны
     * @throws java.io.IOException при ошибке записи файла
     */
    suspend fun exportToFile(
        input: Any,
        format: ReportFormat,
        outputFile: File
    ) {
        when (input) {
            is ThicknessReportInput -> {
                when (format) {
                    ReportFormat.HTML -> {
                        val html = exportThicknessToHtml(input)
                        outputFile.writeText(html, Charsets.UTF_8)
                    }
                    ReportFormat.PDF -> {
                        val pdfBytes = exportThicknessToPdf(input)
                        outputFile.writeBytes(pdfBytes)
                    }
                }
            }
            is DiagnosticsReportInput -> {
                when (format) {
                    ReportFormat.HTML -> {
                        val html = exportDiagnosticsToHtml(input)
                        outputFile.writeText(html, Charsets.UTF_8)
                    }
                    ReportFormat.PDF -> {
                        val pdfBytes = exportDiagnosticsToPdf(input)
                        outputFile.writeBytes(pdfBytes)
                    }
                }
            }
            else -> throw IllegalArgumentException("Unsupported report input type: ${input::class.simpleName}")
        }
    }
    
    /**
     * Создаёт полный экспорт отчёта (HTML + PDF) и сохраняет в хранилище.
     * 
     * @param input входные данные отчёта
     * @param storageManager менеджер хранилища
     * @param formats запрошенные форматы
     * @return результат генерации с метаданными
     */
    suspend fun generateAndStore(
        input: Any,
        storageManager: ReportStorageManager,
        formats: List<ReportFormat> = listOf(ReportFormat.HTML, ReportFormat.PDF)
    ): GenerationResult {
        val startTime = System.currentTimeMillis()
        
        val (sessionId, reportType, html, pdfBytes) = when (input) {
            is ThicknessReportInput -> {
                val html = if (ReportFormat.HTML in formats) exportThicknessToHtml(input) else null
                val pdf = if (ReportFormat.PDF in formats) {
                    val htmlForPdf = html ?: exportThicknessToHtml(input)
                    pdfGenerator.generateFromHtml(htmlForPdf, ReportType.THICKNESS)
                } else null
                Tuple4(input.sessionId, ReportType.THICKNESS, html, pdf)
            }
            is DiagnosticsReportInput -> {
                val html = if (ReportFormat.HTML in formats) exportDiagnosticsToHtml(input) else null
                val pdf = if (ReportFormat.PDF in formats) {
                    val htmlForPdf = html ?: exportDiagnosticsToHtml(input)
                    pdfGenerator.generateFromHtml(htmlForPdf, ReportType.DIAGNOSTICS)
                } else null
                Tuple4(input.sessionId, ReportType.DIAGNOSTICS, html, pdf)
            }
            else -> throw IllegalArgumentException("Unsupported report input type: ${input::class.simpleName}")
        }
        
        // Сохранение файлов
        var htmlPath: String? = null
        var htmlHash: String? = null
        var htmlSizeBytes: Long? = null
        
        if (html != null) {
            val htmlFile = storageManager.saveHtml(sessionId, html)
            htmlPath = htmlFile.relativeTo(storageManager.config.baseDir).path
            htmlHash = storageManager.calculateHash(html)
            htmlSizeBytes = htmlFile.length()
        }
        
        var pdfPath: String? = null
        var pdfHash: String? = null
        var pdfSizeBytes: Long? = null
        
        if (pdfBytes != null) {
            val pdfFile = storageManager.savePdf(sessionId, pdfBytes)
            pdfPath = pdfFile.relativeTo(storageManager.config.baseDir).path
            pdfHash = storageManager.calculateHash(pdfBytes)
            pdfSizeBytes = pdfFile.length()
        }
        
        // Создание метаданных
        val generatedAtMillis = when (input) {
            is ThicknessReportInput -> input.generatedAtMillis
            is DiagnosticsReportInput -> input.generatedAtMillis
            else -> System.currentTimeMillis()
        }
        
        val deliveryContacts = when (input) {
            is ThicknessReportInput -> input.customer?.let {
                DeliveryContacts(email = it.email, phone = it.phone)
            }
            is DiagnosticsReportInput -> input.customer?.let {
                DeliveryContacts(email = it.email, phone = it.phone)
            }
            else -> null
        }
        
        val metadata = ReportMetadata(
            sessionId = sessionId,
            reportType = reportType,
            generatedAtMillis = generatedAtMillis,
            formats = formats,
            htmlHash = htmlHash,
            pdfHash = pdfHash,
            htmlSizeBytes = htmlSizeBytes,
            pdfSizeBytes = pdfSizeBytes,
            status = ReportStatus.GENERATED,
            htmlPath = htmlPath,
            pdfPath = pdfPath,
            deliveryContacts = deliveryContacts
        )
        
        // Сохранение метаданных
        storageManager.saveMetadata(metadata)
        
        val generationTime = System.currentTimeMillis() - startTime
        
        return GenerationResult(
            success = true,
            metadata = metadata,
            html = html,
            pdfBytes = pdfBytes,
            generationTimeMs = generationTime,
            issue = null
        )
    }
    
    // Валидация
    
    private fun validateThicknessInput(input: ThicknessReportInput) {
        require(input.sessionId.isNotBlank()) { "Session ID cannot be blank" }
        require(input.measurements.isNotEmpty()) { "Measurements cannot be empty" }
        require(input.vehicleType.isNotBlank()) { "Vehicle type cannot be blank" }
        
        // Проверка на корректность значений измерений
        input.measurements.forEach { measurement ->
            require(measurement.zone.isNotBlank()) { "Measurement zone cannot be blank" }
            require(measurement.value >= 0) { "Measurement value cannot be negative: ${measurement.value}" }
            require(measurement.value <= 2000) { "Measurement value exceeds maximum (2000 µm): ${measurement.value}" }
        }
    }
    
    private fun validateDiagnosticsInput(input: DiagnosticsReportInput) {
        require(input.sessionId.isNotBlank()) { "Session ID cannot be blank" }
        require(input.vehicle.make.isNotBlank()) { "Vehicle make cannot be blank" }
        require(input.snapshot.metrics.isNotEmpty()) { "Diagnostics metrics cannot be empty" }
    }
    
    // Helper tuple class
    private data class Tuple4<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
    
    companion object {
        /**
         * Создаёт экспортер с настройками по умолчанию.
         * 
         * @param devMode включить DEV режим (показывать бейдж [МОК-РЕЖИМ])
         * @return экземпляр HtmlReportExporter
         */
        fun create(devMode: Boolean = false): HtmlReportExporter {
            return HtmlReportExporter(
                thicknessHtmlFormatter = ThicknessReportHtmlFormatter(),
                diagnosticsHtmlFormatter = DiagnosticsReportHtmlFormatter(
                    DiagnosticsReportViewModelMapper(
                        locale = java.util.Locale("ru", "RU"),
                        zoneId = java.time.ZoneId.of("Europe/Moscow")
                    )
                ),
                pdfGenerator = PdfGenerator(),
                devMode = devMode
            )
        }
    }
}
