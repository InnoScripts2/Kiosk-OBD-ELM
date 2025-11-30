package com.selfservice.obd.core.enums

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Тесты для ObdProtocols
 */
class ObdProtocolsTest {

    @Test
    fun `test protocol values`() {
        assertEquals('0', ObdProtocols.AUTO.value)
        assertEquals('1', ObdProtocols.SAE_J1850_PWM.value)
        assertEquals('6', ObdProtocols.ISO_15765_4_CAN.value)
    }

    @Test
    fun `test all protocols exist`() {
        assertNotNull(ObdProtocols.AUTO)
        assertNotNull(ObdProtocols.SAE_J1850_PWM)
        assertNotNull(ObdProtocols.SAE_J1850_VPW)
        assertNotNull(ObdProtocols.ISO_9141_2)
        assertNotNull(ObdProtocols.ISO_14230_4_KWP)
        assertNotNull(ObdProtocols.ISO_14230_4_KWP_FAST)
        assertNotNull(ObdProtocols.ISO_15765_4_CAN)
        assertNotNull(ObdProtocols.ISO_15765_4_CAN_B)
        assertNotNull(ObdProtocols.ISO_15765_4_CAN_C)
        assertNotNull(ObdProtocols.ISO_15765_4_CAN_D)
        assertNotNull(ObdProtocols.SAE_J1939_CAN)
        assertNotNull(ObdProtocols.USER1_CAN)
        assertNotNull(ObdProtocols.USER2_CAN)
    }
}
