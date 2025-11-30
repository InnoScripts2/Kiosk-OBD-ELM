package com.selfservice.kiosk.ui.viewmodels

import com.selfservice.kiosk.ui.state.ClearResult
import com.selfservice.kiosk.ui.state.DtcCode
import com.selfservice.kiosk.ui.state.DtcSeverity
import com.selfservice.kiosk.ui.state.SystemStatus
import com.selfservice.kiosk.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObdFlowViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `adding dtc codes updates system status`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = ObdFlowViewModel()

        viewModel.addDtcCodes(
            listOf(
                DtcCode(
                    code = "P0420",
                    description = "Эффективность катализатора ниже порога",
                    severity = DtcSeverity.Warning,
                    system = "Выбросы"
                ),
                DtcCode(
                    code = "P0300",
                    description = "Обнаружены пропуски воспламенения",
                    severity = DtcSeverity.Critical,
                    system = "Двигатель"
                )
            )
        )

        val state = viewModel.state.value
        assertEquals(2, state.dtcCodes.size)
        assertEquals(1, state.warningCount)
        assertEquals(1, state.criticalCount)
        assertEquals(SystemStatus.HasErrors, state.systemStatus)
    }

    @Test
    fun `clear dtc codes resets state`() = runTest(dispatcherRule.testDispatcher) {
        val viewModel = ObdFlowViewModel()
        viewModel.addDtcCodes(
            listOf(
                DtcCode(
                    code = "P0102",
                    description = "Низкий уровень сигнала MAF",
                    severity = DtcSeverity.Warning,
                    system = "Воздухозабор"
                )
            )
        )

        viewModel.clearDtcCodes()
        advanceTimeBy(2_000)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.dtcCodes.isEmpty())
        assertEquals(SystemStatus.AllGood, state.systemStatus)
        assertTrue(state.clearResult is ClearResult.Success)
    }
}
