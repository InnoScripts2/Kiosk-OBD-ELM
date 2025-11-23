package com.selfservice.feature.reports

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.text.Charsets

class DiagnosticsReportPdfGeneratorTest {

    private val mapper = createReportMapper()
    private val fakeRenderer = CapturingRenderer()
    private val generator = DiagnosticsReportPdfGenerator(mapper, fakeRenderer)

    @Test
    fun `should produce pdf with metadata`() {
        val pdfBytes = generator.generate(sampleReportInput())

        assertTrue(pdfBytes.isNotEmpty())
        assertTrue(pdfBytes.size >= 4)
        assertEquals(CapturingRenderer.RENDERED, pdfBytes)
        val header = String(pdfBytes, 0, 4, Charsets.US_ASCII)
        assertEquals("%PDF", header)
    }
    
    @Test
    fun `should map snapshot to renderer view model`() {
        val bytes = generator.generate(sampleReportInput())
        
        assertEquals(CapturingRenderer.RENDERED, bytes)
        val viewModel = assertNotNull(fakeRenderer.capturedViewModel)
        assertEquals("session-001", viewModel.sessionId)
        assertEquals(3, viewModel.metrics.size)
        val generatedAt = assertNotNull(fakeRenderer.capturedGeneratedAt)
        assertTrue(
            Regex("\\d{2} [\\p{L}]+ \\d{4} \\d{2}:\\d{2}").matches(generatedAt),
            "Unexpected generatedAt format: $generatedAt"
        )
    }

    private class CapturingRenderer : DiagnosticsReportPdfRenderer {
        var capturedViewModel: ReportViewModel? = null
        var capturedGeneratedAt: String? = null

        override fun render(viewModel: ReportViewModel, generatedAt: String): ByteArray {
            capturedViewModel = viewModel
            capturedGeneratedAt = generatedAt
            return RENDERED
        }

        companion object {
            val RENDERED: ByteArray = "%PDF-RENDERER".toByteArray(Charsets.UTF_8)
        }
    }
}
