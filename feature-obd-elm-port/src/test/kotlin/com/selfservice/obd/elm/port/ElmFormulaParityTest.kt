package com.selfservice.obd.elm.port

import com.selfservice.obd.core.pid.PidCatalog
import com.selfservice.obd.core.protocol.PidFormulaEvaluator
import com.selfservice.obd.elm.port.commands.OBDCommand
import com.selfservice.obd.elm.port.enums.ObdModes
import com.selfservice.obd.elm.port.models.PID
import com.selfservice.obd.elm.port.statics.PIDUtils
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class ElmFormulaParityTest {
    private val supportedFormulaPattern = Regex("^[A-H0-9+\\-*/().]+$")
    private val enumeratedPidKeys = setOf("01:00", "01:01", "01:51")
    private val samplePids by lazy { computeSamplePids() }

    @Test
    fun `ported formulas stay in sync with core evaluator`() {
        check(samplePids.isNotEmpty()) { "No PID definitions with matching formulas were found" }

        samplePids.forEach { pidCode ->
            val donorPid = requireNotNull(PIDUtils.getPid(ObdModes.MODE_01, pidCode)) { "Missing donor PID $pidCode" }
            val definition = requireNotNull(PidCatalog.find("0x01", "0x$pidCode")) { "Missing catalog definition $pidCode" }
            val formula = definition.formula ?: return@forEach
            val bytesRequired = donorPid.bytes.toIntOrNull() ?: return@forEach
            val command = OBDCommand(donorPid)

            repeat(10) {
                val payload = List(bytesRequired) { Random.nextInt(0, 256) }
                val responseMode = (donorPid.mode.toInt(16) + 0x40) and 0xFF
                val elmFrame = buildList {
                    add(responseMode)
                    add(pidCode.toInt(16))
                    addAll(payload)
                }
                val reference = PidFormulaEvaluator.evaluate(formula, payload.map { it.toByte() }.toByteArray())
                val actual = requireNotNull(command.evaluate(elmFrame)?.toDouble()) {
                    "OBDCommand returned null for PID $pidCode"
                }
                assertEquals(reference, actual, 1e-3, "PID $pidCode mismatch")
            }
        }
    }

    private fun computeSamplePids(): List<String> {
        val candidates = PIDUtils.getPidList(ObdModes.MODE_01)
            .asSequence()
            .mapNotNull { pid ->
                if (enumeratedPidKeys.contains("${pid.mode}:${pid.PID}".uppercase(Locale.US))) {
                    return@mapNotNull null
                }
                val donorFormula = pid.formula?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val normalizedDonor = donorFormula.normalizeFormula()
                if (!supportedFormulaPattern.matches(normalizedDonor)) {
                    return@mapNotNull null
                }
                val definition = PidCatalog.find(pid.modeAsCatalogMode(), pid.pidAsCatalogCode()) ?: return@mapNotNull null
                val canonicalFormula = definition.formula?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val normalizedCanonical = canonicalFormula.normalizeFormula()
                if (normalizedDonor != normalizedCanonical) {
                    return@mapNotNull null
                }
                pid.PID.uppercase(Locale.US)
            }
            .distinct()
            .take(32)
            .toList()

        check(candidates.isNotEmpty()) { "No PID entries with matching formulas between donor and catalog" }
        return candidates
    }

    private fun String.normalizeFormula(): String = replace("\\s+".toRegex(), "").uppercase(Locale.US)
}

private fun PID.modeAsCatalogMode(): String = "0x${mode.padStart(2, '0').uppercase(Locale.US)}"

private fun PID.pidAsCatalogCode(): String = "0x${PID.padStart(2, '0').uppercase(Locale.US)}"
