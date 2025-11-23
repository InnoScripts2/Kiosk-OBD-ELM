/**
 * Ported from https://github.com/bradbarnhill/AndroidOBD (MIT License).
 */
package com.selfservice.obd.elm.port.commands

import com.selfservice.obd.elm.port.models.PID
import com.selfservice.obd.elm.port.statics.Translations

class OBDCommand(pid: PID) : BaseObdCommand(pid.mode.trim() + if (pid.PID.trim().isEmpty()) "" else " " + pid.PID.trim(), pid) {
    private var metricUnits = true

    override val formattedResult: String
        get() = currentPid.calculatedResult.toString() + " " + if (metricUnits || currentPid.imperialFormula == null) currentPid.units else currentPid.imperialUnits

    override val name: String
        get() = currentPid.description

    val callDuration: Long
        get() = currentPid.retrievalTime

    fun setUnitType(metric: Boolean): OBDCommand {
        metricUnits = metric
        return this
    }

    fun evaluate(payload: List<Int>, metric: Boolean = true): Float? {
        setUnitType(metric)
        evaluatePayload(payload)
        return Companion.pid.calculatedResult
    }

    override fun performCalculations() {
        if (rawResult() == NODATA) return

        val expressionText = if (metricUnits || currentPid.imperialFormula == null) currentPid.formula else currentPid.imperialFormula
        val numBytes: Byte = runCatching { currentPid.bytes.toByte() }.getOrDefault(0)

        currentPid.data.clear()
        currentPid.data.addAll(buffer)

        if (Translations.handleSpecialPidEnumerations(currentPid, currentPid.data)) {
            return
        }

        if (expressionText.isNullOrBlank() || numBytes > 4 || numBytes <= 0 || currentPid.data.size <= 2) {
            currentPid.calculatedResultString = currentPid.data.toString()
            return
        }

        runCatching {
            currentPid.calculatedResult = ElmFormulaParser.evaluate(expressionText, currentPid.data).toFloat()
            currentPid.calculatedResultString = currentPid.calculatedResult.toString()
        }.onFailure { error ->
            throw IllegalStateException("Failed to evaluate expression $expressionText for ${currentPid.mode} ${currentPid.PID}", error)
        }
    }

}
