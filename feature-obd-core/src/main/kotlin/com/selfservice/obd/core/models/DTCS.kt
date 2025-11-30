package com.selfservice.obd.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Контейнер для всех DTC.
 * Адаптировано из донорского проекта рес 7 (obd).
 */
@Serializable
data class DTCS (
    @SerialName("dtcs")
    val dtcs: List<DTC> = ArrayList()
)
