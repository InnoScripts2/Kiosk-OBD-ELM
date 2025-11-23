/**
 * Ported from https://github.com/bradbarnhill/AndroidOBD (MIT License).
 */
@file:Suppress("unused")

package com.selfservice.obd.elm.port.enums

import java.lang.Long.parseLong

/**
 * Enumerates all supported OBD-II modes from the original ELM327 reference implementation.
 */
enum class ObdModes(val value: Char) {
    MODE_01('1'),
    MODE_02('2'),
    MODE_03('3'),
    MODE_04('4'),
    MODE_05('5'),
    MODE_06('6'),
    MODE_07('7'),
    MODE_08('8'),
    MODE_09('9'),
    MODE_0A('A');

    val intValue: Int get() = parseLong(value.toString(), 16).toInt()

    override fun toString(): String = value.toString()
}
