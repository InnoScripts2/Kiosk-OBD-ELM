package com.selfservice.obd.core.pid

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PidCatalogSchemaTest {

    private val mandatoryMode1Pids = listOf("0x01", "0x04", "0x05", "0x0C", "0x0D", "0x0F", "0x10", "0x11")
    private val mandatoryMode9Pids = listOf("0x00", "0x02", "0x04", "0x0A")

    @Test
    fun `catalog exposes mandatory SAE mode 1 coverage`() {
        val missing = mandatoryMode1Pids.filter { pid -> PidCatalog.find("0x01", pid) == null }
        assertTrue(missing.isEmpty(), "Mandatory Mode 1 PIDs missing: ${missing.joinToString()}")
    }

    @Test
    fun `catalog exposes mandatory mode 9 metadata`() {
        val missing = mandatoryMode9Pids.filter { pid -> PidCatalog.find("0x09", pid) == null }
        assertTrue(missing.isEmpty(), "Mandatory Mode 9 PIDs missing: ${missing.joinToString()}")

        val vin = requireNotNull(PidCatalog.find("0x09", "0x02"))
        assertTrue(vin.sources.isNotEmpty(), "VIN entry must declare sources")
        val payloadLength = vin.payloadLengthBytes
        assertNotNull(payloadLength, "VIN entry must specify payload length")
        assertTrue(payloadLength >= 17, "VIN entry payload must cover full VIN text")
    }

    @Test
    fun `every definition uses canonical metadata`() {
        val definitions = PidCatalog.definitions()
        assertTrue(definitions.isNotEmpty(), "Catalog must not be empty")

        val missingStatus = definitions.filter { it.status.isNullOrBlank() }
        assertTrue(missingStatus.isEmpty(), "Every PID must declare status: ${missingStatus.take(PREVIEW_LIMIT).map { it.pid }}")

        val missingSources = definitions.filter { it.sources.isEmpty() }
        assertTrue(missingSources.isEmpty(), "Every PID must declare at least one source: ${missingSources.take(PREVIEW_LIMIT).map { it.pid }}")

        val unsorted = definitions.zipWithNext().firstOrNull { (a, b) -> !comesBeforeOrSame(a, b) }
        assertTrue(unsorted == null, "Definitions must be sorted by mode/pid, offending pair: $unsorted")
    }

    private fun comesBeforeOrSame(first: ObdPidDefinition, second: ObdPidDefinition): Boolean {
        val modeCompare = compareHex(first.mode, second.mode)
        if (modeCompare > 0) return false
        if (modeCompare < 0) return true
        return compareHex(first.pid, second.pid) <= 0
    }

    private fun compareHex(left: String, right: String): Int =
        hexToInt(left).compareTo(hexToInt(right))

    private fun hexToInt(value: String): Int = value.removePrefix("0x").toInt(radix = 16)

    private companion object {
        private const val PREVIEW_LIMIT = 5
    }
}
