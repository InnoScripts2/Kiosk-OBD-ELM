package com.selfservice.obd.core.dtc

import com.selfservice.platform.data.ManufacturerDtcDatabase
import com.selfservice.platform.data.ManufacturerDtcEntry

class ManufacturerDtcProvider(private val database: ManufacturerDtcDatabase) {
    fun find(manufacturer: String, code: String): ObdDtcDefinition? {
        return database.find(manufacturer, code)?.toObdDefinition()
    }

    fun findAll(code: String): List<ObdDtcDefinition> {
        return database.findAll(code).map { it.toObdDefinition() }
    }

    fun list(manufacturer: String): List<ObdDtcDefinition> {
        return database.list(manufacturer).map { it.toObdDefinition() }
    }

    fun manufacturers(): List<String> = database.manufacturers()

    fun size(): Int = database.size()
}

private fun ManufacturerDtcEntry.toObdDefinition(): ObdDtcDefinition {
    val system = when (code.first()) {
        'P' -> ObdDtcDefinition.System.POWERTRAIN
        'B' -> ObdDtcDefinition.System.BODY
        'C' -> ObdDtcDefinition.System.CHASSIS
        'U' -> ObdDtcDefinition.System.NETWORK
        else -> ObdDtcDefinition.System.POWERTRAIN
    }
    val notes = buildString {
        append("Manufacturer: ")
        append(manufacturer)
        if (source.isNotBlank()) {
            append(" | Source: ")
            append(source)
        }
    }
    return ObdDtcDefinition(
            code = code,
            system = system,
            label = description,
            notes = notes
    )
}
