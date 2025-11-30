package com.selfservice.kiosk.ui.screens

import com.selfservice.kiosk.ui.state.DeviceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThicknessInstructionsScreenTest {

    @Test
    fun buildInstructionMeta_containsStatusAndPoints() {
        val meta = buildInstructionMeta(DeviceStatus.Ready, totalPoints = 60)
        assertEquals(4, meta.size)
        val statusValue = meta.first { it.label == "Состояние прибора" }.value
        assertEquals("Прибор готов", statusValue)
        val pointsValue = meta.first { it.label == "Последовательность" }.value
        assertEquals("60 зон", pointsValue)
        val supporting = meta.first { it.label == "Устройство" }.supporting
        assertTrue(supporting.contains("Автоподключение"))
    }

    @Test
    fun buildInstructionSteps_returnsOrderedContent() {
        val steps = buildInstructionSteps()
        assertEquals(4, steps.size)
        assertEquals("Подготовьте кузов", steps.first().title)
        assertEquals("Отмечайте зоны", steps.last().title)
        assertTrue(steps.any { it.highlight?.contains("60 точек") == true })
    }

    @Test
    fun buildInstructionTips_includesDoAndDont() {
        val tips = buildInstructionTips()
        assertTrue(tips.any { it.kind == InstructionTipKind.Do })
        assertTrue(tips.any { it.kind == InstructionTipKind.Dont })
    }

    @Test
    fun buildZoneFocus_mentionsTotalPoints() {
        val zones = buildZoneFocus(totalPoints = 64)
        val summary = zones.last().description
        assertTrue(summary.contains("64"))
    }
}
