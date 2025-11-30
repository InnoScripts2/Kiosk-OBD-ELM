package com.selfservice.kiosk.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromoHighlightsCatalogTest {
    @Test
    fun `hero highlights cover both services and report`() {
        val highlights = PromoHighlightsCatalog.heroHighlights()
        assertEquals(3, highlights.size)
        assertTrue(highlights.any { it.title.contains("Толщинометрия", ignoreCase = true) })
        assertTrue(highlights.any { it.title.contains("Диагностика", ignoreCase = true) })
        assertTrue(highlights.any { it.title.contains("Отчёт", ignoreCase = true) })
    }

    @Test
    fun `quick stats emphasise duration and payments`() {
        val stats = PromoHighlightsCatalog.quickStats()
        assertTrue(stats.any { it.label == "Время услуги" && it.value.contains("мин") })
        assertTrue(stats.any { it.label == "Оплата" && it.helper.contains("подтверждение") })
    }
}
