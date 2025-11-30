package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.DeviceStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThicknessInstructionsFormatterTest {

    @Test
    fun summaryChipsExposeStatusAndTotals() {
        val chips = buildInstructionSummaryChips(DeviceStatus.Ready, totalPoints = 60)
        assertEquals("Статус", chips.first().label)
        assertEquals("Прибор готов", chips.first().value)
        val pointsChip = chips.first { it.label == "Всего точек" }
        assertEquals("60", pointsChip.value)
    }

    @Test
    fun metaEntriesIncludeDeviceAndStatus() {
        val entries = buildInstructionMeta(DeviceStatus.Connecting, totalPoints = 40)
        val deviceEntry = entries.first { it.label == "Устройство" }
        assertEquals("Толщиномер BLE", deviceEntry.value)
        val statusEntry = entries.first { it.label == "Состояние прибора" }
        assertEquals("Подключаем прибор", statusEntry.value)
    }

    @Test
    fun zoneFocusReferencesTotalPoints() {
        val focus = buildZoneFocus(totalPoints = 64)
        assertTrue(focus.last().description.contains("64"))
    }
}
