package com.selfservice.obd.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Контейнер для всех PID.
 * Адаптировано из донорского проекта рес 7 (obd).
 */
@Serializable
data class PIDS (
    @SerialName("pids")
    val pids: List<PID> = ArrayList()
)
