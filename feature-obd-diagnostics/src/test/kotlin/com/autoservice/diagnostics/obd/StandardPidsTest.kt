package com.autoservice.diagnostics.obd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardPidsTest {

    @Test
    fun `engine rpm formula uses two bytes`() {
        val bytes = byteArrayOf(0x1A.toByte(), 0xF8.toByte())
        val value = StandardPids.ENGINE_RPM.formula(bytes)
        assertEquals(1726.0, value, 0.1)
    }

    @Test
    fun `vehicle speed formula returns kmh`() {
        val bytes = byteArrayOf(0x32.toByte())
        val value = StandardPids.VEHICLE_SPEED.formula(bytes)
        assertEquals(50.0, value)
    }

    @Test
    fun `throttle position normalizes percent`() {
        val bytes = byteArrayOf(0x80.toByte())
        val value = StandardPids.THROTTLE_POSITION.formula(bytes)
        assertEquals(50.196, value, 0.01)
    }

    @Test
    fun `fuel trim formulas normalize around zero`() {
        val shortBytes = byteArrayOf(0x70)
        val shortValue = StandardPids.SHORT_TERM_FUEL_TRIM_BANK1.formula(shortBytes)
        assertEquals(-12.5, shortValue, 0.1)

        val longBytes = byteArrayOf(0xC0.toByte())
        val longValue = StandardPids.LONG_TERM_FUEL_TRIM_BANK1.formula(longBytes)
        assertEquals(50.0, longValue, 0.1)
    }

    @Test
    fun `pressure temperature and oil formulas decode correctly`() {
        val mapBytes = byteArrayOf(0x64)
        val mapValue = StandardPids.INTAKE_MANIFOLD_PRESSURE.formula(mapBytes)
        assertEquals(100.0, mapValue)

        val catBytes = byteArrayOf(0x13, 0x88.toByte()) // (500 + 40) * 10 = 5400 -> 0x1518, but using 0x1388=5000 -> 460°C
        val catValue = StandardPids.CATALYST_TEMPERATURE_BANK1_SENSOR1.formula(catBytes)
        assertEquals(460.0, catValue, 0.1)

        val oilBytes = byteArrayOf(0x80.toByte())
        val oilValue = StandardPids.ENGINE_OIL_TEMP.formula(oilBytes)
        assertEquals(88.0, oilValue, 0.1)
    }

    @Test
    fun `default set contains new critical pids`() {
        val defaultSet = StandardPids.defaultSet
        assertTrue(defaultSet.contains(StandardPids.SHORT_TERM_FUEL_TRIM_BANK1))
        assertTrue(defaultSet.contains(StandardPids.LONG_TERM_FUEL_TRIM_BANK1))
        assertTrue(defaultSet.contains(StandardPids.INTAKE_MANIFOLD_PRESSURE))
        assertTrue(defaultSet.contains(StandardPids.ENGINE_OIL_TEMP))
        assertTrue(defaultSet.contains(StandardPids.CATALYST_TEMPERATURE_BANK1_SENSOR1))
        assertTrue(defaultSet.contains(StandardPids.VIN))
    }

    @Test
    fun `lookup finds pid regardless of casing`() {
        val pidUpper = StandardPids.lookup("01", "0C")
        val pidLower = StandardPids.lookup("0x01", "0x0c")

        assertEquals(StandardPids.ENGINE_RPM, pidUpper)
        assertEquals(StandardPids.ENGINE_RPM, pidLower)
    }

    @Test
    fun `thresholds return configured boundaries`() {
        val thresholds = StandardPids.thresholds(StandardPids.ENGINE_COOLANT_TEMP)
        assertNotNull(thresholds)

        assertEquals(105.0, thresholds.warningHigh)
        assertEquals(115.0, thresholds.criticalHigh)

        val lookupThresholds = StandardPids.thresholds(ObdMode.CURRENT_DATA, "42")
        assertNotNull(lookupThresholds)
        assertEquals(11.8, lookupThresholds.warningLow)
        assertEquals(11.3, lookupThresholds.criticalLow)
        assertEquals(15.5, lookupThresholds.criticalHigh)
    }

    @Test
    fun `thresholds helper returns null for pid without data`() {
        val thresholds = StandardPids.thresholds(StandardPids.ENGINE_RPM)
        assertNull(thresholds)
    }
}
