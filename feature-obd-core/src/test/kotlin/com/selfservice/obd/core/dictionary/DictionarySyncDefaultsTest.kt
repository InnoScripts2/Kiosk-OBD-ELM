package com.selfservice.obd.core.dictionary

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DictionarySyncDefaultsTest {

    @Test
    fun scheduleUsesSixHourCadenceWithWarmup() {
        val schedule = DictionarySyncDefaults.schedule

        assertEquals(TimeUnit.HOURS.toMillis(6), schedule.intervalMillis)
        assertEquals(TimeUnit.MINUTES.toMillis(1), schedule.initialDelayMillis)
        assertFalse(schedule.forceOnSchedule)
    }
}
