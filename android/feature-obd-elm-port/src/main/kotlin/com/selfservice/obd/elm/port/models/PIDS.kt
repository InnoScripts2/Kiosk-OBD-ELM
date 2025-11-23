package com.selfservice.obd.elm.port.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Container for a list of PID definitions as stored in donor JSON assets.
 */
@Serializable
data class PIDS(
    @SerialName("pids")
    val pids: List<PID> = emptyList()
)
