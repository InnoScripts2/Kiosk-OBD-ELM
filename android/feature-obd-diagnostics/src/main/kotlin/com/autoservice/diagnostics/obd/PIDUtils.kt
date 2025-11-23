package com.autoservice.diagnostics.obd

object PIDUtils {

    fun formatRequest(pid: PID): String {
        val mode = normalizeMode(pid.mode.code)
        val pidCode = normalizePid(pid.pid)
        return mode + pidCode
    }

    fun extractPayload(pid: PID, response: String): ByteArray {
        val tokens = tokenize(response)
        require(tokens.isNotEmpty()) { "Empty OBD response" }
        val expectedMode = expectedResponseMode(pid.mode)
        val expectedPid = normalizePid(pid.pid)
        val pairIndex = findModePidIndex(tokens, expectedMode, expectedPid)
            ?: error("Response missing ${pid.mode.code}/${expectedPid}: $response")
        val payloadTokens = tokens.drop(pairIndex + 2)
        require(payloadTokens.isNotEmpty()) { "No payload for PID $expectedPid" }
        return payloadTokens.map { token -> token.toInt(16).toByte() }.toByteArray()
    }

    private fun tokenize(response: String): List<String> {
        if (response.isBlank()) return emptyList()
        val sanitized = response
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('>', ' ')
            .trim()
        if (sanitized.isEmpty()) return emptyList()
        val rawTokens = sanitized.split(Regex("\\s+"))
        val result = mutableListOf<String>()
        for (token in rawTokens) {
            if (token.isBlank()) continue
            val normalized = token.uppercase()
            if (normalized.length == 2 && normalized.all(HEX_CHARS::contains)) {
                result += normalized
            } else if (normalized.length > 2 && normalized.all(HEX_CHARS::contains) && normalized.length % 2 == 0) {
                normalized.chunked(2).forEach { chunk -> result += chunk }
            }
        }
        return result
    }

    private fun findModePidIndex(tokens: List<String>, mode: String, pid: String): Int? {
        for (index in 0 until tokens.size - 1) {
            if (tokens[index] == mode && tokens[index + 1] == pid) {
                return index
            }
        }
        return null
    }

    private fun normalizePid(pid: String): String {
        val trimmed = pid.trim()
        if (trimmed.isEmpty()) error("PID code is empty")
        val withoutPrefix = trimmed.removePrefix("0x").removePrefix("0X")
        return withoutPrefix.uppercase().padStart(2, '0')
    }

    private fun normalizeMode(code: String): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) error("Mode code is empty")
        val withoutPrefix = trimmed.removePrefix("0x").removePrefix("0X")
        return withoutPrefix.uppercase().padStart(2, '0')
    }

    private fun expectedResponseMode(mode: ObdMode): String {
        val request = normalizeMode(mode.code)
        val value = request.toInt(16)
        val response = (value + 0x40) and 0xFF
        return response.toString(16).uppercase().padStart(2, '0')
    }

    private val HEX_CHARS = setOf('0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F')
}
