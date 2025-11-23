package com.selfservice.kiosk.dictionary

import com.selfservice.obd.core.dictionary.DictionarySyncResult
import com.selfservice.obd.core.dictionary.DictionarySyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DictionarySyncStatusUiMapperTest {

    @Test
    fun mapReturnsHiddenForIdleState() {
        val model = DictionarySyncStatusUiMapper.map(DictionarySyncState.Idle)

        assertFalse(model.isVisible)
        assertFalse(model.isRunning)
        assertEquals(null, model.errorDescription)
    }

    @Test
    fun mapReturnsRunningWhenStateIsRunning() {
        val state = DictionarySyncState.Running(startedAtMillis = 10L, force = false)

        val model = DictionarySyncStatusUiMapper.map(state)

        assertTrue(model.isVisible)
        assertTrue(model.isRunning)
        assertEquals(null, model.errorDescription)
    }

    @Test
    fun mapReturnsHiddenAfterSuccessfulCompletion() {
        val state = DictionarySyncState.Completed(
                DictionarySyncResult.Performed(
                        pidRevision = com.selfservice.obd.core.pid.PidDictionaryRevision.bootstrap(),
                        dtcRevision = com.selfservice.obd.core.dtc.DtcDictionaryRevision.bootstrap(),
                        attemptedAtMillis = 100L
                )
        )

        val model = DictionarySyncStatusUiMapper.map(state)

        assertEquals(DictionarySyncStatusUiModel.Hidden, model)
    }

    @Test
    fun mapReturnsErrorDescriptionOnFailure() {
        val error = IllegalStateException("")
        val state = DictionarySyncState.Failed(error)

        val model = DictionarySyncStatusUiMapper.map(state)

        assertTrue(model.isVisible)
        assertFalse(model.isRunning)
        assertEquals("IllegalStateException", model.errorDescription)
    }
}
