package com.selfservice.thickness

import com.selfservice.thickness.models.ThicknessZoneLayout
import com.selfservice.thickness.models.ZoneMeasurement
import com.selfservice.thickness.MeasurementStatus
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для ThicknessMeasurementStateMachine
 * 
 * @since Session 12B
 */
class ThicknessMeasurementStateMachineTest {
    
    @Test
    fun `test initial state is Idle`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Idle)
    }
    
    @Test
    fun `test connect event transitions to Connecting`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Connecting)
    }
    
    @Test
    fun `test connected event transitions to Ready`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Ready)
    }
    
    @Test
    fun `test connection failed transitions to Error`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(
            ThicknessMeasurementStateMachine.Event.ConnectionFailed(
                Exception("Connection timeout")
            )
        )
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Error)
        assertEquals("Connection failed: Connection timeout", (state as ThicknessMeasurementStateMachine.State.Error).message)
    }
    
    @Test
    fun `test start measurements transitions to Measuring`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в Ready состояние
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        
        // Начинаем измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Measuring)
        assertEquals(0, (state as ThicknessMeasurementStateMachine.State.Measuring).progress)
        assertEquals(60, state.total)
    }
    
    @Test
    fun `test measurement received updates progress`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Добавляем измерение
        val measurement = ThicknessMeasurement(
            timestamp = System.currentTimeMillis(),
            value = 100f,
            zone = "zone_0",
            status = MeasurementStatus.VALID
        )
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Measuring)
        assertEquals(1, (state as ThicknessMeasurementStateMachine.State.Measuring).progress)
    }
    
    @Test
    fun `test auto completion after 60 measurements`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Добавляем 60 измерений
        repeat(60) { index ->
            val measurement = ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 100f + index,
                zone = "zone_$index",
                status = MeasurementStatus.VALID
            )
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        }
        
        // Должны автоматически перейти в Completed
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Completed)
        
        val measurements = (state as ThicknessMeasurementStateMachine.State.Completed).measurements
        assertEquals(60, measurements.size)
    }
    
    @Test
    fun `test measurement error transitions to Error state`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Добавляем несколько измерений
        repeat(5) { index ->
            val measurement = ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 100f,
                zone = "zone_$index",
                status = MeasurementStatus.VALID
            )
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        }
        
        // Отправляем ошибку
        stateMachine.handleEvent(
            ThicknessMeasurementStateMachine.Event.MeasurementError(
                Exception("Device disconnected")
            )
        )
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Error)
        assertTrue((state as ThicknessMeasurementStateMachine.State.Error).message.contains("Device disconnected"))
    }
    
    @Test
    fun `test retry from Error returns to Ready`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переводим в состояние ошибки
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(
            ThicknessMeasurementStateMachine.Event.ConnectionFailed(
                Exception("Timeout")
            )
        )
        
        assertTrue(stateMachine.getCurrentState() is ThicknessMeasurementStateMachine.State.Error)
        
        // Повторная попытка
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Retry)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Ready)
    }
    
    @Test
    fun `test reset from any state returns to Idle`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Добавляем измерения
        repeat(10) { index ->
            val measurement = ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 100f,
                zone = "zone_$index",
                status = MeasurementStatus.VALID
            )
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        }
        
        // Сброс
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Reset)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Idle)
        assertEquals(0, stateMachine.getMeasurements().size)
    }
    
    @Test
    fun `test custom expected measurements count`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        stateMachine.setExpectedMeasurements(10)
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Measuring)
        assertEquals(10, (state as ThicknessMeasurementStateMachine.State.Measuring).total)
    }
    
    @Test
    fun `test manual completion event`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Добавляем только 5 измерений (меньше 60)
        repeat(5) { index ->
            val measurement = ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 100f,
                zone = "zone_$index",
                status = MeasurementStatus.VALID
            )
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        }
        
        // Принудительно завершаем
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.AllMeasurementsCompleted)
        
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Completed)
        assertEquals(5, (state as ThicknessMeasurementStateMachine.State.Completed).measurements.size)
    }
    
    @Test
    fun `test getMeasurements returns collected data`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Переходим в состояние измерения
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Добавляем 3 измерения
        repeat(3) { index ->
            val measurement = ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 100f + index * 10,
                zone = "zone_$index",
                status = MeasurementStatus.VALID
            )
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        }
        
        val measurements = stateMachine.getMeasurements()
        assertEquals(3, measurements.size)
        assertEquals(100f, measurements[0].value)
        assertEquals(110f, measurements[1].value)
        assertEquals(120f, measurements[2].value)
    }
    
    @Test
    fun `test invalid transition does not change state`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Пытаемся начать измерения из Idle (невалидный переход)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Состояние должно остаться Idle
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Idle)
    }
    
    @Test
    fun `test measurements cleared on start`() {
        val stateMachine = ThicknessMeasurementStateMachine()
        
        // Первый цикл измерений
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        repeat(5) { index ->
            val measurement = ThicknessMeasurement(
                timestamp = System.currentTimeMillis(),
                value = 100f,
                zone = "zone_$index",
                status = MeasurementStatus.VALID
            )
            stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.MeasurementReceived(measurement))
        }
        
        assertEquals(5, stateMachine.getMeasurements().size)
        
        // Завершаем и начинаем заново
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.AllMeasurementsCompleted)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Reset)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connect)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.Connected)
        stateMachine.handleEvent(ThicknessMeasurementStateMachine.Event.StartMeasurements)
        
        // Измерения должны быть очищены
        val state = stateMachine.getCurrentState()
        assertTrue(state is ThicknessMeasurementStateMachine.State.Measuring)
        assertEquals(0, (state as ThicknessMeasurementStateMachine.State.Measuring).progress)
    }
}
