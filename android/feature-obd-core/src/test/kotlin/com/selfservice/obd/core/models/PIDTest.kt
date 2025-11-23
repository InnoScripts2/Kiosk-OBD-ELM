package com.selfservice.obd.core.models

import com.selfservice.obd.core.enums.ObdModes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты для модели PID
 */
class PIDTest {

    @Test
    fun `test default constructor`() {
        val pid = PID()
        assertEquals("01", pid.mode)
        assertEquals("01", pid.PID)
        assertEquals("", pid.bytes)
        assertEquals("", pid.description)
        assertEquals(0f, pid.calculatedResult)
        assertEquals(false, pid.isPersistent)
    }

    @Test
    fun `test constructor with ObdModes`() {
        val pid = PID(ObdModes.MODE_01, "0C")
        assertEquals("01", pid.mode)
        assertEquals("0C", pid.PID)
    }

    @Test
    fun `test setMode`() {
        val pid = PID()
        pid.setMode(ObdModes.MODE_03)
        assertEquals("03", pid.mode)
    }

    @Test
    fun `test setModeAndPID`() {
        val pid = PID()
        pid.setModeAndPID(ObdModes.MODE_01, "0D")
        assertEquals("01", pid.mode)
        assertEquals("0D", pid.PID)
    }

    @Test
    fun `test toString returns description`() {
        val pid = PID(
            mode = "01",
            PID = "0C",
            description = "Engine RPM"
        )
        assertEquals("Engine RPM", pid.toString())
    }

    @Test
    fun `test data fields`() {
        val pid = PID()
        pid.data.add(0x41)
        pid.data.add(0x0C)
        pid.calculatedResult = 2000f
        pid.calculatedResultString = "2000 RPM"
        
        assertEquals(2, pid.data.size)
        assertEquals(0x41, pid.data[0])
        assertEquals(2000f, pid.calculatedResult)
        assertEquals("2000 RPM", pid.calculatedResultString)
    }

    @Test
    fun `test method chaining`() {
        val pid = PID()
        val result = pid.setMode(ObdModes.MODE_01)
        assertNotNull(result)
        assertEquals(pid, result)
    }
}
