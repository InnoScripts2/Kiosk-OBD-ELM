package com.selfservice.feature.reports

/**
 * Собирает HTML и PDF формы диагностического отчёта в единую структуру.
 */
class DiagnosticsReportGenerator private constructor(
    private val htmlFormatter: DiagnosticsReportHtmlFormatter,
    private val pdfGenerator: DiagnosticsReportPdfGenerator
) {

    fun generate(input: DiagnosticsReportInput): DiagnosticsReport {
        val html = htmlFormatter.format(input)
        val pdfBytes = pdfGenerator.generate(input)
        return DiagnosticsReport(html = html, pdfBytes = pdfBytes)
    }

    companion object {
        fun create(
            locale: java.util.Locale = java.util.Locale("ru", "RU"),
            zoneId: java.time.ZoneId = java.time.ZoneId.systemDefault()
        ): DiagnosticsReportGenerator {
            val mapper = DiagnosticsReportViewModelMapper(locale = locale, zoneId = zoneId)
            return DiagnosticsReportGenerator(
                htmlFormatter = DiagnosticsReportHtmlFormatter(mapper),
                pdfGenerator = DiagnosticsReportPdfGenerator(mapper)
            )
        }

        internal fun create(
            htmlFormatter: DiagnosticsReportHtmlFormatter,
            pdfGenerator: DiagnosticsReportPdfGenerator
        ): DiagnosticsReportGenerator {
            return DiagnosticsReportGenerator(
                htmlFormatter = htmlFormatter,
                pdfGenerator = pdfGenerator
            )
        }
    }
}
