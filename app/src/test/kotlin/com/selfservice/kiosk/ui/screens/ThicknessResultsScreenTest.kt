package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.Measurement
import com.selfservice.kiosk.ui.state.MeasurementStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ThicknessResultsScreenTest {

    @Test
    fun calculateSummary_countsStatusesAndAverage() {
        val measurements = listOf(
            Measurement(zone = "Z1", value = 120f, status = MeasurementStatus.Normal),
            Measurement(zone = "Z2", value = 90f, status = MeasurementStatus.Warning),
            Measurement(zone = "Z3", value = 50f, status = MeasurementStatus.Critical),
            Measurement(zone = "Z4", value = 0f, status = MeasurementStatus.Invalid)
        )

        val summary = calculateThicknessSummary(measurements, totalPoints = 60)

        assertEquals(60, summary.totalPoints)
        assertEquals(4, summary.completedPoints)
        assertEquals(1, summary.warningCount)
        assertEquals(1, summary.criticalCount)
        assertEquals(1, summary.invalidCount)
        assertEquals(3, summary.deviationCount)
        assertEquals(1, summary.normalCount)
        assertEquals(65f, summary.average)
    }

    @Test
    fun formatAverageThickness_handlesZeroAndPositive() {
        assertEquals("—", formatAverageThickness(0f))
        assertEquals("—", formatAverageThickness(-10f))
        assertEquals("123 μm", formatAverageThickness(122.6f))
    }
}
