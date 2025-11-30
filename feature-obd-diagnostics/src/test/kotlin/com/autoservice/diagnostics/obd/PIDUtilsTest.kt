package com.autoservice.diagnostics.obd

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PIDUtilsTest {

    private val rpmPid = PID(
        mode = ObdMode.CURRENT_DATA,
        pid = "0c",
        description = "Engine RPM",
        unit = "rpm",
        formula = { 0.0 }
    )

    @Test
    fun `formatRequest normalizes mode and pid`() {
        val request = PIDUtils.formatRequest(rpmPid)
        assertEquals("010C", request)
    }

    @Test
    fun `extractPayload removes headers and delimiters`() {
        val response = "\r41 0c 1A f8 >"
        val payload = PIDUtils.extractPayload(rpmPid, response)
        assertContentEquals(byteArrayOf(0x1A.toByte(), 0xF8.toByte()), payload)
    }

    @Test
    fun `extractPayload fails without matching header`() {
        assertFailsWith<IllegalStateException> {
            PIDUtils.extractPayload(rpmPid, "410D2A")
        }
    }
}
