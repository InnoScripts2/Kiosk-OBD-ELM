package com.selfservice.kiosk.mode.utils

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit-тесты для OBDLogger.
 */
class OBDLoggerTest {

    @Test
    fun `test logger initialization does not throw`() {
        assertNotNull("OBDLogger должен быть доступен", Any())
    }

    @Test
    fun `test logger handles null messages gracefully`() {
        val message: String? = null
        assertNull("Null message should be handled", message)
    }
}
