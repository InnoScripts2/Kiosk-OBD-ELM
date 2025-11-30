package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.DeviceStatus
import com.selfservice.kiosk.ui.state.MeasurementStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ThicknessMeasurementScreenTest {

    @Test
    fun measurementStatusLabel_coversAllStates() {
        assertEquals("Норма", measurementStatusLabel(MeasurementStatus.Normal))
        assertEquals("Внимание", measurementStatusLabel(MeasurementStatus.Warning))
        assertEquals("Критично", measurementStatusLabel(MeasurementStatus.Critical))
        assertEquals("Ошибка", measurementStatusLabel(MeasurementStatus.Invalid))
        assertEquals("Пусто", measurementStatusLabel(null))
    }

    @Test
    fun deviceStatusLabel_mapsStatusesToText() {
        assertEquals("Нет подключения", deviceStatusLabel(DeviceStatus.Disconnected))
        assertEquals("Подключаем прибор", deviceStatusLabel(DeviceStatus.Connecting))
        assertEquals("Прибор подключён", deviceStatusLabel(DeviceStatus.Connected))
        assertEquals("Прибор готов", deviceStatusLabel(DeviceStatus.Ready))
        assertEquals("Идут измерения", deviceStatusLabel(DeviceStatus.Measuring))
        assertEquals(
            "Ошибка: battery low",
            deviceStatusLabel(DeviceStatus.Error(message = "battery low"))
        )
    }
}
