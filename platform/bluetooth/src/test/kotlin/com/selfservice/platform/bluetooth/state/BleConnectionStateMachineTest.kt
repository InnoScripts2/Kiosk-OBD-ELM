package com.selfservice.platform.bluetooth.state

import com.selfservice.platform.bluetooth.ble.BleConnectionState
import com.selfservice.platform.bluetooth.ble.BleDevice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit-тесты для BLE state machine
 * 
 * @since Session 07B
 */
class BleConnectionStateMachineTest {
    
    private lateinit var stateMachine: BleConnectionStateMachine
    private lateinit var testDevice: BleDevice
    
    @Before
    fun setup() {
        stateMachine = BleConnectionStateMachine()
        testDevice = BleDevice("00:11:22:33:44:55", "Test Device", rssi = -60)
    }
    
    @Test
    fun `начальное состояние должно быть DISCONNECTED`() {
        assertEquals(BleConnectionState.DISCONNECTED, stateMachine.getCurrentState())
        assertNull(stateMachine.getConnectedDevice())
    }
    
    @Test
    fun `startConnecting должен переводить в CONNECTING`() {
        stateMachine.startConnecting(testDevice)
        
        assertEquals(BleConnectionState.CONNECTING, stateMachine.getCurrentState())
        assertEquals(testDevice, stateMachine.getConnectedDevice())
    }
    
    @Test
    fun `startConnecting из неправильного состояния должен выбрасывать исключение`() {
        stateMachine.startConnecting(testDevice)
        
        assertThrows(IllegalArgumentException::class.java) {
            stateMachine.startConnecting(testDevice)
        }
    }
    
    @Test
    fun `onConnected должен переводить в DISCOVERING_SERVICES`() {
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        
        assertEquals(BleConnectionState.DISCOVERING_SERVICES, stateMachine.getCurrentState())
    }
    
    @Test
    fun `onServicesDiscovered должен переводить в CONNECTED`() {
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        stateMachine.onServicesDiscovered()
        
        assertEquals(BleConnectionState.CONNECTED, stateMachine.getCurrentState())
    }
    
    @Test
    fun `startDisconnecting должен переводить в DISCONNECTING`() {
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        stateMachine.onServicesDiscovered()
        stateMachine.startDisconnecting()
        
        assertEquals(BleConnectionState.DISCONNECTING, stateMachine.getCurrentState())
    }
    
    @Test
    fun `onDisconnected должен переводить в DISCONNECTED и очищать устройство`() {
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        stateMachine.onServicesDiscovered()
        stateMachine.startDisconnecting()
        stateMachine.onDisconnected()
        
        assertEquals(BleConnectionState.DISCONNECTED, stateMachine.getCurrentState())
        assertNull(stateMachine.getConnectedDevice())
    }
    
    @Test
    fun `onError должен переводить в DISCONNECTED из любого состояния`() {
        stateMachine.startConnecting(testDevice)
        val error = RuntimeException("Test error")
        
        stateMachine.onError(error)
        
        assertEquals(BleConnectionState.DISCONNECTED, stateMachine.getCurrentState())
        assertNull(stateMachine.getConnectedDevice())
    }
    
    @Test
    fun `история переходов должна записываться`() {
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        stateMachine.onServicesDiscovered()
        
        val history = stateMachine.getStateHistory()
        
        assertTrue(history.size >= 3)
        assertEquals(BleConnectionState.DISCONNECTED, history[0].from)
        assertEquals(BleConnectionState.CONNECTING, history[0].to)
        assertEquals(testDevice, history[0].device)
    }
    
    @Test
    fun `canPerformOperation должен проверять возможность операций`() {
        // В DISCONNECTED можно только CONNECT
        assertTrue(stateMachine.canPerformOperation(BleOperation.CONNECT))
        assertFalse(stateMachine.canPerformOperation(BleOperation.READ))
        assertFalse(stateMachine.canPerformOperation(BleOperation.WRITE))
        
        // В CONNECTED можно READ/WRITE
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        stateMachine.onServicesDiscovered()
        
        assertFalse(stateMachine.canPerformOperation(BleOperation.CONNECT))
        assertTrue(stateMachine.canPerformOperation(BleOperation.READ))
        assertTrue(stateMachine.canPerformOperation(BleOperation.WRITE))
        assertTrue(stateMachine.canPerformOperation(BleOperation.DISCONNECT))
    }
    
    @Test
    fun `reset должен сбрасывать все состояния`() {
        stateMachine.startConnecting(testDevice)
        stateMachine.onConnected()
        
        stateMachine.reset()
        
        assertEquals(BleConnectionState.DISCONNECTED, stateMachine.getCurrentState())
        assertNull(stateMachine.getConnectedDevice())
        assertTrue(stateMachine.getStateHistory().isEmpty())
    }
}

/**
 * Unit-тесты для BleStateValidator
 */
class BleStateValidatorTest {
    
    @Test
    fun `isValidTransition должен проверять допустимые переходы`() {
        // Допустимые переходы
        assertTrue(BleStateValidator.isValidTransition(
            BleConnectionState.DISCONNECTED, 
            BleConnectionState.CONNECTING
        ))
        
        assertTrue(BleStateValidator.isValidTransition(
            BleConnectionState.CONNECTING, 
            BleConnectionState.DISCOVERING_SERVICES
        ))
        
        assertTrue(BleStateValidator.isValidTransition(
            BleConnectionState.DISCOVERING_SERVICES, 
            BleConnectionState.CONNECTED
        ))
        
        // Недопустимые переходы
        assertFalse(BleStateValidator.isValidTransition(
            BleConnectionState.DISCONNECTED, 
            BleConnectionState.CONNECTED
        ))
        
        assertFalse(BleStateValidator.isValidTransition(
            BleConnectionState.CONNECTED, 
            BleConnectionState.CONNECTING
        ))
    }
    
    @Test
    fun `getAllowedTransitions должен возвращать список допустимых переходов`() {
        val fromDisconnected = BleStateValidator.getAllowedTransitions(BleConnectionState.DISCONNECTED)
        assertEquals(1, fromDisconnected.size)
        assertTrue(BleConnectionState.CONNECTING in fromDisconnected)
        
        val fromConnecting = BleStateValidator.getAllowedTransitions(BleConnectionState.CONNECTING)
        assertEquals(3, fromConnecting.size)
        assertTrue(BleConnectionState.DISCOVERING_SERVICES in fromConnecting)
        assertTrue(BleConnectionState.DISCONNECTING in fromConnecting)
        assertTrue(BleConnectionState.DISCONNECTED in fromConnecting)
        
        val fromConnected = BleStateValidator.getAllowedTransitions(BleConnectionState.CONNECTED)
        assertEquals(2, fromConnected.size)
        assertTrue(BleConnectionState.DISCONNECTING in fromConnected)
        assertTrue(BleConnectionState.DISCONNECTED in fromConnected)
    }
}
