package com.selfservice.obd.core.dictionary

import kotlin.math.max

/**
 * Declarative schedule describing how often dictionary synchronisation should run. Encapsulates
 * timing constraints so callers avoid duplicating guard clauses.
 */
data class DictionarySyncSchedule(
        val intervalMillis: Long,
        val initialDelayMillis: Long = intervalMillis,
        val forceOnSchedule: Boolean = false
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be > 0" }
        require(initialDelayMillis >= 0) { "initialDelayMillis must be >= 0" }
        // Guard against callers supplying nonsensical zero interval with zero delay forcing loops.
        require(max(intervalMillis, initialDelayMillis) > 0) { "Schedule must advance over time" }
    }
}
