package com.selfservice.obd.core.models

import com.selfservice.obd.core.enums.ObdModes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты для модели DTC
 */
class DTCTest {

    @Test
    fun `test default constructor`() {
        val dtc = DTC()
        assertEquals("01", dtc.mode)
        assertEquals(null, dtc.code)
        assertEquals(null, dtc.description)
    }

    @Test
    fun `test data class constructor`() {
        val dtc = DTC(
            mode = "03",
            code = "P0420",
            description = "Catalyst System Efficiency Below Threshold"
        )
        assertEquals("03", dtc.mode)
        assertEquals("P0420", dtc.code)
        assertEquals("Catalyst System Efficiency Below Threshold", dtc.description)
    }

    @Test
    fun `test setMode with ObdModes`() {
        val dtc = DTC()
        dtc.setMode(ObdModes.MODE_03)
        assertEquals("03", dtc.mode)
    }

    @Test
    fun `test modeString`() {
        val dtc = DTC(mode = "03")
        assertEquals("3", dtc.modeString)
        
        val dtc2 = DTC(mode = "01")
        assertEquals("1", dtc2.modeString)
    }

    @Test
    fun `test setMode returns this for chaining`() {
        val dtc = DTC()
        val result = dtc.setMode(ObdModes.MODE_03)
        assertNotNull(result)
        assertEquals(dtc, result)
    }
}
