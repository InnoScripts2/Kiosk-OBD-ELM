package com.selfservice.kiosk.ui.screens

import androidx.annotation.VisibleForTesting
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.selfservice.kiosk.ui.state.DeviceStatus
import com.selfservice.kiosk.ui.state.MeasurementStatus

internal data class MeasurementColors(val background: Color, val content: Color)

@Composable
internal fun measurementColors(status: MeasurementStatus?): MeasurementColors {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        MeasurementStatus.Normal -> MeasurementColors(
            background = scheme.tertiary.copy(alpha = 0.25f),
            content = scheme.onTertiary
        )
        MeasurementStatus.Warning -> MeasurementColors(
            background = scheme.secondary.copy(alpha = 0.2f),
            content = scheme.onSecondary
        )
        MeasurementStatus.Critical -> MeasurementColors(
            background = scheme.error.copy(alpha = 0.25f),
            content = scheme.onError
        )
        MeasurementStatus.Invalid -> MeasurementColors(
            background = scheme.surfaceVariant,
            content = scheme.onSurfaceVariant
        )
        null -> MeasurementColors(
            background = scheme.surface.copy(alpha = 0.7f),
            content = scheme.onSurfaceVariant
        )
    }
}

@VisibleForTesting
internal fun measurementStatusLabel(status: MeasurementStatus?): String = when (status) {
    MeasurementStatus.Normal -> "Норма"
    MeasurementStatus.Warning -> "Внимание"
    MeasurementStatus.Critical -> "Критично"
    MeasurementStatus.Invalid -> "Ошибка"
    null -> "Пусто"
}

@VisibleForTesting
internal fun deviceStatusLabel(status: DeviceStatus): String = when (status) {
    DeviceStatus.Disconnected -> "Нет подключения"
    DeviceStatus.Connecting -> "Подключаем прибор"
    DeviceStatus.Connected -> "Прибор подключён"
    DeviceStatus.Ready -> "Прибор готов"
    DeviceStatus.Measuring -> "Идут измерения"
    is DeviceStatus.Error -> "Ошибка: ${status.message}"
}
