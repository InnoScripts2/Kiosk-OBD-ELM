package com.selfservice.feature.reports

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsReportHtmlFormatterTest {

    private val mapper = createReportMapper()
    private val formatter = DiagnosticsReportHtmlFormatter(mapper)

    @Test
    fun `should render diagnostics report as styled html`() {
        val input = sampleReportInput()

        val html = formatter.format(input)

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("Диагностический отчёт"))
        assertTrue(html.contains("session-001"))
        assertTrue(html.contains("Toyota Camry 2020"))
        assertTrue(html.contains("VIN: <strong>JT1234567890VIN"))
        assertTrue(html.contains("Телефон: +7 (900) 123-45-67"))
        assertTrue(html.contains("Email: owner@example.com"))
        assertTrue(html.contains("Метрик в отчёте: <strong>3"))
        assertTrue(html.contains("class=\"tag tag-critical\""))
        assertTrue(html.contains("class=\"tag tag-warning\""))
        assertTrue(html.contains("class=\"tag tag-unknown\""))
        assertTrue(html.contains("Порог ≥ 110.00 °C"))
        assertTrue(html.contains("Остановите автомобиль"))
        assertTrue(html.contains("Работа в норме без отклонений."))

        // HTML escaping
        assertTrue(html.contains("&lt;RPM&gt;"))
        assertFalse(html.contains("<RPM>"))
    }
}