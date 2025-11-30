package com.selfservice.obd.core.dtc

/**
 * Manufacturer-specific override for a diagnostic trouble code. Used when brand-provided
 * descriptions diverge from the canonical SAE/ISO definition.
 */
data class DtcBrandOverride(
    val brand: String,
    val code: String,
    val system: ObdDtcDefinition.System,
    val description: String,
    val source: String?
)
