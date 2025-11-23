package com.selfservice.obd.elm.port.statics

import com.selfservice.obd.elm.port.models.PID
import java.util.BitSet

/**
 * Minimal translation helpers for enumerated PID responses. The original strings referenced
 * Android resources; here we provide plain English fallbacks to keep the logic testable.
 */
object Translations {
    private const val HEX_RADIX = 16

    fun handleSpecialPidEnumerations(pid: PID, buffer: ArrayList<Int>): Boolean {
        return when {
            pid.mode == "01" && pid.PID == "00" -> {
                mode1Pid00Translation(pid, buffer)
                true
            }
            pid.mode == "01" && pid.PID == "01" -> {
                mode1Pid01Translation(pid, buffer)
                true
            }
            pid.mode == "01" && pid.PID == "51" -> {
                mode1Pid51Translation(pid, buffer)
                true
            }
            else -> false
        }
    }

    private fun mode1Pid00Translation(pid: PID, buffer: ArrayList<Int>) {
        if (buffer.size != 6) return
        val hexString = buildString {
            buffer.subList(2, buffer.size).forEach { value ->
                append(value.toString(HEX_RADIX).padStart(2, '0'))
            }
        }
        pid.calculatedResult = hexString.toLong(HEX_RADIX).toFloat()
        pid.calculatedResultString = hexString
    }

    private fun mode1Pid01Translation(pid: PID, buffer: ArrayList<Int>) {
        if (buffer.size <= 2) return
        val mil = buffer[2]
        val onOff = if (mil and 0x80 == 0x80) "ON" else "OFF"
        pid.calculatedResultString = "MIL=$onOff DTCs=${mil and 0x7F}"
    }

    private fun mode1Pid51Translation(pid: PID, buffer: ArrayList<Int>) {
        val translation = listOf(
            "Not Available",
            "Gasoline",
            "Methanol",
            "Ethanol",
            "Diesel",
            "LPG",
            "CNG",
            "Propane",
            "Electric",
            "Bifuel Gasoline",
            "Bifuel Methanol",
            "Bifuel Ethanol",
            "Bifuel LPG",
            "Bifuel CNG",
            "Bifuel Propane",
            "Bifuel Electric",
            "Bifuel Electric+combustion"
        )
        val index = buffer.getOrNull(2) ?: -1
        pid.calculatedResultString = translation.getOrElse(index) { "Unknown" }
    }

    fun isPidAvailable(pid: PID, pidValue: String): Boolean {
        val pidIndex = pid.PID.toInt(HEX_RADIX) % 32 - 1
        return hexToBitSet(pidValue).get(pidIndex)
    }

    private fun hexToBitSet(hex: String): BitSet {
        val bitSet = BitSet(hex.length * 4)
        hex.forEachIndexed { index, char ->
            val value = Integer.parseInt(char.toString(), HEX_RADIX)
            for (j in 0 until 4) {
                if (Integer.lowestOneBit(value shr j) == 1) {
                    bitSet.set(j + (hex.length - index - 1) * 4)
                }
            }
        }
        return bitSet
    }
}
