package com.selfservice.kiosk.dictionary

import com.selfservice.obd.core.dictionary.DictionaryRevisionSnapshot
import com.selfservice.obd.core.dtc.DtcDictionaryRevision
import com.selfservice.obd.core.pid.PidDictionaryRevision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.json.JSONObject

class DictionaryUpdatePayloadParserTest {

    private val parser = DictionaryUpdatePayloadParser()

    @Test
    fun parseReturnsNullWhenNoDefinitionsPresent() {
        val json = JSONObject().apply {
            put("updated_at_ms", 42L)
            put("source", "dev")
            put("pid", JSONObject().put("version", "pid-01"))
            put("dtc", JSONObject().put("version", "dtc-01"))
        }

        val payload = parser.parse(json, fallbackSource = "local", fallbackUpdatedAtMillis = 100L)

        assertNull(payload)
    }

    @Test
    fun parseExtractsDefinitionsAndMetadata() {
        val json = JSONObject(
                """
                {
                  "source": "remote",
                  "updated_at_ms": 1700000100000,
                  "pid": {
                    "version": "pid-2025-11-16",
                    "definitions": [
                      {
                        "mode": "01",
                        "pid": "0C",
                        "label": "Engine RPM",
                        "unit": "rpm",
                        "min": 0,
                        "max": 8000
                      }
                    ]
                  },
                  "dtc": {
                    "version": "dtc-2025-11-16",
                    "definitions": [
                      {
                        "code": "P0123",
                        "system": "powertrain",
                        "label": "Throttle Position"
                      },
                      {
                        "code": "P9999",
                        "system": "invalid",
                        "label": "Ignored"
                      }
                    ]
                  }
                }
                """
        )

        val payload = requireNotNull(parser.parse(json, fallbackSource = "local", fallbackUpdatedAtMillis = 0L))

        assertEquals("remote", payload.source)
        assertEquals("pid-2025-11-16", payload.pidVersion)
        assertEquals("dtc-2025-11-16", payload.dtcVersion)
        assertEquals(1, payload.pidDefinitions.size)
        assertEquals("Engine RPM", payload.pidDefinitions.first().label)
        assertEquals(1, payload.dtcDefinitions.size)
        assertEquals("P0123", payload.dtcDefinitions.first().code)
        assertEquals(1_700_000_100_000L, payload.updatedAtMillis)
    }

    @Test
    fun payloadIsRedundantWhenSnapshotMatchesVersionsAndTimestamp() {
        val snapshot = DictionaryRevisionSnapshot(
                pid = PidDictionaryRevision(
                        source = "remote",
                        versionLabel = "pid-2025-11-16",
                        refreshedAtMillis = 1700000100000,
                        entryCount = 10
                ),
                dtc = DtcDictionaryRevision(
                        source = "remote",
                        versionLabel = "dtc-2025-11-16",
                        refreshedAtMillis = 1700000100000,
                        entryCount = 10
                )
        )
        val json = JSONObject(
                """
                {
                  "pid": {
                    "version": "pid-2025-11-16",
                    "definitions": [
                      { "mode": "01", "pid": "0C", "label": "Engine RPM" }
                    ]
                  }
                }
                """
        )

        val payload = parser.parse(json, fallbackSource = "local", fallbackUpdatedAtMillis = 1700000100000L)

        assertNotNull(payload)
        assertEquals(true, payload.isRedundant(snapshot))
    }
}
