package com.selfservice.obd.core.dtc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrandOverrideDtcProviderTest {

    private val provider = BrandOverrideDtcProvider()

    @Test
    fun `find returns manufacturer specific entry`() {
        val bmw = provider.find("BMW", "B0016")
        assertNotNull(bmw)
        assertEquals("B0016", bmw.code)
        assertTrue(bmw.brandOverrides.contains("BMW"))
        assertTrue(bmw.notes?.contains("BMW") == true)
    }

    @Test
    fun `findAll aggregates overrides across brands`() {
        val overrides = provider.findAll("B0016")
        assertTrue(overrides.size >= 2, "expected overrides for multiple brands")
        val brands = overrides.flatMap { it.brandOverrides }
        assertTrue(brands.contains("BMW"))
    }

    @Test
    fun `list enumerates brand catalog`() {
        val entries = provider.list("Toyota")
        assertTrue(entries.isNotEmpty(), "expected Toyota overrides")
        assertTrue(entries.none { it.label.isBlank() })
    }

    @Test
    fun `metadata exposes manufacturers and size`() {
        val manufacturers = provider.manufacturers()
        assertTrue(manufacturers.isNotEmpty(), "expected manufacturers list")
        assertTrue(provider.size() > 0, "expected non-zero catalog size")
    }
}
