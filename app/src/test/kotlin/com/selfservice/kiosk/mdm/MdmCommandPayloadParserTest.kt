package com.selfservice.kiosk.mdm

import kotlin.test.Test
import kotlin.test.assertEquals

class MdmCommandPayloadParserTest {

    @Test
    fun `parses simple boolean payload`() {
        val raw = "{\"force\":true}"
        val parsed = MdmCommandPayloadParser.parseJsonMap(raw)
        assertEquals(true, parsed["force"])
    }
}
