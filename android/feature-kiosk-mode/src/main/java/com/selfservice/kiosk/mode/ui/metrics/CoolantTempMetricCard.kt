package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.mode.ui.components.MetricCard
import com.selfservice.kiosk.mode.ui.components.MetricStatus

@Composable
fun CoolantTempMetricCard(
    temp: Int,
    modifier: Modifier = Modifier
) {
    MetricCard(
        title = "COOLANT",
        value = "$temp",
        unit = "°C",
        status = when {
            temp > 110 -> MetricStatus.ERROR
            temp > 95 -> MetricStatus.WARNING
            else -> MetricStatus.NORMAL
        },
        modifier = modifier
    )
}
