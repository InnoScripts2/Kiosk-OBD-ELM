package com.selfservice.obd.core.enums

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты для ObdModes
 */
class ObdModesTest {

    @Test
    fun `test mode values`() {
        assertEquals('1', ObdModes.MODE_01.value)
        assertEquals('3', ObdModes.MODE_03.value)
        assertEquals('4', ObdModes.MODE_04.value)
        assertEquals('9', ObdModes.MODE_09.value)
    }

    @Test
    fun `test intValue conversion`() {
        assertEquals(1, ObdModes.MODE_01.intValue)
        assertEquals(3, ObdModes.MODE_03.intValue)
        assertEquals(4, ObdModes.MODE_04.intValue)
        assertEquals(9, ObdModes.MODE_09.intValue)
        assertEquals(10, ObdModes.MODE_0A.intValue)
    }

    @Test
    fun `test toString`() {
        assertEquals("1", ObdModes.MODE_01.toString())
        assertEquals("3", ObdModes.MODE_03.toString())
        assertEquals("A", ObdModes.MODE_0A.toString())
    }

    @Test
    fun `test all modes exist`() {
        assertNotNull(ObdModes.MODE_01)
        assertNotNull(ObdModes.MODE_02)
        assertNotNull(ObdModes.MODE_03)
        assertNotNull(ObdModes.MODE_04)
        assertNotNull(ObdModes.MODE_05)
        assertNotNull(ObdModes.MODE_06)
        assertNotNull(ObdModes.MODE_07)
        assertNotNull(ObdModes.MODE_08)
        assertNotNull(ObdModes.MODE_09)
        assertNotNull(ObdModes.MODE_0A)
    }
}
