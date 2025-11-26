package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.mode.ui.components.MetricCard
import com.selfservice.kiosk.mode.ui.components.MetricStatus

@Composable
fun BaroPressureMetricCard(
    pressure: Int,
    modifier: Modifier = Modifier
) {
    // Determine barometric pressure status
    val status = when {
        pressure < 90 -> MetricStatus.WARNING // Low pressure (stormy weather)
        pressure > 105 -> MetricStatus.WARNING // High pressure (extreme conditions)
        else -> MetricStatus.NORMAL
    }
    
    MetricCard(
        title = "BAROMETRIC PRESSURE",
        value = "$pressure",
        unit = "kPa",
        status = status,
        modifier = modifier
    )
}