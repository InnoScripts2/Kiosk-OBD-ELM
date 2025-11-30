package com.selfservice.kiosk

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isFocusable
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.selfservice.kiosk.diagnostics.ObdConnectionController
import com.selfservice.core.DispatchersProvider
import com.selfservice.core.permissions.BluetoothEnvironmentState
import com.selfservice.core.permissions.BluetoothPermissionHelper
import com.selfservice.core.permissions.toStatus
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScannerConfig
import com.selfservice.obd.core.connection.ObdConnectionManager
import com.selfservice.obd.core.platform.BluetoothEnvironmentRepository
import com.selfservice.obd.core.platform.BluetoothPrerequisitesMonitor
import com.selfservice.obd.core.platform.BluetoothPrerequisitesUseCase
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.obd.ui.prerequisites.BluetoothPrerequisitePresenter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityBluetoothPrerequisiteTest {

    private lateinit var obdHarness: TestObdConnectionHarness

    @Before
    fun setUp() {
        obdHarness = TestObdConnectionHarness()
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KioskApp
        app.overrideObdConnectionController(obdHarness.controller)
    }

    @After
    fun tearDown() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as KioskApp
        app.overrideObdConnectionController(null)
        obdHarness.shutdown()
        BluetoothPrerequisitesEntryPoint.resetOverride()
    }

    @Test
    fun showsMissingPermissionsStatusWhenPermissionsAreMissing() {
        val harness = TestBluetoothPrerequisitesHarness(missingPermissionsState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
                .check(matches(isFocusable()))
                .check(matches(withContentDescription(expectedOverlayDescription(R.string.bluetooth_prereq_missing_permissions))))
            onView(withId(R.id.prerequisiteStatusText))
                .check(matches(withText(R.string.bluetooth_prereq_missing_permissions)))
            onView(withId(R.id.prerequisiteStatusHint))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.bluetooth_prereq_retry_hint)))
        }
    }

    @Test
    fun showsEnableBluetoothMessageWhenBluetoothDisabled() {
        val harness = TestBluetoothPrerequisitesHarness(bluetoothDisabledState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.prerequisiteStatusContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.prerequisiteStatusText))
                .check(matches(withText(R.string.bluetooth_prereq_enable_bluetooth)))
            onView(withId(R.id.prerequisiteStatusHint))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.bluetooth_prereq_retry_hint)))
            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(withContentDescription(expectedOverlayDescription(R.string.bluetooth_prereq_enable_bluetooth))))
        }
    }

    @Test
    fun showsEnableLocationMessageWhenLocationDisabled() {
        val harness = TestBluetoothPrerequisitesHarness(locationDisabledState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.prerequisiteStatusContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.prerequisiteStatusText))
                .check(matches(withText(R.string.bluetooth_prereq_enable_location)))
            onView(withId(R.id.prerequisiteStatusHint))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.bluetooth_prereq_retry_hint)))
            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(withContentDescription(expectedOverlayDescription(R.string.bluetooth_prereq_enable_location))))
        }
    }

    @Test
    fun hidesOverlayWhenPrerequisitesReady() {
        val harness = TestBluetoothPrerequisitesHarness(readyState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
                .check(matches(withContentDescription(nullValue(CharSequence::class.java))))
                .check(matches(not(isFocusable())))
                .check(matches(not(isClickable())))
            onView(withId(R.id.prerequisiteStatusHint))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun tappingOverlayReevaluatesPrerequisites() {
        val harness = TestBluetoothPrerequisitesHarness(missingPermissionsState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.prerequisiteStatusContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.prerequisiteStatusHint))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.bluetooth_prereq_retry_hint)))
            harness.updateState(readyState())

            onView(withId(R.id.prerequisiteStatusContainer)).perform(click())

            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
                .check(matches(withContentDescription(nullValue(CharSequence::class.java))))
                .check(matches(not(isFocusable())))
                .check(matches(not(isClickable())))
            onView(withId(R.id.prerequisiteStatusHint))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun overlayRestoresDescriptionWhenStateBecomesMissingAgain() {
        val harness = TestBluetoothPrerequisitesHarness(readyState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))

            harness.updateState(missingPermissionsState())
            waitForStatePropagation()

            onView(withId(R.id.prerequisiteStatusContainer))
                .check(matches(isDisplayed()))
                .check(matches(withContentDescription(expectedOverlayDescription(R.string.bluetooth_prereq_missing_permissions))))
                .check(matches(isFocusable()))
                .check(matches(isClickable()))
        }
    }

    @Test
    fun requestsObdConnectWhenPrerequisitesReady() {
        val harness = TestBluetoothPrerequisitesHarness(readyState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForStatePropagation()

            assertEquals(1, obdHarness.connectAttempts)
            assertEquals(1, obdHarness.discoveryStarts)
        }
    }

    @Test
    fun requestsObdDisconnectWhenPrerequisitesLost() {
        val harness = TestBluetoothPrerequisitesHarness(readyState())
        BluetoothPrerequisitesEntryPoint.override { harness.presenter }

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForStatePropagation()
            obdHarness.resetCounters()

            harness.updateState(missingPermissionsState())
            waitForStatePropagation()

            assertEquals(1, obdHarness.disconnectAttempts)
            assertEquals(1, obdHarness.discoveryStops)
        }
    }
}

private fun expectedOverlayDescription(messageResId: Int): String {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val message = context.getString(messageResId)
    val hint = context.getString(R.string.bluetooth_prereq_retry_hint)
    return "$message $hint"
}

