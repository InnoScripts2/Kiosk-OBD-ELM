package com.selfservice.obd.elm.port

import com.selfservice.obd.core.dtc.DtcCatalog
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.elm.port.adapter.ElmDtcDefinitionAdapter
import com.selfservice.obd.elm.port.statics.DtcUtils
import kotlin.test.Test
import kotlin.test.assertTrue

class ElmDtcCatalogParityTest {

    private val donorEntries by lazy {
        DtcUtils.loadAll().map { ElmDtcDefinitionAdapter.asDefinition(it) }
    }

    @Test
    fun `donor dtc entries exist in canonical catalog`() {
        val missing = mutableListOf<String>()
        donorEntries.forEach { donor ->
            if (DtcCatalog.find(donor.code) == null) {
                missing += donor.code
            }
        }

        assertTrue(missing.isEmpty(), "Canonical DTC catalog is missing donor entries: ${missing.take(25).joinToString()}${suffixIfTrimmed(missing)}")
    }

    @Test
    fun `donor dtc descriptions align with canonical labels`() {
        val mismatched = mutableListOf<String>()
        donorEntries.forEach { donor ->
            val canonical = DtcCatalog.find(donor.code) ?: return@forEach
            if (canonical.system !in DESCRIPTION_SYSTEMS) return@forEach
            if (!descriptionEquivalent(donor.label, canonical.label)) {
                mismatched += "${donor.code}: donor='${donor.label}' canonical='${canonical.label}'"
            }
        }

        assertTrue(mismatched.isEmpty(), "DTC description mismatches detected: ${mismatched.take(25).joinToString()}${suffixIfTrimmed(mismatched)}")
    }

    private fun descriptionEquivalent(donor: String, canonical: String): Boolean {
        if (hasReservedMarker(donor)) return true

        val donorTokens = tokenize(donor)
        val canonicalTokens = tokenize(canonical)
        if (donorTokens.isEmpty()) return true
        if (donorTokens.size <= 2 && donorTokens.any { it.length <= 3 }) return true

        val overlap = donorTokens.count { token -> token in canonicalTokens }
        val ratio = overlap.toDouble() / donorTokens.size
        return ratio >= 0.6 || canonicalTokens.containsAll(donorTokens.take(2))
    }

    private fun tokenize(raw: String): List<String> = preprocess(raw)
        .split(NON_ALPHANUMERIC)
        .map { it.trim(*TRIM_CHARS) }
        .filter { it.isNotEmpty() && it.length > 2 }
        .flatMap { expandToken(it) }
        .distinct()

    private fun preprocess(raw: String): String {
        var normalized = raw.lowercase()
        PHRASE_ALIASES.forEach { (phrase, replacement) ->
            normalized = normalized.replace(phrase, replacement)
        }
        return normalized
    }

    private fun expandToken(token: String): List<String> {
        val expanded = mutableSetOf(token)
        TOKEN_SUBSTRINGS.forEach { substring ->
            if (substring != token && token.contains(substring)) {
                expanded += substring
            }
        }
        TOKEN_SYNONYMS[token]?.let { expanded += it }
        return expanded.toList()
    }

    private fun hasReservedMarker(value: String): Boolean =
        RESERVED_MARKERS.any { marker -> value.contains(marker, ignoreCase = true) }

    private fun suffixIfTrimmed(items: List<String>): String {
        val trimmed = items.size - 25
        return if (trimmed > 0) " …(+${trimmed})" else ""
    }

    companion object {
        private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
        private val TRIM_CHARS = charArrayOf(',', '.', '/', '-', '(', ')', '\'', '"')
        private val TOKEN_SUBSTRINGS = listOf("turbo", "super", "charger")
        private val TOKEN_SYNONYMS = mapOf(
            "turbocharger" to listOf("turbo"),
            "supercharger" to listOf("super"),
            "underspeed" to listOf("low"),
            "overspeed" to listOf("high"),
            "pcm" to listOf("module", "control"),
            "ecm" to listOf("module", "control"),
            "tcm" to listOf("module", "control")
        )
        private val RESERVED_MARKERS = listOf("reserved", "not used")
        private val DESCRIPTION_SYSTEMS = setOf(
            ObdDtcDefinition.System.POWERTRAIN,
            ObdDtcDefinition.System.NETWORK
        )
        private val PHRASE_ALIASES = linkedMapOf(
            "exhaust gas recirculation" to "egr",
            "evaporative emission" to "evap",
            "evaporative emissions" to "evap",
            "secondary air injection" to "air",
            "malfunction indicator lamp" to "mil"
        )
    }
}