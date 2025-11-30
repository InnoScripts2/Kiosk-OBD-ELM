package com.selfservice.obd.core.dtc

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

class DtcCatalogParityTest {

    private val saePowertrainCodes = (1..999).map { index -> "P%04d".format(Locale.US, index) }

    @Test
    fun `catalog contains every SAE p0001-p0999 entry`() {
        val missing = saePowertrainCodes.filter { code -> DtcCatalog.find(code) == null }
        assertTrue(
            missing.isEmpty(),
            "Canonical catalog is missing SAE P0xxx codes: ${missing.take(PREVIEW_LIMIT).joinToString()}${suffixIfTrimmed(missing)}"
        )
    }

    @Test
    fun `sae range entries map to powertrain system`() {
        val wrongSystem = saePowertrainCodes.mapNotNull { code ->
            val definition = DtcCatalog.find(code) ?: return@mapNotNull null
            if (definition.system != ObdDtcDefinition.System.POWERTRAIN) code else null
        }
        assertTrue(
            wrongSystem.isEmpty(),
            "SAE P0xxx entries must belong to POWERTRAIN system: ${wrongSystem.take(PREVIEW_LIMIT).joinToString()}${suffixIfTrimmed(wrongSystem)}"
        )
    }

    private fun suffixIfTrimmed(items: List<String>): String {
        val trimmed = items.size - PREVIEW_LIMIT
        return if (trimmed > 0) " …(+${trimmed})" else ""
    }

    private companion object {
        private const val PREVIEW_LIMIT = 25
    }
}
