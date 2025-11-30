package com.selfservice.obd.core.dtc

/**
 * Lightweight provider that exposes manufacturer-specific overrides embedded into the canonical
 * DTC catalog. Used as a fallback when the platform database is unavailable.
 */
class BrandOverrideDtcProvider {

    fun find(manufacturer: String, code: String): ObdDtcDefinition? {
        val override = DtcCatalog.findBrandOverride(manufacturer, code) ?: return null
        return override.toObdDefinition()
    }

    fun findAll(code: String): List<ObdDtcDefinition> {
        return DtcCatalog.findBrandOverrides(code).map { it.toObdDefinition() }
    }

    fun list(manufacturer: String): List<ObdDtcDefinition> {
        return DtcCatalog.listBrandOverrides(manufacturer).map { it.toObdDefinition() }
    }

    fun manufacturers(): List<String> = DtcCatalog.listBrands()

    fun size(): Int = manufacturers().sumOf { DtcCatalog.countBrandOverrides(it) }

    private fun DtcBrandOverride.toObdDefinition(): ObdDtcDefinition {
        val canonical = DtcCatalog.find(code)
        val overrideNote = buildString {
            append("Производитель: ")
            append(brand)
            if (!source.isNullOrBlank()) {
                append(" | Источник: ")
                append(source)
            }
        }
        val mergedNotes = mergeNotes(canonical?.notes, overrideNote)
        if (canonical != null) {
            val mergedSources = (canonical.sources + listOfNotNull(source)).distinct()
            val mergedBrands = (canonical.brandOverrides + brand).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            return canonical.copy(
                label = description,
                notes = mergedNotes,
                sources = mergedSources,
                brandOverrides = mergedBrands
            )
        }
        return ObdDtcDefinition(
            code = code,
            system = system,
            label = description,
            notes = mergedNotes,
            status = ObdDtcDefinition.Availability.UNKNOWN,
            sources = listOfNotNull(source),
            details = null,
            brandOverrides = listOf(brand)
        )
    }

    private fun mergeNotes(existing: String?, overrideNote: String?): String? {
        val parts = listOfNotNull(overrideNote?.takeIf { it.isNotBlank() }, existing?.takeIf { it.isNotBlank() })
        return if (parts.isEmpty()) null else parts.joinToString(separator = " | ")
    }
}
