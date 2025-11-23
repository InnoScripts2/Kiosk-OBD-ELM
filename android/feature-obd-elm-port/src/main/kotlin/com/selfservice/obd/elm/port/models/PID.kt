/**
 * Ported from https://github.com/bradbarnhill/AndroidOBD (MIT License).
 */
package com.selfservice.obd.elm.port.models

import com.selfservice.obd.elm.port.enums.ObdModes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("MemberVisibilityCanBePrivate")
@Serializable
data class PID(
    @SerialName("Mode")
    var mode: String = "01",
    @SerialName("PID")
    var PID: String = "01",
    @SerialName("Bytes")
    var bytes: String = "",
    @SerialName("Description")
    var description: String = "",
    @SerialName("Min")
    var min: String? = null,
    @SerialName("Max")
    var max: String? = null,
    @SerialName("Units")
    var units: String? = null,
    @SerialName("Formula")
    var formula: String? = null,
    @SerialName("ImperialFormula")
    var imperialFormula: String? = null,
    @SerialName("ImperialUnits")
    var imperialUnits: String? = null,
    @SerialName("isPersistent")
    var isPersistent: Boolean = false
) : java.io.Serializable {
    var data: ArrayList<Int> = ArrayList()
    var calculatedResultString: String? = null
    var calculatedResult: Float = 0f
    var retrievalTime: Long = 0

    constructor(mode: ObdModes, pid: String = "") : this() {
        setModeAndPID(mode, pid)
    }

    fun setMode(mode: ObdModes): PID {
        this.mode = "0" + mode.value
        return this
    }

    fun setModeAndPID(mode: ObdModes, pid: String): PID {
        setMode(mode)
        this.PID = pid
        return this
    }

    override fun toString(): String = description
}
