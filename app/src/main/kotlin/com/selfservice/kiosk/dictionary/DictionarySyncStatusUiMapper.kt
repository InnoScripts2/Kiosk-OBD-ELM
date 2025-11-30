package com.selfservice.kiosk.dictionary

import com.selfservice.obd.core.dictionary.DictionarySyncState

/**
 * Converts [DictionarySyncState] updates to a minimal UI model so the activity only reacts to
 * layout changes without duplicating state branching logic.
 */
object DictionarySyncStatusUiMapper {

    fun map(state: DictionarySyncState): DictionarySyncStatusUiModel =
            when (state) {
                DictionarySyncState.Idle -> DictionarySyncStatusUiModel.Hidden
                is DictionarySyncState.Running ->
                        DictionarySyncStatusUiModel(
                                isVisible = true,
                                isRunning = true,
                                errorDescription = null
                        )
                is DictionarySyncState.Completed -> DictionarySyncStatusUiModel.Hidden
                is DictionarySyncState.Skipped -> DictionarySyncStatusUiModel.Hidden
                is DictionarySyncState.Failed ->
                        DictionarySyncStatusUiModel(
                                isVisible = true,
                                isRunning = false,
                                errorDescription = state.error.message?.takeIf { it.isNotBlank() }
                                        ?: state.error.javaClass.simpleName
                        )
            }
}

data class DictionarySyncStatusUiModel(
        val isVisible: Boolean,
        val isRunning: Boolean,
        val errorDescription: String?
) {
    companion object {
        val Hidden = DictionarySyncStatusUiModel(isVisible = false, isRunning = false, errorDescription = null)
    }
 }
