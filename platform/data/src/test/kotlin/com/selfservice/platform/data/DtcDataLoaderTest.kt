package com.selfservice.platform.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DtcDataLoaderTest {
    @Test
    fun parsesAndIndexesManufacturers() {
        val json = """
            {
              "Toyota": [
                {"code": "p1500", "description": "Hybrid battery voltage high", "source": "toyota.txt"},
                {"code": "P1500", "description": "duplicate entry should be ignored", "source": "dup.txt"}
              ],
              "bmw ": [
                {"code": "0xP1472", "description": "Secondary air injection pump", "source": "bmw.csv"}
              ]
            }
        """.trimIndent()

        val database = DtcDataLoader.loadDatabase(json)

        assertEquals(2, database.size())
        assertEquals(listOf("Toyota", "bmw"), database.manufacturers())

        val toyota = database.find("toyota", "P1500")
        assertNotNull(toyota)
        assertEquals("Toyota", toyota.manufacturer)
        assertEquals("P1500", toyota.code)
        assertEquals("Hybrid battery voltage high", toyota.description)
        assertEquals("toyota.txt", toyota.source)

        val missing = database.find("toyota", "P9999")
        assertNull(missing)

        val cross = database.findAll("P1472")
        assertEquals(1, cross.size)
        assertEquals("BMW", cross.first().manufacturer.uppercase())

        val list = database.list("BMW")
        assertEquals(1, list.size)
        assertEquals("P1472", list.first().code)
    }

    @Test
    fun mergePrefersOverlayEntriesAndAddsManufacturers() {
        val base =
                DtcDataLoader.loadDatabase(
                        """
            {
              "Toyota": [
                {"code": "P1500", "description": "Hybrid battery voltage high", "source": "embedded"}
              ]
            }
                        """
                                .trimIndent()
                )
        val overlay =
                DtcDataLoader.loadDatabase(
                        """
            {
              "Toyota": [
                {"code": "P1500", "description": "Updated description", "source": "overlay"}
              ],
              "BMW": [
                {"code": "P1472", "description": "Secondary air injection pump", "source": "overlay"}
              ]
            }
                        """
                                .trimIndent()
                )

        val merged = DtcDataLoader.merge(base, overlay)

        val toyota = requireNotNull(merged.find("Toyota", "P1500"))
        assertEquals("Updated description", toyota.description)
        assertEquals("overlay", toyota.source)

        val bmw = requireNotNull(merged.find("BMW", "P1472"))
        assertEquals("Secondary air injection pump", bmw.description)
        assertEquals("overlay", bmw.source)

        assertEquals(listOf("BMW", "Toyota"), merged.manufacturers())
        assertEquals(2, merged.size())
    }

    @Test
    fun mergeHandlesEmptyDatabases() {
        val empty = DtcDataLoader.loadDatabase("{}")
        val populated =
                DtcDataLoader.loadDatabase(
                        """
            {
              "Toyota": [
                {"code": "P1500", "description": "Hybrid battery voltage high", "source": "embedded"}
              ]
            }
                        """
                                .trimIndent()
                )

        assertTrue(empty.isEmpty())

        val mergedWithEmpty = DtcDataLoader.merge(populated, empty)
        assertEquals(populated.size(), mergedWithEmpty.size())
        val mergedFromEmpty = DtcDataLoader.merge(empty, populated)
        assertEquals(populated.size(), mergedFromEmpty.size())
    }
}
