package com.selfservice.kiosk.dictionary

import com.selfservice.obd.core.dictionary.DictionaryRevisionSnapshot
import com.selfservice.obd.core.dictionary.DictionaryUpdateBatch
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.pid.ObdPidDefinition

/**
 * Structured representation of a dictionary update payload prior to conversion into
 * [DictionaryUpdateBatch]. Carries version information so providers can decide whether an update
 * should be forwarded to the synchroniser.
 */
data class DictionaryUpdatePayload(
        val pidDefinitions: List<ObdPidDefinition>,
        val dtcDefinitions: List<ObdDtcDefinition>,
        val pidVersion: String?,
        val dtcVersion: String?,
        val source: String?,
        val updatedAtMillis: Long
) {

    fun isRedundant(snapshot: DictionaryRevisionSnapshot): Boolean {
        val pidFresh = !pidVersion.isNullOrBlank() && pidVersion == snapshot.pid.versionLabel
        val dtcFresh = !dtcVersion.isNullOrBlank() && dtcVersion == snapshot.dtc.versionLabel
        val pidMissing = pidVersion.isNullOrBlank()
        val dtcMissing = dtcVersion.isNullOrBlank()
        val isUpToDate = updatedAtMillis <= snapshot.latestRefreshMillis
        return (pidFresh || pidMissing) && (dtcFresh || dtcMissing) && isUpToDate
    }

    fun toBatch(defaultSource: String): DictionaryUpdateBatch {
        val pid = pidDefinitions.takeIf { it.isNotEmpty() }
        val dtc = dtcDefinitions.takeIf { it.isNotEmpty() }
        return DictionaryUpdateBatch(
                pidDefinitions = pid,
                dtcDefinitions = dtc,
                source = source ?: defaultSource,
                pidVersionLabel = pidVersion,
                dtcVersionLabel = dtcVersion
        )
    }

    fun signature(): DictionaryUpdateSignature =
            DictionaryUpdateSignature(pidVersion = pidVersion, dtcVersion = dtcVersion, updatedAtMillis = updatedAtMillis)
}

/** Signature used to avoid re-delivering identical dictionary payloads. */
data class DictionaryUpdateSignature(
        val pidVersion: String?,
        val dtcVersion: String?,
        val updatedAtMillis: Long
)

fun DictionaryUpdateSignature?.sameAs(other: DictionaryUpdateSignature?): Boolean = this == other
