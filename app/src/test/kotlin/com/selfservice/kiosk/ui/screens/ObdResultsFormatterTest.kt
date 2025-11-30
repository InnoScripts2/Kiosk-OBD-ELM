package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.AdapterStatus
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

class ObdResultsFormatterTest {

    @Test
    fun `formatScanDuration returns placeholder for zero`() {
        assertEquals("—", formatScanDuration(0))
        assertEquals("—", formatScanDuration(-100))
    }

    @Test
    fun `formatScanDuration formats minutes and seconds`() {
        assertEquals("1 мин 30 с", formatScanDuration(90_000))
        assertEquals("12 с", formatScanDuration(12_000))
    }

    @Test
    fun `formatDtcTimestamp formats date in ru locale`() {
        val zone = ZoneId.of("UTC")
        val instant = ZonedDateTime.parse("2024-01-10T12:34:00Z", DateTimeFormatter.ISO_ZONED_DATE_TIME)
        assertEquals("12:34, 10 янв.", formatDtcTimestamp(instant.toInstant().toEpochMilli(), zone))
    }

    @Test
    fun `formatDtcTimestamp returns placeholder on invalid input`() {
        assertEquals("—", formatDtcTimestamp(-1))
    }

    @Test
    fun `adapterStatusLabel reflects modes`() {
        assertEquals("Нет подключения", obdResultsAdapterStatusLabel(AdapterStatus.Disconnected))
        assertEquals("Подключение", obdResultsAdapterStatusLabel(AdapterStatus.Connecting))
        assertEquals("Готов к сканированию", obdResultsAdapterStatusLabel(AdapterStatus.Connected))
        assertEquals("Сканирование", obdResultsAdapterStatusLabel(AdapterStatus.Scanning))
        assertEquals("Готово", obdResultsAdapterStatusLabel(AdapterStatus.Complete))
        val error = AdapterStatus.Error("Нет ответа")
        assertEquals("Нет ответа", obdResultsAdapterStatusLabel(error))
    }
}
