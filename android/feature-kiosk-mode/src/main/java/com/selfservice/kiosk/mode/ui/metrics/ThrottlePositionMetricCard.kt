package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.mode.ui.components.MetricCard
import com.selfservice.kiosk.mode.ui.components.MetricStatus

@Composable
fun ThrottlePositionMetricCard(
    position: Int,
    modifier: Modifier = Modifier
) {
    MetricCard(
        title = "THROTTLE",
        value = "$position",
        unit = "%",
        status = when {
            position > 90 -> MetricStatus.WARNING
            position > 70 -> MetricStatus.NORMAL
            else -> MetricStatus.NORMAL
        },
        modifier = modifier
    )
}
