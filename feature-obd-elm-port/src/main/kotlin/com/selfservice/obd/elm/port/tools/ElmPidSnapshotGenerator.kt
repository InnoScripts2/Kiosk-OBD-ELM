package com.selfservice.obd.elm.port.tools

import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.elm.port.adapter.ElmPidDefinitionAdapter
import com.selfservice.obd.elm.port.enums.ObdModes
import com.selfservice.obd.elm.port.models.PID
import com.selfservice.obd.elm.port.statics.PIDUtils
import java.io.IOException
import java.nio.file.Path
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Generates JSON snapshots of the donor AndroidOBD PID catalog after it has been
 * normalised via [ElmPidDefinitionAdapter]. These snapshots are used in parity tests
 * and as a bridge for ingesting additional transports (ISO-TP/USB) outside of Android.
 */
object ElmPidSnapshotGenerator {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
    }

    private val defaultModes = listOf(ObdModes.MODE_01, ObdModes.MODE_04, ObdModes.MODE_09)

    /**
     * Builds a structured snapshot for the provided [mode].
     */
    @Throws(IOException::class)
    fun snapshot(mode: ObdModes): ElmPidSnapshot {
        val entries = PIDUtils.getPidList(mode)
            .map { toSnapshotEntry(it) }
            .sortedBy { it.pid }
        return ElmPidSnapshot(
            mode = canonicalMode(mode),
            generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            sourceDataset = "AndroidOBD-main",
            entries = entries
        )
    }

    /**
     * Writes snapshots for [modes] (defaults to 01/04/09) into [outputDir].
     * Returns the list of generated files for convenience.
     */
    @Throws(IOException::class)
    fun writeSnapshots(outputDir: Path, modes: List<ObdModes> = defaultModes): List<Path> {
        outputDir.createDirectories()
        return modes.map { mode ->
            val snapshot = snapshot(mode)
            val fileName = "mode-${mode.intValue.toString(16).uppercase().padStart(2, '0')}.json"
            val target = outputDir.resolve(fileName)
            target.parent?.createDirectories()
            target.writeText(json.encodeToString(snapshot))
            target
        }
    }

    private fun toSnapshotEntry(pid: PID): ElmPidSnapshotEntry {
        val definition = ElmPidDefinitionAdapter.asObdDefinition(pid)
        return ElmPidSnapshotEntry(
            mode = definition.mode,
            pid = definition.pid,
            label = definition.label,
            unit = definition.unit,
            conversion = definition.conversion,
            min = definition.min,
            max = definition.max,
            formula = definition.formula,
            notes = definition.notes,
            payloadLengthBytes = definition.payloadLengthBytes ?: pid.payloadLengthBytes()
        )
    }

    private fun PID.payloadLengthBytes(): Int? {
        val normalized = bytes.trim()
        if (normalized.isEmpty()) return null
        return normalized.toIntOrNull()
    }

    private fun canonicalMode(mode: ObdModes): String {
        val suffix = mode.intValue.toString(16).uppercase().padStart(2, '0')
        return "0x$suffix"
    }
}

@Serializable
data class ElmPidSnapshot(
    val mode: String,
    val generatedAt: String,
    val sourceDataset: String,
    val entries: List<ElmPidSnapshotEntry>
)

@Serializable
data class ElmPidSnapshotEntry(
    val mode: String,
    val pid: String,
    val label: String,
    val unit: String? = null,
    val conversion: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val formula: String? = null,
    val notes: String? = null,
    @SerialName("payloadLengthBytes")
    val payloadLengthBytes: Int? = null
)
