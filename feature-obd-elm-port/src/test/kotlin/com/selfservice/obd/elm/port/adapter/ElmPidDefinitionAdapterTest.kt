package com.selfservice.obd.elm.port.adapter

import com.selfservice.obd.elm.port.models.PID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElmPidDefinitionAdapterTest {
    @Test
    fun `converts PID payload to canonical definition`() {
        val pid = PID(
            mode = "1",
            PID = "0c",
            description = "Engine RPM",
            min = "0",
            max = "8000",
            units = "RPM",
            formula = " ((A*256)+B)/4 "
        )

        val definition = ElmPidDefinitionAdapter.asObdDefinition(pid)

        assertEquals("0x01", definition.mode)
        assertEquals("0x0C", definition.pid)
        assertEquals("Engine RPM", definition.label)
        assertEquals(0.0, definition.min)
        assertEquals(8000.0, definition.max)
        assertEquals("rpm", definition.unit)
        assertEquals("((A*256)+B)/4", definition.formula)
    }

    @Test
    fun `falls back to default label and skips blanks`() {
        val pid = PID(
            mode = "0x09",
            PID = "0F",
            description = "",
            units = "  ",
            formula = null,
            min = null,
            max = null
        )

        val definition = ElmPidDefinitionAdapter.asObdDefinition(pid)

        assertEquals("PID 0x0F", definition.label)
        assertNull(definition.unit)
        assertNull(definition.min)
        assertNull(definition.max)
        assertNull(definition.formula)
    }

    @Test
    fun `maps donor units to canonical ones`() {
        val pid = PID(
            mode = "01",
            PID = "0b",
            description = "Intake manifold absolute pressure",
            units = "kPa (absolute)",
            min = "0",
            max = "255"
        )

        val definition = ElmPidDefinitionAdapter.asObdDefinition(pid)

        assertEquals("kPa", definition.unit)
    }
}
