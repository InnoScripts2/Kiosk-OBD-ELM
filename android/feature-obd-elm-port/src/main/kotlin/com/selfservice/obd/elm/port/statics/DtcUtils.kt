package com.selfservice.obd.elm.port.statics

import com.selfservice.obd.elm.port.models.ElmDtcContainer
import com.selfservice.obd.elm.port.models.ElmDtcEntry
import kotlinx.serialization.json.Json

/**
 * Utility loader for donor DTC assets.
 */
object DtcUtils {
    private const val ASSET_NAME = "dtc-codes.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<ElmDtcEntry> {
        val payload = FileUtils.readFromFile(ASSET_NAME)
        return json.decodeFromString<ElmDtcContainer>(payload).entries
    }
}