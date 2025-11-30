package com.selfservice.obd.core.dictionary

import java.util.concurrent.TimeUnit

/**
 * Centralises default timing configuration for dictionary refresh and scheduling to keep both
 * policy and runtime cadence consistent across services.
 */
object DictionarySyncDefaults {
    val policy: DictionarySyncPolicy = DictionarySyncPolicy()
    val schedule: DictionarySyncSchedule =
            DictionarySyncSchedule(
                    intervalMillis = TimeUnit.HOURS.toMillis(6),
                    initialDelayMillis = TimeUnit.MINUTES.toMillis(1),
                    forceOnSchedule = false
            )
}
