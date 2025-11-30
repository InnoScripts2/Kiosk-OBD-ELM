package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.AdapterStatus
import com.selfservice.kiosk.ui.state.ClearResult
import com.selfservice.kiosk.ui.state.DtcCode
import com.selfservice.kiosk.ui.state.DtcSeverity
import com.selfservice.kiosk.ui.state.ObdFlowState
import com.selfservice.kiosk.ui.state.SystemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdDetailsScreenTest {

    @Test
    fun summaryChipsReflectStatusCountsAndMil() {
        val state = ObdFlowState(
            systemStatus = SystemStatus.HasErrors,
            dtcCodes = listOf(
                DtcCode(
                    code = "P0001",
                    description = "Регулятор требуемого давления",
                    severity = DtcSeverity.Critical,
                    system = "Двигатель"
                ),
                DtcCode(
                    code = "P0002",
                    description = "Регулятор подачи топлива",
                    severity = DtcSeverity.Warning,
                    system = "Топливная система"
                )
            )
        )

        val chips = buildObdDetailsSummaryChips(state)
        val statusChip = chips.first { it.label == "Статус систем" }
        assertEquals("Обнаружены ошибки", statusChip.value)
        val codesChip = chips.first { it.label == "Активные коды" }
        assertTrue(codesChip.value == "2")
        assertTrue(codesChip.supporting.contains("Критические: 1"))
        val milChip = chips.first { it.label == "Статус MIL" }
        assertEquals("Активен", milChip.value)
    }

    @Test
    fun metaEntriesCoverAdapterScanClearAndTimestamp() {
        val state = ObdFlowState(
            adapterStatus = AdapterStatus.Scanning,
            scanDuration = 65_000L,
            clearRequested = false,
            clearResult = ClearResult.Failure("Ошибка адаптера"),
            dtcCodes = listOf(
                DtcCode(
                    code = "P0420",
                    description = "Катализатор",
                    severity = DtcSeverity.Info,
                    system = "Выхлоп",
                    timestamp = 0L
                )
            )
        )

        val meta = buildObdDetailsMeta(state)
        val adapterEntry = meta.first { it.label == "Адаптер" }
        assertEquals("Сканирование", adapterEntry.value)
        val clearEntry = meta.first { it.label == "Очистка DTC" }
        assertEquals("Ошибка", clearEntry.value)
        val lastEntry = meta.first { it.label == "Последний код" }
        assertEquals("—", lastEntry.value)
    }

    @Test
    fun clearStatusLabelReflectsFlags() {
        assertEquals("Выполняем", obdDetailsClearStatusLabel(clearRequested = true, clearResult = null))
        assertEquals("Успешно", obdDetailsClearStatusLabel(clearRequested = false, clearResult = ClearResult.Success))
        assertEquals(
            "Ошибка",
            obdDetailsClearStatusLabel(clearRequested = false, clearResult = ClearResult.Failure("fail"))
        )
        assertEquals("Не выполнялось", obdDetailsClearStatusLabel(clearRequested = false, clearResult = null))
    }
}
