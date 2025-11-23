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
import com.selfservice.kiosk.selftest.PassThruSelfTestRuntime
import com.selfservice.kiosk.selftest.PassThruSelfTestRuntimeProvider
import com.selfservice.obd.core.dictionary.DictionaryRevisionSnapshot
import com.selfservice.obd.core.dictionary.DictionaryUpdateBatch
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.pid.ObdPidDefinition
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.nullValue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityDictionaryStatusTest {

    @After
    fun tearDown() {
        PassThruSelfTestRuntimeProvider.resetOverride()
    }

    @Test
    fun showsRunningStatusWhileSyncIsInProgress() {
        val provider = ScriptedDictionaryUpdateProvider()
        PassThruSelfTestRuntimeProvider.override { config ->
            PassThruSelfTestRuntime(
                    context = config.context,
                    dispatchers = config.dispatchers,
                    dictionaryUpdateProvider = provider,
                    metadataProvider = config.metadataProvider,
                    dictionarySchedule = null,
                    dictionaryPolicy = config.dictionaryPolicy,
                    bridgeProvider = config.bridgeProvider,
                    controllerBuilder = config.controllerBuilder,
                    stepHandlerFactory = config.stepHandlerFactory
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val app = instrumentationApp()

            app.passThruSelfTestRuntime.requestDictionarySync(force = true)
            provider.awaitStart()
            waitForStatePropagation()

            onView(withId(R.id.dictionaryStatusContainer))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
                .check(matches(isFocusable()))
                .check(matches(withContentDescription(expectedDictionaryDescription(
                        InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.dictionary_sync_running)
                ))))
            onView(withId(R.id.dictionaryStatusText))
                .check(matches(withText(R.string.dictionary_sync_running)))
            onView(withId(R.id.dictionaryStatusHint))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.dictionary_sync_touch_hint)))
            onView(withId(R.id.dictionaryStatusProgress))
                .check(matches(isDisplayed()))

            provider.deliverSuccess(sampleDictionaryUpdateBatch())
            waitForStatePropagation()

            onView(withId(R.id.dictionaryStatusContainer))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
                .check(matches(withContentDescription(nullValue(CharSequence::class.java))))
        }
    }

    @Test
    fun showsErrorStatusAndAllowsManualRetry() {
        val provider = ScriptedDictionaryUpdateProvider()
        PassThruSelfTestRuntimeProvider.override { config ->
            PassThruSelfTestRuntime(
                    context = config.context,
                    dispatchers = config.dispatchers,
                    dictionaryUpdateProvider = provider,
                    metadataProvider = config.metadataProvider,
                    dictionarySchedule = null,
                    dictionaryPolicy = config.dictionaryPolicy,
                    bridgeProvider = config.bridgeProvider,
                    controllerBuilder = config.controllerBuilder,
                    stepHandlerFactory = config.stepHandlerFactory
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            val app = instrumentationApp()
            app.passThruSelfTestRuntime.requestDictionarySync(force = true)
            provider.awaitStart()
            provider.deliverFailure(RuntimeException("Network error"))
            waitForStatePropagation()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val failureText = context.getString(R.string.dictionary_sync_failed, "Network error")

            onView(withId(R.id.dictionaryStatusContainer))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
                .check(matches(isFocusable()))
                .check(matches(withContentDescription(expectedDictionaryDescription(failureText))))
            onView(withId(R.id.dictionaryStatusText))
                .check(matches(withText(failureText)))
            onView(withId(R.id.dictionaryStatusHint))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.dictionary_sync_touch_hint)))
            onView(withId(R.id.dictionaryStatusProgress))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))

            onView(withId(R.id.dictionaryStatusContainer)).perform(click())
            provider.awaitStart()
            provider.deliverSuccess(sampleDictionaryUpdateBatch())
            waitForStatePropagation()

            onView(withId(R.id.dictionaryStatusContainer))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
                .check(matches(withContentDescription(nullValue(CharSequence::class.java))))
            onView(withId(R.id.dictionaryStatusProgress))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    private fun instrumentationApp(): KioskApp {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        return app as? KioskApp ?: error("Application context is not KioskApp")
    }
}

private class ScriptedDictionaryUpdateProvider : DictionaryUpdateProvider {
    private val startSignals = Channel<Unit>(Channel.UNLIMITED)
    private val outcomes = Channel<Result<DictionaryUpdateBatch?>>(Channel.UNLIMITED)

    override suspend fun fetchUpdate(current: DictionaryRevisionSnapshot): DictionaryUpdateBatch? {
        startSignals.send(Unit)
        val result = outcomes.receive()
        return result.getOrThrow()
    }

    fun awaitStart() {
        runBlocking { startSignals.receive() }
    }

    fun deliverSuccess(batch: DictionaryUpdateBatch) {
        runBlocking { outcomes.send(Result.success(batch)) }
    }

    fun deliverFailure(error: Throwable) {
        runBlocking { outcomes.send(Result.failure(error)) }
    }
}

private fun sampleDictionaryUpdateBatch(): DictionaryUpdateBatch {
    val pidDefinition = ObdPidDefinition(mode = "01", pid = "0C", label = "Engine RPM")
    val dtcDefinition = ObdDtcDefinition(
        code = "P0001",
        system = ObdDtcDefinition.System.POWERTRAIN,
        label = "Mock fault"
    )
    return DictionaryUpdateBatch(
        pidDefinitions = listOf(pidDefinition),
        dtcDefinitions = listOf(dtcDefinition),
        source = "instrumentation",
        pidVersionLabel = "pid-test",
        dtcVersionLabel = "dtc-test"
    )
}

private fun expectedDictionaryDescription(message: String): String {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val hint = context.getString(R.string.dictionary_sync_touch_hint)
    return "$message $hint"
}

private fun waitForStatePropagation() {
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    Thread.sleep(50)
}
