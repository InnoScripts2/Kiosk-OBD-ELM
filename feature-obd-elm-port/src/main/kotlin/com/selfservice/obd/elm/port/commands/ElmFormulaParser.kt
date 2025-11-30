package com.selfservice.obd.elm.port.commands

import com.selfservice.obd.core.protocol.PidFormulaEvaluator

/**
 * Adapter that reuses the canonical [PidFormulaEvaluator] from feature-obd-core while
 * stripping ELM-specific header bytes from raw frames.
 */
internal object ElmFormulaParser {

    fun evaluate(formula: String, buffer: List<Int>): Double {
        require(buffer.size >= 2) { "ELM response must contain at least mode and PID bytes" }
        val payload = buffer.drop(2)
            .map { (it and 0xFF).toByte() }
            .toByteArray()
        return PidFormulaEvaluator.evaluate(formula, payload)
    }
}
