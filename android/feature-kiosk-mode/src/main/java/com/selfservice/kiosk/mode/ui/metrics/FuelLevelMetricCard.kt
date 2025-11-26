package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.mode.ui.components.MetricCard
import com.selfservice.kiosk.mode.ui.components.MetricStatus

@Composable
fun FuelLevelMetricCard(
    level: Int,
    modifier: Modifier = Modifier
) {
    MetricCard(
        title = "FUEL",
        value = "$level",
        unit = "%",
        status = when {
            level < 10 -> MetricStatus.ERROR
            level < 25 -> MetricStatus.WARNING
            else -> MetricStatus.NORMAL
        },
        modifier = modifier
    )
}
