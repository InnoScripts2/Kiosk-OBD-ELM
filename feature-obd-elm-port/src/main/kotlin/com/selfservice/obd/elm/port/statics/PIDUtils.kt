package com.selfservice.obd.elm.port.statics

import com.selfservice.obd.elm.port.enums.ObdModes
import com.selfservice.obd.elm.port.models.PID
import com.selfservice.obd.elm.port.models.PIDS
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.SortedMap
import java.util.TreeMap

/**
 * Kotlin port of the donor PID lookup helpers.
 */
object PIDUtils {
    private val pidCache: MutableMap<Int, SortedMap<Int, PID>> = mutableMapOf()
    private val json = Json { ignoreUnknownKeys = true }

    @Throws(IOException::class)
    fun getPidList(mode: ObdModes): List<PID> = ArrayList(getPidMap(mode)?.values ?: emptyList())

    @Throws(IOException::class)
    fun getPid(mode: ObdModes, pid: String): PID? = getPidMap(mode)?.get(pid.toInt(radix = 16))?.copy()

    @Throws(IOException::class)
    private fun getPidMap(mode: ObdModes): SortedMap<Int, PID>? {
        pidCache[mode.intValue]?.let { return it }

        val assetName = "pids-mode${mode.intValue}.json"
        val pidList = json.decodeFromString<PIDS>(FileUtils.readFromFile(assetName)).pids
        val pidMap = TreeMap<Int, PID>()

        pidList.forEach { pid ->
            runCatching { pid.PID.toInt(radix = 16) }
                .onSuccess { pidMap[it] = pid }
        }

        check(pidMap.isNotEmpty()) { "Unsupported mode requested: $mode" }

        pidCache[mode.intValue] = pidMap
        return pidMap
    }
}