private class TestObdConnectionHarness {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val time = AtomicLong(0L)
    private val adapter = ConnectedAdapter(
        device = BleDevice(address = "12:34:56:78:90:AB", name = "TestAdapter", rssi = -45),
        protocol = "BLE"
    )
    private val sessionGateway = RecordingSessionGateway()
    private val adapterDiscovery = RecordingAdapterDiscovery(adapter)
    private val manager = ObdConnectionManager(
        sessionGateway = sessionGateway,
        adapterDiscovery = adapterDiscovery,
        dispatchers = TestDispatchersProvider,
        timeProvider = { time.incrementAndGet() }
    )

    val controller: ObdConnectionController = ObdConnectionController(
        manager = manager,
        scope = scope,
        dispatchers = TestDispatchersProvider,
        logTag = "TestObdConnectionHarness"
    )

    val connectAttempts: Int
        get() = sessionGateway.startCalls

    val disconnectAttempts: Int
        get() = sessionGateway.cancelCalls

    val discoveryStarts: Int
        get() = adapterDiscovery.startCalls

    val discoveryStops: Int
        get() = adapterDiscovery.stopCalls

    fun resetCounters() {
        sessionGateway.reset()
        adapterDiscovery.reset()
    }

    fun shutdown() {
        controller.disconnectAsync()
        scope.cancel()
        manager.shutdown()
    }

    private class RecordingSessionGateway : ObdConnectionManager.SessionGateway {
        private val backingState = MutableStateFlow<ObdSessionState>(ObdSessionState.Idle)
        var startCalls: Int = 0
            private set
        var cancelCalls: Int = 0
            private set

        override val state: StateFlow<ObdSessionState> = backingState

        override suspend fun startWithAdapter(
            adapter: ConnectedAdapter,
            scannerConfig: BleScannerConfig
        ): ObdSessionState {
            startCalls += 1
            val next = ObdSessionState.Diagnostics(adapter.device)
            backingState.value = next
            return next
        }

        override suspend fun cancel() {
            cancelCalls += 1
            backingState.value = ObdSessionState.Idle
        }

        fun reset() {
            startCalls = 0
            cancelCalls = 0
            backingState.value = ObdSessionState.Idle
        }
    }

    private class RecordingAdapterDiscovery(
        private val adapter: ConnectedAdapter
    ) : ObdConnectionManager.AdapterDiscovery {
        var startCalls: Int = 0
            private set
        var stopCalls: Int = 0
            private set

        override suspend fun start(config: BleScannerConfig) {
            startCalls += 1
        }

        override fun latest(config: BleScannerConfig): ConnectedAdapter? = adapter

        override suspend fun await(
            config: BleScannerConfig,
            timeoutMs: Long
        ): ConnectedAdapter? = adapter

        override suspend fun stop() {
            stopCalls += 1
        }

        override fun close() {
            stopCalls += 1
        }

        fun reset() {
            startCalls = 0
            stopCalls = 0
        }
    }
}

private class TestBluetoothPrerequisitesHarness(
    initialState: BluetoothEnvironmentState
) {
    private val repository = FakeBluetoothEnvironmentRepository(initialState)
    private val timestamp = AtomicLong(0L)
    private val useCase = BluetoothPrerequisitesUseCase(repository) { timestamp.incrementAndGet() }
    private val monitor = BluetoothPrerequisitesMonitor(
        useCase = useCase,
        dispatchers = TestDispatchersProvider,
        pollIntervalMillis = 10L
    )

    val presenter: BluetoothPrerequisitePresenter = BluetoothPrerequisitePresenter(useCase, monitor)

    fun updateState(state: BluetoothEnvironmentState) {
        repository.update(state)
    }
}

private class FakeBluetoothEnvironmentRepository(
    initialState: BluetoothEnvironmentState
) : BluetoothEnvironmentRepository {

    private val stateRef = AtomicReference(initialState)

    override fun snapshot(): BluetoothEnvironmentState = stateRef.get()

    override fun status() = stateRef.get().toStatus()

    fun update(state: BluetoothEnvironmentState) {
        stateRef.set(state)
    }
}

private object TestDispatchersProvider : DispatchersProvider {
    override val io = Dispatchers.Unconfined
    override val computation = Dispatchers.Unconfined
    override val main = Dispatchers.Unconfined
}

private fun waitForStatePropagation() {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    Thread.sleep(50)
}

private fun missingPermissionsState(): BluetoothEnvironmentState {
    return BluetoothEnvironmentState(
        permissionStatus = BluetoothPermissionHelper.PermissionStatus.Missing(
            listOf("android.permission.BLUETOOTH_CONNECT")
        ),
        bluetoothEnabled = false,
        locationEnabled = false
    )
}

private fun bluetoothDisabledState(): BluetoothEnvironmentState {
    return BluetoothEnvironmentState(
        permissionStatus = BluetoothPermissionHelper.PermissionStatus.Granted,
        bluetoothEnabled = false,
        locationEnabled = true
    )
}

private fun locationDisabledState(): BluetoothEnvironmentState {
    return BluetoothEnvironmentState(
        permissionStatus = BluetoothPermissionHelper.PermissionStatus.Granted,
        bluetoothEnabled = true,
        locationEnabled = false
    )
}

private fun readyState(): BluetoothEnvironmentState {
    return BluetoothEnvironmentState(
        permissionStatus = BluetoothPermissionHelper.PermissionStatus.Granted,
        bluetoothEnabled = true,
        locationEnabled = true
    )
}
