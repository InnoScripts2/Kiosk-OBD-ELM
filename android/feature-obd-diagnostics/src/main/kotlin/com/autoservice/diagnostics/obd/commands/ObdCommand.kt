package com.autoservice.diagnostics.obd.commands

import com.autoservice.diagnostics.obd.PID
import com.autoservice.diagnostics.obd.PIDUtils

abstract class ObdCommand(protected val pid: PID) {

    fun buildFrame(): String = PIDUtils.formatRequest(pid)

    fun parseResult(rawResponse: String): Double {
        val payload = PIDUtils.extractPayload(pid, rawResponse)
        return pid.formula(payload)
    }
}
