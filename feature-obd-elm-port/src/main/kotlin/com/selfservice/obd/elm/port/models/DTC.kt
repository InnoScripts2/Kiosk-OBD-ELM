package com.selfservice.obd.elm.port.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single DTC entry in the AndroidOBD donor catalog.
 */
@Serializable
data class ElmDtcEntry(
    @SerialName("code")
    val code: String,
    @SerialName("description")
    val description: String
)

/**
 * Container used by the donor JSON asset.
 */
@Serializable
data class ElmDtcContainer(
    @SerialName("dtcs")
    val entries: List<ElmDtcEntry> = emptyList()
)