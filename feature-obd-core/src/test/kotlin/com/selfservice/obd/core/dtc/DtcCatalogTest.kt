package com.selfservice.obd.core.dtc

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import org.junit.Test

class DtcCatalogTest {
    private val allowedSystems = setOf(
        ObdDtcDefinition.System.POWERTRAIN,
        ObdDtcDefinition.System.CHASSIS,
        ObdDtcDefinition.System.BODY,
        ObdDtcDefinition.System.NETWORK
    )

    @Test
    fun `definitions expose normalized immutable catalog`() {
        val catalog = DtcCatalog.definitions()
        assertTrue(catalog.isNotEmpty(), "catalog must not be empty")

        val seenCodes = mutableSetOf<String>()
        val systems = mutableSetOf<ObdDtcDefinition.System>()
        for (entry in catalog) {
            assertEquals(5, entry.code.length, "unexpected DTC length for ${entry.code}")
            assertTrue(entry.code[0] in "PCBHU", "unexpected DTC prefix for ${entry.code}")
            assertTrue(
                entry.code.substring(1).all { it.isDigit() || it in 'A'..'F' },
                "unexpected DTC suffix for ${entry.code}"
            )
            assertTrue(entry.system in allowedSystems, "unknown system for ${entry.code}")

            assertTrue(seenCodes.add(entry.code), "duplicate DTC code ${entry.code}")
            systems.add(entry.system)
        }

        val expectedSystems = allowedSystems.map { it.value }.sorted()
        val actualSystems = systems.map { it.value }.sorted()
        assertEquals(expectedSystems, actualSystems, "catalog must expose all supported systems")
    }

    @Test
    fun `find handles case and prefix while returning defensive copy`() {
        val lower = DtcCatalog.find("p0420")
        assertNotNull(lower, "lowercase lookup should succeed")

        val canonical = DtcCatalog.find("P0420")
        assertNotNull(canonical, "canonical lookup should succeed")
        val withPrefix = DtcCatalog.find("0xP0420")
        assertNotNull(withPrefix, "prefixed lookup should succeed")
        assertEquals(canonical, withPrefix, "lookup should normalise optional prefix")

        val fresh = DtcCatalog.find("P0420")
        assertNotNull(fresh, "subsequent lookup should succeed")
        assertNotSame(canonical, fresh, "lookup must return defensive copy")

        val chassis = DtcCatalog.find("c0035")
        assertNotNull(chassis, "chassis lookup should succeed")
        assertEquals(ObdDtcDefinition.System.CHASSIS, chassis.system)
    }

    @Test
    fun `listSystems returns sorted defensive copy`() {
        val systems = DtcCatalog.listSystems()
        assertTrue(systems.isNotEmpty(), "expected non-empty systems list")

        val expected = allowedSystems.map { it.value }.sorted()
        val actual = systems.map { it.value }
        assertEquals(expected, actual, "systems must be sorted alphabetically")

        val mutated = DtcCatalog.listSystems().toMutableList()
        mutated.removeAt(mutated.lastIndex)
        val reloaded = DtcCatalog.listSystems().map { it.value }
        assertEquals(expected, reloaded, "systems list must be defensive copy")
    }

    @Test
    fun `listBySystem returns sorted defensive copy`() {
        val chassisList = DtcCatalog.listBySystem(ObdDtcDefinition.System.CHASSIS)
        assertTrue(chassisList.isNotEmpty(), "expected chassis entries")

        val sortedCodes = chassisList.map { it.code }
        val expectedCodes = sortedCodes.sorted()
        assertEquals(expectedCodes, sortedCodes, "codes must be returned in sorted order")

        val originalLabel = chassisList.first().label
        val mutatedList = DtcCatalog.listBySystem(ObdDtcDefinition.System.CHASSIS).toMutableList()
        mutatedList[0] = mutatedList[0].copy(label = "mutated")
        val refreshedLabel = DtcCatalog.listBySystem(ObdDtcDefinition.System.CHASSIS).first().label
        assertEquals(originalLabel, refreshedLabel, "listBySystem must return defensive copies")

        val networkEntries = DtcCatalog.listBySystem(ObdDtcDefinition.System.NETWORK)
        assertTrue(networkEntries.isNotEmpty(), "network catalog should contain entries")
    }

    @Test
    fun `definitions returns copies`() {
        val first = DtcCatalog.definitions().first()
        val second = DtcCatalog.definitions().first()
        assertNotSame(first, second)
    }

    @Test
    fun `definitions expose metadata from dtc index`() {
        val entry = DtcCatalog.find("B0028")
        assertNotNull(entry, "expected canonical entry for B0028")
        assertEquals(ObdDtcDefinition.Availability.BOTH, entry.status)
        assertTrue(entry.sources.contains("dtcmapping.json"))
        assertNotNull(entry.details, "details must be populated when JSON provides them")
        assertTrue(entry.brandOverrides.isNotEmpty(), "brand overrides list should not be empty")
    }

    @Test
    fun `brand overrides resolve manufacturer specific descriptions`() {
        val bmwOverride = DtcCatalog.findBrandOverride("BMW", "B0016")
        assertNotNull(bmwOverride)
        assertEquals("B0016", bmwOverride.code)
        assertEquals("BMW", bmwOverride.brand)

        val aggregated = DtcCatalog.findBrandOverrides("B0016")
        assertTrue(aggregated.size >= 2, "expected overrides for multiple brands")
        assertTrue(aggregated.any { it.brand.equals("Toyota", ignoreCase = true) })

        val knownBrands = DtcCatalog.listBrands()
        assertTrue(knownBrands.contains("BMW"))
        assertTrue(knownBrands.contains("Generic"))
    }
}