package com.selfservice.platform.bluetooth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Контрактные тесты для моделей и состояний BLE уровня
 * без инициализации реального Bluetooth стека Android.
 *
 * @since Session 10B
 */
class BleConnectionManagerCallbackTest {

    @Test
    fun `BleConnectionState sealed class has all required states`() {
        val disconnected: BleConnectionState = BleConnectionState.Disconnected
        val scanning: BleConnectionState = BleConnectionState.Scanning
        val connecting: BleConnectionState = BleConnectionState.Connecting
        val connected: BleConnectionState = BleConnectionState.Connected("AA:BB:CC:DD:EE:FF")
        val error: BleConnectionState = BleConnectionState.Error("Test error")

        assertTrue(disconnected is BleConnectionState.Disconnected)
        assertTrue(scanning is BleConnectionState.Scanning)
        assertTrue(connecting is BleConnectionState.Connecting)
        val connectedState = when (connected) {
            is BleConnectionState.Connected -> connected
            else -> fail("Connected state expected")
        }
        val errorState = when (error) {
            is BleConnectionState.Error -> error
            else -> fail("Error state expected")
        }
        assertEquals("AA:BB:CC:DD:EE:FF", connectedState.address)
        assertEquals("Test error", errorState.message)
    }

    @Test
    fun `BleDevice exposes stable data contract`() {
        val device = BleDevice(
            address = "11:22:33:44:55:66",
            name = "ELM327 OBD",
            rssi = -65
        )

        assertEquals("11:22:33:44:55:66", device.address)
        assertEquals("ELM327 OBD", device.name)
        assertEquals(-65, device.rssi)
    }

    @Test
    fun `BleDevice state update replaces same address`() {
        val existing = mutableListOf(
            BleDevice("AA:BB:CC:DD:EE:FF", "Test", rssi = -80)
        )
        val updated = BleDevice("AA:BB:CC:DD:EE:FF", "Test", rssi = -60)

        val index = existing.indexOfFirst { it.address == updated.address }
        if (index >= 0) {
            existing[index] = updated
        } else {
            existing.add(updated)
        }

        assertEquals(1, existing.size)
        assertEquals(-60, existing.first().rssi)
    }
}
