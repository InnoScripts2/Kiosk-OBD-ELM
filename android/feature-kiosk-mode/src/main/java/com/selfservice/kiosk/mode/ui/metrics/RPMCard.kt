package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import com.selfservice.kiosk.mode.ui.components.MetricCard

@Composable
fun RPMCard(rpm: Int) {
    MetricCard(
        title = "RPM",
        value = rpm.toString(),
        unit = "rpm"
    )
}
