package com.selfservice.platform.data.diagnostics.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticsMetricDefinitionJsonParserTest {

    @Test
    fun `parse returns definitions from json`() {
        val json = """
            {
              "metrics": [
                {
                  "id": "voltage",
                  "mode": "01",
                  "pid": "42",
                  "label": "Battery Voltage",
                  "unit": "V",
                  "thresholds": {
                    "warningLow": 11.8,
                    "criticalLow": 11.2
                  },
                  "advice": {
                    "default": "Проверьте систему",
                    "noData": "Нет данных",
                    "messages": {
                      "OK": "Норма",
                      "WARNING_LOW": "Низкое",
                      "CRITICAL_LOW": "Критически низкое"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val definitions = DiagnosticsMetricDefinitionJsonParser.parse(json)

        assertEquals(1, definitions.size)
        val definition = definitions.first()
        assertEquals("voltage", definition.id)
        assertEquals("01", definition.mode)
        assertEquals("42", definition.pid)
        assertEquals("Battery Voltage", definition.label)
        assertEquals("V", definition.unit)
        assertEquals(11.8, definition.thresholds.warningLow)
        assertEquals(11.2, definition.thresholds.criticalLow)
        assertEquals(DiagnosticsMetricCategory.GENERAL, definition.category)
        val advice = definition.advice
        assertEquals("Норма", advice.message(DiagnosticsMetricStatus.OK))
        assertEquals("Низкое", advice.message(DiagnosticsMetricStatus.WARNING_LOW))
        assertEquals("Критически низкое", advice.message(DiagnosticsMetricStatus.CRITICAL_LOW))
        assertEquals("Проверьте систему", advice.message(DiagnosticsMetricStatus.WARNING_HIGH))
    }

    @Test
    fun `parse returns empty list for malformed json`() {
        val json = "{ invalid }"

        val definitions = DiagnosticsMetricDefinitionJsonParser.parse(json)

        assertTrue(definitions.isEmpty())
    }

    @Test
    fun `parse maps category slugs`() {
        val json = """
            {
              "metrics": [
                {
                  "id": "temp",
                  "mode": "01",
                  "pid": "0F",
                  "label": "Intake",
                  "category": "thermal"
                }
              ]
            }
        """.trimIndent()

        val definitions = DiagnosticsMetricDefinitionJsonParser.parse(json)

        assertEquals(1, definitions.size)
        assertEquals(DiagnosticsMetricCategory.THERMAL, definitions.first().category)
    }
}
