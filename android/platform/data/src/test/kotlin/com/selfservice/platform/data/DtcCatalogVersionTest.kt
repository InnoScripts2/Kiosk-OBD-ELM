package com.selfservice.platform.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DtcCatalogVersionTest {
    @Test
    fun parsesVersionMetadata() {
        val json = """
            {
              "version": "1.0.1",
              "build_timestamp": "2025-11-23T06:37:40.248683+00:00",
              "generic_entries": 17958,
              "manufacturer_entries": 58217,
              "manufacturers": ["BMW", "Toyota", "Lexus"],
              "sources": {
                "csharp_catalog": "base/OBDII.DTC-main/DTC.cs",
                "dtc_mapping": "base/dtcmapping.json",
                "manufacturer_directories": [
                  "All BMW OBD2 Codes List (7)",
                  "All Toyota OBD2 Codes List (2)"
                ]
              }
            }
        """.trimIndent()

        val version = DtcCatalogVersion.parse(json)

        assertEquals("1.0.1", version.version)
        assertEquals("2025-11-23T06:37:40.248683+00:00", version.buildTimestamp)
        assertEquals(17958, version.genericEntries)
        assertEquals(58217, version.manufacturerEntries)
        assertEquals(listOf("BMW", "Toyota", "Lexus"), version.manufacturers)
        assertEquals("base/OBDII.DTC-main/DTC.cs", version.sources.csharpCatalog)
        assertEquals("base/dtcmapping.json", version.sources.dtcMapping)
        assertEquals(2, version.sources.manufacturerDirectories.size)
    }

    @Test
    fun toLogStringFormatsCorrectly() {
        val version = DtcCatalogVersion(
            version = "1.0.0",
            buildTimestamp = "2025-11-23T06:00:00Z",
            genericEntries = 100,
            manufacturerEntries = 200,
            manufacturers = listOf("BMW", "Toyota"),
            sources = DtcCatalogVersion.CatalogSources(
                csharpCatalog = "test.cs",
                dtcMapping = "test.json",
                manufacturerDirectories = emptyList()
            )
        )

        val logString = version.toLogString()

        assertTrue(logString.contains("v1.0.0"))
        assertTrue(logString.contains("100 generic"))
        assertTrue(logString.contains("200 manufacturer"))
        assertTrue(logString.contains("2 manufacturers"))
    }

    @Test
    fun loadsFromInputStream() {
        val json = """
            {
              "version": "1.0.0",
              "build_timestamp": "2025-11-23T00:00:00Z",
              "generic_entries": 100,
              "manufacturer_entries": 200,
              "manufacturers": ["BMW"],
              "sources": {
                "csharp_catalog": "test.cs",
                "dtc_mapping": "test.json",
                "manufacturer_directories": []
              }
            }
        """.trimIndent()

        val stream = json.byteInputStream(Charsets.UTF_8)
        val version = DtcCatalogVersion.load(stream)

        assertNotNull(version)
        assertEquals("1.0.0", version.version)
    }
}
