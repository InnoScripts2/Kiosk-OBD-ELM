package com.selfservice.obd.core.session

import com.selfservice.obd.core.session.BleSessionStateMachine.Phase
import com.selfservice.obd.core.session.BleSessionStateMachine.SessionTimeouts
import com.selfservice.obd.core.session.BleSessionStateMachine.WatchdogKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest

/**
 * Расширенные unit-тесты для OBD session state machine
 * 
 * Проверяет:
 * - Детерминированные переходы Disconnected→Connected→Scanning→Finished
 * - Таймауты: tconn=5s, tscan=90s
 * - Guard-логику и отмену watchdog
 * 
 * @since Session 11C
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObdSessionStateMachineTest {
    
    @Test
    fun `default timeouts match requirements - tconn 5s tscan 90s`() {
        val timeouts = SessionTimeouts()
        
        // Verify tconn = 5s (handshake timeout)
        assertEquals(5_000L, timeouts.handshakeTimeoutMillis)
        
        // Verify tscan = 90s (diagnostics timeout)
        assertEquals(90_000L, timeouts.diagnosticsTimeoutMillis)
        
        // Verify inactivity timeout = 15s
        assertEquals(15_000L, timeouts.inactivityTimeoutMillis)
    }
    
    @Test
    fun `deterministic transitions - idle to connecting to handshake to ready`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Phase 1: IDLE → CONNECTING
        assertEquals(Phase.IDLE, machine.state.value.phase)
        
        machine.startSession(attempt = 0)
        assertEquals(Phase.CONNECTING, machine.state.value.phase)
        
        // Phase 2: CONNECTING → HANDSHAKE
        machine.onTransportReady()
        assertEquals(Phase.HANDSHAKE, machine.state.value.phase)
        
        // Phase 3: HANDSHAKE → READY
        machine.onHandshakeCompleted()
        assertEquals(Phase.READY, machine.state.value.phase)
        
        // Verify transitions were recorded
        assertTrue(observer.transitions.size >= 3)
        assertEquals(Phase.IDLE to Phase.CONNECTING, observer.transitions[0])
        assertEquals(Phase.CONNECTING to Phase.HANDSHAKE, observer.transitions[1])
        assertEquals(Phase.HANDSHAKE to Phase.READY, observer.transitions[2])
    }
    
    @Test
    fun `deterministic transitions - ready to diagnostics to completed`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Setup: reach READY state
        machine.startSession()
        machine.onTransportReady()
        machine.onHandshakeCompleted()
        assertEquals(Phase.READY, machine.state.value.phase)
        
        // Phase 4: READY → DIAGNOSTICS
        machine.beginDiagnostics()
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        // Keep diagnostics alive with heartbeats
        advanceTimeBy(10_000L)
        machine.recordDiagnosticsHeartbeat()
        advanceTimeBy(10_000L)
        machine.recordDiagnosticsHeartbeat()
        
        // Phase 5: DIAGNOSTICS → COMPLETED
        machine.completeSuccessfully()
        assertEquals(Phase.COMPLETED, machine.state.value.phase)
        
        // Verify complete transition sequence
        val relevantTransitions = observer.transitions.filter { 
            it.first == Phase.READY || it.first == Phase.DIAGNOSTICS 
        }
        assertTrue(relevantTransitions.contains(Phase.READY to Phase.DIAGNOSTICS))
        assertTrue(relevantTransitions.contains(Phase.DIAGNOSTICS to Phase.COMPLETED))
    }
    
    @Test
    fun `connection timeout tconn 5s triggers failure`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(
                handshakeTimeoutMillis = 5_000L,  // tconn = 5s
                inactivityTimeoutMillis = 15_000L,
                diagnosticsTimeoutMillis = 90_000L
            ),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        machine.startSession()
        machine.onTransportReady()
        assertEquals(Phase.HANDSHAKE, machine.state.value.phase)
        
        // Advance time to just before timeout
        advanceTimeBy(4_900L)
        assertEquals(Phase.HANDSHAKE, machine.state.value.phase)
        
        // Advance past timeout (5s)
        advanceTimeBy(200L)
        assertEquals(Phase.FAILED, machine.state.value.phase)
        assertEquals("handshake_timeout", machine.state.value.cause)
        
        // Verify watchdog was triggered
        assertTrue(observer.watchdogEvents.any { 
            it.first == Phase.HANDSHAKE && it.second == WatchdogKey.HANDSHAKE 
        })
    }
    
    @Test
    fun `scanning timeout tscan 90s triggers failure`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(
                handshakeTimeoutMillis = 5_000L,
                inactivityTimeoutMillis = 15_000L,
                diagnosticsTimeoutMillis = 90_000L  // tscan = 90s
            ),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Setup: reach DIAGNOSTICS state
        machine.startSession()
        machine.onTransportReady()
        machine.onHandshakeCompleted()
        machine.beginDiagnostics()
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        // Keep alive with heartbeats
        advanceTimeBy(30_000L)
        machine.recordDiagnosticsHeartbeat()
        advanceTimeBy(30_000L)
        machine.recordDiagnosticsHeartbeat()
        advanceTimeBy(29_000L)
        machine.recordDiagnosticsHeartbeat()
        
        // Total time: 89s, still in DIAGNOSTICS
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        // Advance past 90s timeout
        advanceTimeBy(2_000L) // Now at 91s
        assertEquals(Phase.FAILED, machine.state.value.phase)
        assertEquals("diagnostics_timeout", machine.state.value.cause)
        
        // Verify watchdog was triggered
        assertTrue(observer.watchdogEvents.any { 
            it.first == Phase.DIAGNOSTICS && it.second == WatchdogKey.DIAGNOSTICS 
        })
    }
    
    @Test
    fun `handshake completion cancels watchdog`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(handshakeTimeoutMillis = 5_000L),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        machine.startSession()
        machine.onTransportReady()
        assertEquals(Phase.HANDSHAKE, machine.state.value.phase)
        
        // Complete handshake before timeout
        advanceTimeBy(2_000L)
        machine.onHandshakeCompleted()
        assertEquals(Phase.READY, machine.state.value.phase)
        
        // Advance past original timeout - should not fail
        advanceTimeBy(4_000L) // Total 6s > 5s timeout
        assertEquals(Phase.READY, machine.state.value.phase)
        
        // Verify no handshake watchdog triggered
        assertTrue(observer.watchdogEvents.none { it.second == WatchdogKey.HANDSHAKE })
    }
    
    @Test
    fun `heartbeat resets inactivity watchdog`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(
                handshakeTimeoutMillis = 5_000L,
                inactivityTimeoutMillis = 15_000L,
                diagnosticsTimeoutMillis = 90_000L
            ),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Setup: reach DIAGNOSTICS
        machine.startSession()
        machine.onTransportReady()
        machine.onHandshakeCompleted()
        machine.beginDiagnostics()
        
        // Send heartbeat every 10s to prevent inactivity timeout
        advanceTimeBy(10_000L)
        machine.recordDiagnosticsHeartbeat()
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        advanceTimeBy(10_000L)
        machine.recordDiagnosticsHeartbeat()
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        advanceTimeBy(10_000L)
        machine.recordDiagnosticsHeartbeat()
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        // Total 30s elapsed, still alive due to heartbeats
        // Without heartbeats, would fail at 15s inactivity
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
    }
    
    @Test
    fun `inactivity timeout without heartbeat triggers failure`() = runTest {
        val observer = RecordingObserver()
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(
                handshakeTimeoutMillis = 5_000L,
                inactivityTimeoutMillis = 15_000L,
                diagnosticsTimeoutMillis = 90_000L
            ),
            observer = observer,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Setup: reach DIAGNOSTICS
        machine.startSession()
        machine.onTransportReady()
        machine.onHandshakeCompleted()
        machine.beginDiagnostics()
        
        // Don't send heartbeat, let inactivity timeout trigger
        advanceTimeBy(14_900L)
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        
        advanceTimeBy(200L) // Pass 15s inactivity timeout
        assertEquals(Phase.FAILED, machine.state.value.phase)
        assertEquals("diagnostics_inactivity", machine.state.value.cause)
        
        // Verify inactivity watchdog triggered
        assertTrue(observer.watchdogEvents.any { 
            it.first == Phase.DIAGNOSTICS && it.second == WatchdogKey.INACTIVITY 
        })
    }
    
    @Test
    fun `reset from any state returns to idle`() = runTest {
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(),
            observer = null,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Test reset from CONNECTING
        machine.startSession()
        assertEquals(Phase.CONNECTING, machine.state.value.phase)
        machine.reset()
        assertEquals(Phase.IDLE, machine.state.value.phase)
        
        // Test reset from HANDSHAKE
        machine.startSession()
        machine.onTransportReady()
        assertEquals(Phase.HANDSHAKE, machine.state.value.phase)
        machine.reset()
        assertEquals(Phase.IDLE, machine.state.value.phase)
        
        // Test reset from DIAGNOSTICS
        machine.startSession()
        machine.onTransportReady()
        machine.onHandshakeCompleted()
        machine.beginDiagnostics()
        assertEquals(Phase.DIAGNOSTICS, machine.state.value.phase)
        machine.reset()
        assertEquals(Phase.IDLE, machine.state.value.phase)
    }
    
    @Test
    fun `cannot start session from non-idle state`() = runTest {
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(),
            observer = null,
            timeProvider = { testScheduler.currentTime }
        )
        
        machine.startSession()
        assertEquals(Phase.CONNECTING, machine.state.value.phase)
        
        // Attempt to start again should fail
        assertFailsWith<IllegalStateException> {
            machine.startSession()
        }
    }
    
    @Test
    fun `completed session cannot transition further`() = runTest {
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(),
            observer = null,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Reach COMPLETED state
        machine.startSession()
        machine.onTransportReady()
        machine.onHandshakeCompleted()
        machine.beginDiagnostics()
        machine.completeSuccessfully()
        assertEquals(Phase.COMPLETED, machine.state.value.phase)
        
        // Attempt further transitions should fail
        assertFailsWith<IllegalStateException> {
            machine.beginDiagnostics()
        }
    }
    
    @Test
    fun `failed session cannot transition further`() = runTest {
        val machine = BleSessionStateMachine(
            scope = this,
            timeouts = SessionTimeouts(),
            observer = null,
            timeProvider = { testScheduler.currentTime }
        )
        
        // Reach FAILED state
        machine.startSession()
        machine.fail("test_failure")
        assertEquals(Phase.FAILED, machine.state.value.phase)
        assertEquals("test_failure", machine.state.value.cause)
        
        // Attempt further transitions should fail
        assertFailsWith<IllegalStateException> {
            machine.onTransportReady()
        }
    }
    
    private class RecordingObserver : BleSessionStateMachine.SessionObserver {
        val transitions = mutableListOf<Pair<Phase, Phase>>()
        val watchdogEvents = mutableListOf<Pair<Phase, WatchdogKey>>()
        
        override fun onStateChanged(
            previous: BleSessionStateMachine.StateSnapshot,
            current: BleSessionStateMachine.StateSnapshot,
            durationMillis: Long
        ) {
            transitions += previous.phase to current.phase
        }
        
        override fun onWatchdogTriggered(phase: Phase, key: WatchdogKey, timeoutMillis: Long) {
            watchdogEvents += phase to key
        }
    }
}
