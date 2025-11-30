package com.selfservice.feature.reports

/**
 * Генератор отчётов диагностики OBD-II.
 * 
 * Оркестрирует процесс генерации:
 * 1. Валидация входных данных
 * 2. Генерация HTML
 * 3. Конвертация в PDF
 * 4. Сохранение файлов
 * 5. Запись метаданных
 * 
 * Поддерживает DEV/QA/PROD режимы.
 */
class DiagnosticsReportGenerator(
    private val htmlFormatter: DiagnosticsReportHtmlFormatter,
    private val pdfGenerator: PdfGenerator,
    private val storageManager: ReportStorageManager,
    private val devMode: Boolean = false
) {
    
    /**
     * Генерирует полный отчёт (HTML + PDF).
     * 
     * @param input входные данные
     * @param formats запрошенные форматы
     * @return результат генерации
     */
    suspend fun generate(
        input: DiagnosticsReportInput,
        formats: List<ReportFormat> = listOf(ReportFormat.HTML, ReportFormat.PDF)
    ): GenerationResult {
        val startTime = System.currentTimeMillis()
        
        try {
            // Валидация
            validateInput(input)
            
            // Генерация HTML
            val html = if (ReportFormat.HTML in formats) {
                htmlFormatter.format(input)
            } else null
            
            // Генерация PDF
            val pdfBytes = if (ReportFormat.PDF in formats) {
                val htmlForPdf = html ?: htmlFormatter.format(input)
                pdfGenerator.generateFromHtml(htmlForPdf, ReportType.DIAGNOSTICS)
            } else null
            
            // Сохранение файлов
            var htmlPath: String? = null
            var htmlHash: String? = null
            var htmlSizeBytes: Long? = null
            
            if (html != null) {
                val htmlFile = storageManager.saveHtml(input.sessionId, html)
                htmlPath = htmlFile.relativeTo(storageManager.config.baseDir).path
                htmlHash = storageManager.calculateHash(html)
                htmlSizeBytes = htmlFile.length()
            }
            
            var pdfPath: String? = null
            var pdfHash: String? = null
            var pdfSizeBytes: Long? = null
            
            if (pdfBytes != null) {
                val pdfFile = storageManager.savePdf(input.sessionId, pdfBytes)
                pdfPath = pdfFile.relativeTo(storageManager.config.baseDir).path
                pdfHash = storageManager.calculateHash(pdfBytes)
                pdfSizeBytes = pdfFile.length()
            }
            
            // Создание метаданных
            val metadata = ReportMetadata(
                sessionId = input.sessionId,
                reportType = ReportType.DIAGNOSTICS,
                generatedAtMillis = input.generatedAtMillis,
                formats = formats,
                htmlHash = htmlHash,
                pdfHash = pdfHash,
                htmlSizeBytes = htmlSizeBytes,
                pdfSizeBytes = pdfSizeBytes,
                status = ReportStatus.GENERATED,
                htmlPath = htmlPath,
                pdfPath = pdfPath,
                deliveryContacts = input.customer?.let {
                    DeliveryContacts(email = it.email, phone = it.phone)
                }
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
        } catch (e: Exception) {
            // Логирование ошибки
            val issue = ReportIssue(
                sessionId = input.sessionId,
                reportType = ReportType.DIAGNOSTICS,
                timestampMillis = System.currentTimeMillis(),
                issueType = determineIssueType(e),
                description = e.message ?: "Unknown error during report generation",
                stackTrace = e.stackTraceToString(),
                context = mapOf(
                    "vehicleBrand" to (input.vehicle?.make ?: "unknown"),
                    "dtcCodeCount" to input.snapshot.let { "0" }, // Placeholder
                    "devMode" to devMode.toString()
                )
            )
            
            storageManager.saveIssue(issue)
            
            val generationTime = System.currentTimeMillis() - startTime
            
            return GenerationResult(
                success = false,
                metadata = null,
                html = null,
                pdfBytes = null,
                generationTimeMs = generationTime,
                issue = issue
            )
        }
    }
    
    /**
     * Валидация входных данных.
     */
    private fun validateInput(input: DiagnosticsReportInput) {
        require(input.sessionId.isNotBlank()) { "Session ID cannot be blank" }
        // Дополнительная валидация может быть добавлена здесь
    }
    
    /**
     * Определяет тип проблемы по исключению.
     */
    private fun determineIssueType(exception: Exception): IssueType {
        return when {
            exception is OutOfMemoryError -> IssueType.OUT_OF_MEMORY
            exception.message?.contains("storage", ignoreCase = true) == true -> IssueType.OUT_OF_DISK_SPACE
            exception.message?.contains("invalid", ignoreCase = true) == true -> IssueType.INVALID_INPUT_DATA
            exception.message?.contains("PDF", ignoreCase = true) == true -> IssueType.PDF_GENERATION_ERROR
            exception.message?.contains("HTML", ignoreCase = true) == true -> IssueType.HTML_GENERATION_ERROR
            else -> IssueType.OTHER
        }
    }

    companion object {
        /**
         * Создаёт экземпляр генератора с дефолтными настройками.
         */
        fun create(
            locale: java.util.Locale = java.util.Locale("ru", "RU"),
            zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault(),
            storageManager: ReportStorageManager,
            devMode: Boolean = false
        ): DiagnosticsReportGenerator {
            val mapper = DiagnosticsReportViewModelMapper(locale = locale, zoneId = zoneId)
            return DiagnosticsReportGenerator(
                htmlFormatter = DiagnosticsReportHtmlFormatter(mapper),
                pdfGenerator = PdfGenerator(),
                storageManager = storageManager,
                devMode = devMode
            )
        }
    }
}
