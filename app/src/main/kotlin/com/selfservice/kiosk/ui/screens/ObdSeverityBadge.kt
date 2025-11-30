package com.selfservice.kiosk.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.selfservice.kiosk.ui.state.DtcSeverity
import com.selfservice.platform.ui.foundation.KioskTokens

@Composable
fun ObdSeverityBadge(
    severity: DtcSeverity,
    modifier: Modifier = Modifier
) {
    val spacing = KioskTokens.spacing
    val colorScheme = MaterialTheme.colorScheme
    val descriptor = when (severity) {
        DtcSeverity.Critical -> BadgeDescriptor("Критический", colorScheme.error)
        DtcSeverity.Warning -> BadgeDescriptor("Предупреждение", colorScheme.tertiary)
        DtcSeverity.Info -> BadgeDescriptor("Инфо", colorScheme.primary)
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = descriptor.color.copy(alpha = 0.15f)
    ) {
        Text(
            text = descriptor.label,
            style = MaterialTheme.typography.labelLarge,
            color = descriptor.color,
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)
        )
    }
}

private data class BadgeDescriptor(
    val label: String,
    val color: Color
)
