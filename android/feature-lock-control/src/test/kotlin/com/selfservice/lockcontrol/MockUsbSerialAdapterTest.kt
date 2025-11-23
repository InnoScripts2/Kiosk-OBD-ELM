package com.selfservice.lockcontrol

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit тесты для MockUsbSerialAdapter
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MockUsbSerialAdapterTest {
    
    private lateinit var adapter: MockUsbSerialAdapter
    private lateinit var config: UsbSerialConfig
    
    @Before
    fun setup() {
        config = UsbSerialConfig(
            port = "/dev/mock",
            baudRate = 9600,
            commandTimeout = 5000,
            reconnectDelay = 1000,
            heartbeatInterval = 30000
        )
        adapter = MockUsbSerialAdapter(config)
    }
    
    @Test
    fun `connect emits Connected event`() = runTest {
        adapter.events.test {
            adapter.connect()
            
            val event = awaitItem()
            assertTrue(event is UsbSerialEvent.Connected)
            
            assertTrue(adapter.isConnected())
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `disconnect emits Disconnected event`() = runTest {
        adapter.connect()
        
        adapter.events.test {
            adapter.disconnect()
            
            val event = awaitItem()
            assertTrue(event is UsbSerialEvent.Disconnected)
            
            assertFalse(adapter.isConnected())
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `connect twice does not fail`() = runTest {
        adapter.connect()
        assertTrue(adapter.isConnected())
        
        // Should not throw
        adapter.connect()
        assertTrue(adapter.isConnected())
    }
    
    @Test
    fun `sendCommand PING returns PONG`() = runTest {
        adapter.connect()
        
        val response = adapter.sendCommand(ArduinoCommand.PING)
        
        assertTrue(response is ArduinoResponse.Pong)
        assertEquals("PONG", response.raw)
    }
    
    @Test
    fun `sendCommand OPEN_THICKNESS returns OK`() = runTest {
        adapter.connect()
        
        val response = adapter.sendCommand(ArduinoCommand.OPEN_THICKNESS)
        
        assertTrue(response is ArduinoResponse.Ok)
        assertEquals("OK:OPENED:THICKNESS", response.raw)
        assertEquals("OPENED", (response as ArduinoResponse.Ok).action)
        assertEquals("THICKNESS", response.device)
    }
    
    @Test
    fun `sendCommand OPEN_OBD returns OK`() = runTest {
        adapter.connect()
        
        val response = adapter.sendCommand(ArduinoCommand.OPEN_OBD)
        
        assertTrue(response is ArduinoResponse.Ok)
        assertEquals("OK:OPENED:OBD", response.raw)
    }
    
    @Test
    fun `sendCommand CLOSE_THICKNESS returns OK`() = runTest {
        adapter.connect()
        
        val response = adapter.sendCommand(ArduinoCommand.CLOSE_THICKNESS)
        
        assertTrue(response is ArduinoResponse.Ok)
        assertEquals("OK:CLOSED:THICKNESS", response.raw)
    }
    
    @Test
    fun `sendCommand STATUS returns correct state`() = runTest {
        adapter.connect()
        
        // Initially closed
        val status1 = adapter.sendCommand(ArduinoCommand.STATUS)
        assertTrue(status1 is ArduinoResponse.Status)
        assertFalse((status1 as ArduinoResponse.Status).thicknessOpen)
        assertFalse(status1.obdOpen)
        
        // Open thickness
        adapter.sendCommand(ArduinoCommand.OPEN_THICKNESS)
        
        val status2 = adapter.sendCommand(ArduinoCommand.STATUS)
        assertTrue(status2 is ArduinoResponse.Status)
        assertTrue((status2 as ArduinoResponse.Status).thicknessOpen)
        assertFalse(status2.obdOpen)
        
        // Open OBD
        adapter.sendCommand(ArduinoCommand.OPEN_OBD)
        
        val status3 = adapter.sendCommand(ArduinoCommand.STATUS)
        assertTrue(status3 is ArduinoResponse.Status)
        assertTrue((status3 as ArduinoResponse.Status).thicknessOpen)
        assertTrue(status3.obdOpen)
    }
    
    @Test
    fun `sendCommand without connection throws exception`() = runTest {
        assertThrows(UsbSerialException.IoError::class.java) {
            runTest {
                adapter.sendCommand(ArduinoCommand.PING)
            }
        }
    }
    
    @Test
    fun `events flow emits Response events`() = runTest {
        adapter.connect()
        
        adapter.events.test {
            // Skip Connected event
            skipItems(2) // Connected + Ready
            
            adapter.sendCommand(ArduinoCommand.PING)
            
            val event = awaitItem()
            assertTrue(event is UsbSerialEvent.Response)
            val response = (event as UsbSerialEvent.Response).response
            assertTrue(response is ArduinoResponse.Pong)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `ArduinoCommand openCommand returns correct command`() {
        assertEquals(ArduinoCommand.OPEN_THICKNESS, 
            ArduinoCommand.openCommand(DeviceType.THICKNESS))
        assertEquals(ArduinoCommand.OPEN_OBD, 
            ArduinoCommand.openCommand(DeviceType.ADAPTER))
    }
    
    @Test
    fun `ArduinoCommand closeCommand returns correct command`() {
        assertEquals(ArduinoCommand.CLOSE_THICKNESS, 
            ArduinoCommand.closeCommand(DeviceType.THICKNESS))
        assertEquals(ArduinoCommand.CLOSE_OBD, 
            ArduinoCommand.closeCommand(DeviceType.ADAPTER))
    }
}
