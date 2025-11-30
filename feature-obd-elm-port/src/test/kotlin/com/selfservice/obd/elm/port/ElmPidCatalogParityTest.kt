package com.selfservice.obd.elm.port

import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.core.pid.PidCatalog
import com.selfservice.obd.core.pid.PidConversions
import com.selfservice.obd.core.protocol.PidFormulaEvaluator
import com.selfservice.obd.elm.port.adapter.ElmPidDefinitionAdapter
import com.selfservice.obd.elm.port.enums.ObdModes
import com.selfservice.obd.elm.port.statics.PIDUtils
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ElmPidCatalogParityTest {
    private val ignoredPidPairs = setOf(
        "0x01/0x00",
        "0x01/0x20",
        "0x01/0x40",
        "0x01/0x60",
        "0x01/0x80"
    )

    @Test
    fun `mode 01 formulas stay in sync`() {
        val donorEntries = pidDefinitions(ObdModes.MODE_01)
            .filter { it.formula?.isNotBlank() == true }
            .filterNot { shouldIgnore(it) }

        val missing = mutableListOf<String>()
        val mismatched = mutableListOf<String>()

        donorEntries.forEach { donor ->
            val canonical = PidCatalog.find(donor.mode, donor.pid)
            if (canonical == null) {
                missing += "${donor.mode}/${donor.pid}"
                return@forEach
            }
            val donorFormula = donor.formula
            val canonicalFormula = resolveFormula(canonical)
            if (!formulasEquivalent(donorFormula, canonicalFormula)) {
                mismatched += "${donor.mode}/${donor.pid}: donor=${donorFormula} canonical=${canonicalFormula}"
            }
        }

        assertTrue(missing.isEmpty(), "Missing canonical PID definitions: ${missing.joinToString()}")
        assertTrue(mismatched.isEmpty(), "Formula mismatches detected: ${mismatched.joinToString()}" )
    }

    @Test
    fun `donor pid catalogs are fully represented`() {
        val modesToCheck = listOf(ObdModes.MODE_01, ObdModes.MODE_04, ObdModes.MODE_09)
        val missing = mutableListOf<String>()

        modesToCheck.forEach { mode ->
            pidDefinitions(mode).forEach { donor ->
                if (shouldIgnore(donor)) return@forEach
                val canonical = PidCatalog.find(donor.mode, donor.pid)
                if (canonical == null) {
                    missing += "${donor.mode}/${donor.pid} (${mode.name})"
                }
            }
        }

        assertTrue(missing.isEmpty(), "Canonical catalog is missing entries: ${missing.joinToString()}" )
    }

    @Test
    fun `mode 09 payload lengths stay in sync`() {
        val donorEntries = pidDefinitions(ObdModes.MODE_09)
        val missing = mutableListOf<String>()
        val mismatched = mutableListOf<String>()

        donorEntries.forEach { donor ->
            val canonical = PidCatalog.find(donor.mode, donor.pid)
            if (canonical == null) {
                missing += "${donor.mode}/${donor.pid}"
                return@forEach
            }
            val donorPayload = donor.payloadLengthBytes
            val canonicalPayload = canonical.payloadLengthBytes
            if (donorPayload == null && canonicalPayload == null) return@forEach
            if (donorPayload == canonicalPayload) return@forEach
            mismatched += "${donor.mode}/${donor.pid}: donor=${donorPayload} canonical=${canonicalPayload}"
        }

        assertTrue(missing.isEmpty(), "Mode 09 entries not present in canonical catalog: ${missing.joinToString()}" )
        assertTrue(mismatched.isEmpty(), "Mode 09 payload length mismatches: ${mismatched.joinToString()}" )
    }

    private fun resolveFormula(definition: ObdPidDefinition): String? {
        if (!definition.formula.isNullOrBlank()) return definition.formula
        val conversion = definition.conversion ?: return null
        return PidConversions.find(conversion)?.formula
    }

    private fun normalizeFormula(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val compact = raw.uppercase().replace(Regex("\\s+"), "")
        return canonicalizeFormula(compact) ?: compact
    }

    private fun pidDefinitions(mode: ObdModes): List<ObdPidDefinition> {
        return PIDUtils.getPidList(mode)
            .map { ElmPidDefinitionAdapter.asObdDefinition(it) }
    }

    private fun shouldIgnore(definition: ObdPidDefinition): Boolean {
        val key = "${definition.mode}/${definition.pid}"
        return key in ignoredPidPairs
    }

    private fun formulasEquivalent(leftRaw: String?, rightRaw: String?): Boolean {
        if (leftRaw.isNullOrBlank() || rightRaw.isNullOrBlank()) {
            return leftRaw.isNullOrBlank() && rightRaw.isNullOrBlank()
        }
        val leftNormalized = normalizeFormula(leftRaw)
        val rightNormalized = normalizeFormula(rightRaw)
        if (leftNormalized == rightNormalized) return true
        val payloadLength = requiredPayloadLength(listOf(leftRaw, rightRaw))
        if (payloadLength <= 0) return false
        val samples = payloadSamples(payloadLength)
        return samples.all { payload ->
            val leftValue = runCatching { PidFormulaEvaluator.evaluate(leftRaw, payload) }.getOrNull()
            val rightValue = runCatching { PidFormulaEvaluator.evaluate(rightRaw, payload) }.getOrNull()
            leftValue != null && rightValue != null && abs(leftValue - rightValue) < 1e-6
        }
    }

    private fun requiredPayloadLength(formulas: List<String>): Int {
        var maxIndex = -1
        formulas.forEach { formula ->
            formula.uppercase().forEach { char ->
                val idx = LETTERS.indexOf(char)
                if (idx >= 0 && idx > maxIndex) {
                    maxIndex = idx
                }
            }
        }
        return maxIndex + 1
    }

    private fun payloadSamples(length: Int): List<ByteArray> {
        val first = ByteArray(length) { (it * 17 + 3).toByte() }
        val second = ByteArray(length) { (255 - it * 29).toByte() }
        return listOf(first, second)
    }

    private fun canonicalizeFormula(formula: String): String? = runCatching {
        val tokens = tokenize(formula)
        val rpn = toRpn(tokens)
        rpn.joinToString(separator = " ")
    }.getOrNull()

    private fun tokenize(formula: String): List<String> {
        if (formula.isEmpty()) return emptyList()
        val tokens = mutableListOf<String>()
        var index = 0
        while (index < formula.length) {
            val char = formula[index]
            when {
                char in LETTERS -> {
                    tokens += char.toString()
                    index += 1
                }
                char.isDigit() || char == '.' -> {
                    val start = index
                    index += 1
                    while (index < formula.length && (formula[index].isDigit() || formula[index] == '.')) {
                        index += 1
                    }
                    tokens += formula.substring(start, index)
                }
                char == '-' && index + 1 < formula.length && (formula[index + 1].isDigit() || formula[index + 1] == '.') &&
                    (tokens.isEmpty() || tokens.last() in OPERATORS || tokens.last() == "(") -> {
                    val start = index
                    index += 1
                    while (index < formula.length && (formula[index].isDigit() || formula[index] == '.')) {
                        index += 1
                    }
                    tokens += formula.substring(start, index)
                }
                char == '(' || char == ')' -> {
                    tokens += char.toString()
                    index += 1
                }
                char in setOf('+', '-', '*', '/') -> {
                    tokens += char.toString()
                    index += 1
                }
                else -> throw IllegalArgumentException("Unsupported character '$char' in formula '$formula'")
            }
        }
        return tokens
    }

    private fun toRpn(tokens: List<String>): List<String> {
        if (tokens.isEmpty()) return emptyList()
        val output = mutableListOf<String>()
        val operators = ArrayDeque<String>()
        for (token in tokens) {
            when {
                token in OPERATORS -> {
                    while (operators.isNotEmpty()) {
                        val head = operators.last()
                        if (head in OPERATORS && precedence(head) >= precedence(token)) {
                            output += operators.removeLast()
                        } else {
                            break
                        }
                    }
                    operators.addLast(token)
                }
                token == "(" -> operators.addLast(token)
                token == ")" -> {
                    while (operators.isNotEmpty() && operators.last() != "(") {
                        output += operators.removeLast()
                    }
                    if (operators.isEmpty() || operators.removeLast() != "(") {
                        throw IllegalArgumentException("Mismatched parentheses in formula")
                    }
                }
                else -> output += token
            }
        }
        while (operators.isNotEmpty()) {
            val op = operators.removeLast()
            if (op == "(" || op == ")") {
                throw IllegalArgumentException("Mismatched parentheses in formula")
            }
            output += op
        }
        return output
    }

    private fun precedence(operator: String): Int = when (operator) {
        "*", "/" -> 2
        "+", "-" -> 1
        else -> 0
    }

    private val LETTERS = listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H')
    private val OPERATORS = setOf("+", "-", "*", "/")
}
