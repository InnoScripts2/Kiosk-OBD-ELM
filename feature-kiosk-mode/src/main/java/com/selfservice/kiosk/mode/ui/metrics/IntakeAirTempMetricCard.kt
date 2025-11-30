package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.mode.ui.components.MetricCard
import com.selfservice.kiosk.mode.ui.components.MetricStatus

@Composable
fun IntakeAirTempMetricCard(
    temp: Int,
    modifier: Modifier = Modifier
) {
    MetricCard(
        title = "IAT",
        value = "$temp",
        unit = "°C",
        status = when {
            temp > 60 -> MetricStatus.WARNING
            temp > 80 -> MetricStatus.ERROR
            else -> MetricStatus.NORMAL
        },
        modifier = modifier
    )
}
