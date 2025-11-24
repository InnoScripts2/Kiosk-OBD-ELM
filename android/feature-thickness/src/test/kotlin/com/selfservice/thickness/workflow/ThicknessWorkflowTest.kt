package com.selfservice.thickness.workflow

import com.selfservice.core.logging.Logger
import com.selfservice.thickness.ThicknessDevice
import com.selfservice.thickness.ThicknessMeasurement
import com.selfservice.thickness.ThicknessMeasurementStateMachine
import com.selfservice.thickness.ConnectionStatus
import com.selfservice.thickness.MeasurementStatus
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для ThicknessWorkflow
 * @since Session 12B
 */
class ThicknessWorkflowTest {
    
    // Мок устройства для тестирования
    class MockDevice : ThicknessDevice {
        var isConnected = false
        var measurementCount = 0
        
        override suspend fun connect(): Result<Unit> {
            isConnected = true
            return Result.success(Unit)
        }
        
        override suspend fun disconnect() {
            isConnected = false
            measurementCount = 0
        }
        
        override suspend fun startMeasurements(): kotlinx.coroutines.flow.Flow<ThicknessMeasurement> {
            return flow {
                // Эмитируем 60 измерений
                repeat(60) { index ->
                    emit(ThicknessMeasurement(
                        timestamp = System.currentTimeMillis(),
                        value = 100f + index,
                        zone = "zone_$index",
                        status = MeasurementStatus.VALID
                    ))
                    measurementCount++
                }
            }
        }
        
        override suspend fun stopMeasurements() {
            // Останавливаем
        }
        
        override fun getConnectionStatus(): ConnectionStatus {
            return if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED
        }
    }
    
    // Мок логгера
    class MockLogger : Logger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warn(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
    
    @Test
    fun `test complete workflow success`() = runBlocking {
        val device = MockDevice()
        val stateMachine = ThicknessMeasurementStateMachine()
        val logger = MockLogger()
        
        val workflow = ThicknessWorkflow(
            device = device,
            stateMachine = stateMachine,
            logger = logger
        )
        
        val result = workflow.startMeasurementProcess(
            sessionId = "test_session_1",
            vehicleType = "sedan"
        )
        
        assertTrue(result.isSuccess)
        
        val report = result.getOrNull()
        assertEquals("test_session_1", report?.sessionId)
        assertEquals("sedan", report?.vehicleType)
        assertEquals(60, report?.measurements?.size)
        
        // Проверяем что устройство отключено после завершения
        assertEquals(ConnectionStatus.DISCONNECTED, device.getConnectionStatus())
    }
    
    @Test
    fun `test workflow state transitions`() = runBlocking {
        val device = MockDevice()
        val stateMachine = ThicknessMeasurementStateMachine()
        val logger = MockLogger()
        
        val workflow = ThicknessWorkflow(
            device = device,
            stateMachine = stateMachine,
            logger = logger
        )
        
        // Начальное состояние - Idle
        assertTrue(workflow.workflowState.value is ThicknessWorkflow.WorkflowState.Idle)
        
        // Запускаем процесс (асинхронно)
        val result = workflow.startMeasurementProcess("test_session_2", "suv")
        
        // После завершения должны быть в состоянии Completed
        assertTrue(result.isSuccess)
        assertTrue(workflow.workflowState.value is ThicknessWorkflow.WorkflowState.Completed)
    }
    
    @Test
    fun `test progress tracking`() = runBlocking {
        val device = MockDevice()
        val stateMachine = ThicknessMeasurementStateMachine()
        val logger = MockLogger()
        
        val workflow = ThicknessWorkflow(
            device = device,
            stateMachine = stateMachine,
            logger = logger
        )
        
        // Начальный прогресс
        val initialProgress = workflow.getProgress()
        assertEquals(0, initialProgress.first)
        assertEquals(60, initialProgress.second)
        
        // После завершения
        workflow.startMeasurementProcess("test_session_3", "minivan")
        
        val finalProgress = workflow.getProgress()
        assertEquals(60, finalProgress.first)
        assertEquals(60, finalProgress.second)
    }
    
    @Test
    fun `test cancel workflow`() = runBlocking {
        val device = MockDevice()
        val stateMachine = ThicknessMeasurementStateMachine()
        val logger = MockLogger()
        
        val workflow = ThicknessWorkflow(
            device = device,
            stateMachine = stateMachine,
            logger = logger
        )
        
        // Отменяем workflow
        workflow.cancel()
        
        // Состояние должно быть Idle
        assertTrue(workflow.workflowState.value is ThicknessWorkflow.WorkflowState.Idle)
        
        // Устройство должно быть отключено
        assertEquals(ConnectionStatus.DISCONNECTED, device.getConnectionStatus())
    }
    
    @Test
    fun `test getCurrentMeasurements`() = runBlocking {
        val device = MockDevice()
        val stateMachine = ThicknessMeasurementStateMachine()
        val logger = MockLogger()
        
        val workflow = ThicknessWorkflow(
            device = device,
            stateMachine = stateMachine,
            logger = logger
        )
        
        // До начала измерений
        val initialMeasurements = workflow.getCurrentMeasurements()
        assertTrue(initialMeasurements.isEmpty())
        
        // После завершения
        workflow.startMeasurementProcess("test_session_4", "sedan")
        
        val finalMeasurements = workflow.getCurrentMeasurements()
        assertEquals(60, finalMeasurements.size)
        
        // Проверяем что все измерения валидны
        finalMeasurements.forEach { measurement ->
            assertEquals(MeasurementStatus.VALID, measurement.status)
            assertTrue(measurement.value != null)
            assertTrue(measurement.value!! >= 100f)
        }
    }
}
