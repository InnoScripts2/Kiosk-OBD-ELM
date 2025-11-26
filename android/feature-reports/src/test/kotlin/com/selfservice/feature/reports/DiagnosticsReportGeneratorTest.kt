package com.selfservice.feature.reports

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticsReportGeneratorTest {

    private val mapper = createReportMapper()

    @Test
    fun `should produce html and pdf payloads`() = runTest {
        val storage = createStorageTestContext(prefix = "diagnostics-report")
        val pdfGenerator = FakePdfGenerator()
        val generator = DiagnosticsReportGenerator(
            htmlFormatter = DiagnosticsReportHtmlFormatter(mapper),
            pdfGenerator = pdfGenerator,
            storageManager = storage.storageManager,
            devMode = false
        )

        try {
            val report = generator.generate(sampleReportInput())

            assertTrue(report.success)
            val html = assertNotNull(report.html)
            assertTrue(html.contains("Диагностический отчёт"))
            assertEquals(FakePdfGenerator.RENDERED_PDF, report.pdfBytes)
            assertEquals(ReportType.DIAGNOSTICS, pdfGenerator.capturedReportType)
            val capturedHtml = assertNotNull(pdfGenerator.capturedHtml)
            assertTrue(capturedHtml.contains("session-001"))

            val metadata = assertNotNull(report.metadata)
            assertEquals("session-001", metadata.sessionId)
            assertEquals(ReportStatus.GENERATED, metadata.status)
            assertTrue(metadata.formats.containsAll(listOf(ReportFormat.HTML, ReportFormat.PDF)))

            val sessionDir = storage.reportsDir.resolve("session-001")
            assertTrue(sessionDir.resolve("report.html").exists())
            assertTrue(sessionDir.resolve("report.pdf").exists())
        } finally {
            storage.cleanup()
        }
    }
}
