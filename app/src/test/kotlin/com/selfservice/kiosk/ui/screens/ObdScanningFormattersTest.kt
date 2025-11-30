package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.AdapterStatus
import kotlin.test.assertEquals
import org.junit.Test

class ObdScanningFormattersTest {

    @Test
    fun `formatEstimatedRemaining handles zero or negative`() {
        assertEquals("≈ 0 с", formatEstimatedRemaining(0))
        assertEquals("≈ 0 с", formatEstimatedRemaining(-30))
    }

    @Test
    fun `formatEstimatedRemaining formats seconds and minutes`() {
        assertEquals("≈ 45 с", formatEstimatedRemaining(45))
        assertEquals("≈ 2 мин 10 с", formatEstimatedRemaining(130))
        assertEquals("≈ 2 мин", formatEstimatedRemaining(120))
    }

    @Test
    fun `formatEstimatedRemaining caps at five hours`() {
        assertEquals("≈ 300 мин", formatEstimatedRemaining(20000))
    }

    @Test
    fun `checklistActiveIndex normalizes progress`() {
        assertEquals(0, checklistActiveIndex(-0.2f))
        assertEquals(0, checklistActiveIndex(0.2f))
        assertEquals(1, checklistActiveIndex(0.5f))
        assertEquals(2, checklistActiveIndex(0.8f))
        assertEquals(2, checklistActiveIndex(1.4f))
    }

    @Test
    fun `obdScanAdapterStatusLabel reflects adapter states`() {
        assertEquals("Отключено", obdScanAdapterStatusLabel(AdapterStatus.Disconnected))
        assertEquals("Подключаемся", obdScanAdapterStatusLabel(AdapterStatus.Connecting))
        assertEquals("Готовимся", obdScanAdapterStatusLabel(AdapterStatus.Connected))
        assertEquals("Сканирование", obdScanAdapterStatusLabel(AdapterStatus.Scanning))
        assertEquals("Готово", obdScanAdapterStatusLabel(AdapterStatus.Complete))
        assertEquals(
            "Ошибка",
            obdScanAdapterStatusLabel(AdapterStatus.Error("Нет ответа"))
        )
    }
}
