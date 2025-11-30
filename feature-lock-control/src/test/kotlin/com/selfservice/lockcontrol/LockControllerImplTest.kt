package com.selfservice.lockcontrol

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit тесты для LockControllerImpl
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockControllerImplTest {
    
    @get:Rule
    val tempFolder = TemporaryFolder()
    
    private lateinit var controller: LockControllerImpl
    private lateinit var mockAdapter: MockUsbSerialAdapter
    private lateinit var logDir: File
    private lateinit var scope: CoroutineScope
    private var currentSessionId = "test_session_123"
    
    @Before
    fun setup() {
        val testDispatcher = StandardTestDispatcher()
        scope = CoroutineScope(SupervisorJob() + testDispatcher)
        
        val config = UsbSerialConfig(
            port = "/dev/mock",
            baudRate = 9600,
            commandTimeout = 5000,
            reconnectDelay = 1000,
            heartbeatInterval = 30000
        )
        
        mockAdapter = MockUsbSerialAdapter(config)
        logDir = tempFolder.newFolder("logs")
        
        controller = LockControllerImpl(
            usbSerialAdapter = mockAdapter,
            logDir = logDir,
            sessionIdProvider = { currentSessionId },
            scope = scope,
            retryCount = 3,
            retryDelay = 100
        )
    }
    
    @Test
    fun `initialize connects and queries status`() = runTest {
        controller.initialize()
        
        assertTrue(mockAdapter.isConnected())
        
        val status = controller.getStatus()
        assertTrue(status.connected)
        assertEquals(LockState.CLOSED, status.thickness)
        assertEquals(LockState.CLOSED, status.adapter)
    }
    
    @Test
    fun `openSlot THICKNESS changes status to OPEN`() = runTest {
        controller.initialize()
        
        controller.openSlot(DeviceType.THICKNESS)
        
        val status = controller.getStatus()
        assertEquals(LockState.OPEN, status.thickness)
        assertEquals(LockState.CLOSED, status.adapter)
    }
    
    @Test
    fun `openSlot ADAPTER changes status to OPEN`() = runTest {
        controller.initialize()
        
        controller.openSlot(DeviceType.ADAPTER)
        
        val status = controller.getStatus()
        assertEquals(LockState.CLOSED, status.thickness)
        assertEquals(LockState.OPEN, status.adapter)
    }
    
    @Test
    fun `closeSlot THICKNESS changes status to CLOSED`() = runTest {
        controller.initialize()
        
        // Open first
        controller.openSlot(DeviceType.THICKNESS)
        assertEquals(LockState.OPEN, controller.getStatus().thickness)
        
        // Then close
        controller.closeSlot(DeviceType.THICKNESS)
        assertEquals(LockState.CLOSED, controller.getStatus().thickness)
    }
    
    @Test
    fun `closeSlot ADAPTER changes status to CLOSED`() = runTest {
        controller.initialize()
        
        controller.openSlot(DeviceType.ADAPTER)
        assertEquals(LockState.OPEN, controller.getStatus().adapter)
        
        controller.closeSlot(DeviceType.ADAPTER)
        assertEquals(LockState.CLOSED, controller.getStatus().adapter)
    }
    
    @Test
    fun `operation logs are emitted`() = runTest {
        controller.initialize()
        
        controller.operationLogs.test {
            controller.openSlot(DeviceType.THICKNESS)
            
            val log = awaitItem()
            assertEquals("open", log.action)
            assertEquals(DeviceType.THICKNESS, log.deviceType)
            assertTrue(log.success)
            assertEquals(LockState.OPEN, log.status)
            assertNull(log.error)
            assertEquals(currentSessionId, log.sessionId)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
    
    @Test
    fun `operation logs are saved to file`() = runTest {
        controller.initialize()
        
        controller.openSlot(DeviceType.THICKNESS)
        
        // Check that log file was created
        val logFiles = logDir.listFiles()
        assertNotNull(logFiles)
        assertTrue(logFiles!!.isNotEmpty())
        
        val logFile = logFiles.first()
        assertTrue(logFile.name.startsWith("lock-operations-"))
        assertTrue(logFile.name.endsWith(".json"))
        
        // Check log content
        val logContent = logFile.readText()
        assertTrue(logContent.contains("\"action\":\"open\""))
        assertTrue(logContent.contains("\"deviceType\":\"THICKNESS\""))
        assertTrue(logContent.contains("\"success\":true"))
        assertTrue(logContent.contains("\"sessionId\":\"$currentSessionId\""))
    }
    
    @Test
    fun `multiple operations are logged correctly`() = runTest {
        controller.initialize()
        
        controller.openSlot(DeviceType.THICKNESS)
        controller.openSlot(DeviceType.ADAPTER)
        controller.closeSlot(DeviceType.THICKNESS)
        
        val logFiles = logDir.listFiles()
        assertNotNull(logFiles)
        val logFile = logFiles!!.first()
        
        val lines = logFile.readLines()
        assertEquals(3, lines.size)
        
        assertTrue(lines[0].contains("\"action\":\"open\""))
        assertTrue(lines[0].contains("\"deviceType\":\"THICKNESS\""))
        
        assertTrue(lines[1].contains("\"action\":\"open\""))
        assertTrue(lines[1].contains("\"deviceType\":\"ADAPTER\""))
        
        assertTrue(lines[2].contains("\"action\":\"close\""))
        assertTrue(lines[2].contains("\"deviceType\":\"THICKNESS\""))
    }
    
    @Test
    fun `shutdown disconnects adapter`() = runTest {
        controller.initialize()
        assertTrue(mockAdapter.isConnected())
        
        controller.shutdown()
        assertFalse(mockAdapter.isConnected())
        
        val status = controller.getStatus()
        assertFalse(status.connected)
    }
    
    @Test
    fun `getStatus reflects current lock states`() = runTest {
        controller.initialize()
        
        // All closed initially
        var status = controller.getStatus()
        assertEquals(LockState.CLOSED, status.thickness)
        assertEquals(LockState.CLOSED, status.adapter)
        
        // Open thickness
        controller.openSlot(DeviceType.THICKNESS)
        status = controller.getStatus()
        assertEquals(LockState.OPEN, status.thickness)
        assertEquals(LockState.CLOSED, status.adapter)
        
        // Open adapter
        controller.openSlot(DeviceType.ADAPTER)
        status = controller.getStatus()
        assertEquals(LockState.OPEN, status.thickness)
        assertEquals(LockState.OPEN, status.adapter)
        
        // Close thickness
        controller.closeSlot(DeviceType.THICKNESS)
        status = controller.getStatus()
        assertEquals(LockState.CLOSED, status.thickness)
        assertEquals(LockState.OPEN, status.adapter)
    }
    
    @Test
    fun `sessionId is included in logs`() = runTest {
        currentSessionId = "custom_session_456"
        controller.initialize()
        
        controller.operationLogs.test {
            controller.openSlot(DeviceType.ADAPTER)
            
            val log = awaitItem()
            assertEquals("custom_session_456", log.sessionId)
            
            cancelAndIgnoreRemainingEvents()
        }
    }
}
