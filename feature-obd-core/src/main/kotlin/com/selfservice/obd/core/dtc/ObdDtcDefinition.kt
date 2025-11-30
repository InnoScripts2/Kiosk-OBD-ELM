package com.selfservice.obd.core.dtc

/**
 * Describes a canonical diagnostic trouble code entry. Values are normalised
 * to uppercase code identifiers and immutable when exposed to callers.
 */
data class ObdDtcDefinition(
    val code: String,
    val system: System,
    val label: String,
    val notes: String? = null,
    val status: Availability = Availability.UNKNOWN,
    val sources: List<String> = emptyList(),
    val details: Details? = null,
    val brandOverrides: List<String> = emptyList()
) {
    data class Details(
        val preferred: String? = null,
        val base: String? = null,
        val app: String? = null
    )

    enum class Availability(val value: String) {
        BOTH("both"),
        APP_ONLY("app-only"),
        BASE_ONLY("base-only"),
        UNKNOWN("unknown");

        companion object {
            fun fromValue(raw: String?): Availability {
                val normalized = raw?.trim()?.lowercase()
                return entries.firstOrNull { it.value == normalized } ?: UNKNOWN
            }
        }
    }

    enum class System(val value: String) {
        POWERTRAIN("powertrain"),
        CHASSIS("chassis"),
        BODY("body"),
        NETWORK("network");

        companion object {
            fun fromValue(raw: String): System {
                val normalized = raw.trim().lowercase()
                return entries.firstOrNull { it.value == normalized }
                    ?: error("Unknown DTC system $raw")
            }
        }
    }
}
