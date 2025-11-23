package com.selfservice.obd.core.dtc

import com.selfservice.platform.data.DtcDataLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ManufacturerDtcProviderTest {
    @Test
    fun providesManufacturerSpecificDefinitions() {
        val json = """
            {
              "Toyota": [
                {"code": "P1500", "description": "Hybrid battery voltage high", "source": "toyota.txt"}
              ],
              "Lexus": [
                {"code": "P1500", "description": "HV battery state of charge", "source": "lexus.txt"}
              ]
            }
        """.trimIndent()
        val database = DtcDataLoader.loadDatabase(json)
        val provider = ManufacturerDtcProvider(database)

        val toyota = provider.find("toyota", "p1500")
        assertNotNull(toyota)
        assertEquals("P1500", toyota.code)
        assertEquals(ObdDtcDefinition.System.POWERTRAIN, toyota.system)
        assertEquals("Hybrid battery voltage high", toyota.label)
        assertEquals("Manufacturer: Toyota | Source: toyota.txt", toyota.notes)

        val all = provider.findAll("P1500")
        assertEquals(2, all.size)
        assertEquals(setOf("Hybrid battery voltage high", "HV battery state of charge"), all.map { it.label }.toSet())

        val lexusList = provider.list("lexus")
        assertEquals(1, lexusList.size)
        assertEquals("HV battery state of charge", lexusList.first().label)
    }
}
