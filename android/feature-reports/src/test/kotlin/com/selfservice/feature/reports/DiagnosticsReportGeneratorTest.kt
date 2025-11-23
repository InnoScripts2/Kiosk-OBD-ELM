package com.selfservice.feature.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.text.Charsets

class DiagnosticsReportGeneratorTest {

    private val mapper = createReportMapper()
    private val fakeRenderer = FakePdfRenderer()
    private val generator = DiagnosticsReportGenerator.create(
        htmlFormatter = DiagnosticsReportHtmlFormatter(mapper),
        pdfGenerator = DiagnosticsReportPdfGenerator(mapper, fakeRenderer)
    )

    @Test
    fun `should produce html and pdf payloads`() {
        val report = generator.generate(sampleReportInput())

        assertTrue(report.html.contains("Диагностический отчёт"))
        assertEquals(FakePdfRenderer.RENDERED_BYTES, report.pdfBytes)
        assertEquals("session-001", fakeRenderer.capturedViewModel?.sessionId)
        val generatedAt = assertNotNull(fakeRenderer.capturedGeneratedAt)
        assertTrue(
            Regex("\\d{2} [\\p{L}]+ \\d{4} \\d{2}:\\d{2}").matches(generatedAt),
            "Unexpected generatedAt format: $generatedAt"
        )
    }

    private class FakePdfRenderer : DiagnosticsReportPdfRenderer {
        var capturedViewModel: ReportViewModel? = null
        var capturedGeneratedAt: String? = null

        override fun render(viewModel: ReportViewModel, generatedAt: String): ByteArray {
            capturedViewModel = viewModel
            capturedGeneratedAt = generatedAt
            return RENDERED_BYTES
        }

        companion object {
            val RENDERED_BYTES: ByteArray = "%PDF-FAKE".toByteArray(Charsets.UTF_8)
        }
    }
}
