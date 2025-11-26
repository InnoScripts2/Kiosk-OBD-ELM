package com.selfservice.kiosk.mode.ui.metrics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.selfservice.kiosk.mode.ui.components.MetricCard
import com.selfservice.kiosk.mode.ui.components.MetricStatus
import kotlin.math.roundToInt

@Composable
fun FuelUsageMetricCard(
    usageRate: Float,
    isConnected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val usageRateRounded = usageRate.roundToInt()
    
    // Determine status: high consumption is a warning
    val status = when {
        !isConnected -> MetricStatus.DISCONNECTED
        usageRate > 10 -> MetricStatus.WARNING // Over 10% per hour is high
        usageRate > 15 -> MetricStatus.ERROR   // Over 15% per hour is very high
        else -> MetricStatus.GOOD
    }
    
    MetricCard(
        title = "FUEL USAGE",
        value = "$usageRateRounded",
        unit = "%/hr",
        status = status,
        isConnected = isConnected,
        modifier = modifier
    )
}