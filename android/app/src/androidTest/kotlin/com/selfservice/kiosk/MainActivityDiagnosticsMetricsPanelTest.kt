package com.selfservice.kiosk

package com.selfservice.kiosk

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isSelected
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.core.protocol.ObdPidSample
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricProfileRepository
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrendRecorder
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityDiagnosticsMetricsPanelTest {

    @Test
    fun panelReflectsSnapshotLifecycle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val app = instrumentationApp()

            scenario.onActivity {
                val definition = DiagnosticsMetricDefinition(
                    id = "control_module_voltage",
                    mode = "01",
                    pid = "42",
                    label = "Напряжение",
                    unit = "V",
                    thresholds = DiagnosticsMetricThresholds(
                        warningLow = 11.5,
                        criticalLow = 11.0,
                        warningHigh = 14.5,
                        criticalHigh = 15.0
                    ),
                    advice = DiagnosticsMetricAdvice(
                        messages = emptyMap(),
                        defaultMessage = "Проверьте питание",
                        noDataMessage = "Нет данных"
                    )
                )
                app.installDiagnosticsDefinitions(definition)
            }

            instrumentation.waitForIdleSync()

            onView(withId(R.id.diagnosticsMetricsPanel))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))

            scenario.onActivity {
                val sample = ObdPidSample(
                    definition = ObdPidDefinition(
                        mode = "01",
                        pid = "42",
                        label = "Напряжение",
                        unit = "V"
                    ),
                    rawPayload = byteArrayOf(0x00.toByte()),
                    rawHex = "00",
                    value = 12.6,
                    unit = "V",
                    timestampMillis = 1_000L
                )
                app.evaluateDiagnosticsMetrics(listOf(sample))
            }

            instrumentation.waitForIdleSync()

            val context = instrumentation.targetContext
            val expectedStatus = context.getString(R.string.diagnostics_metric_status_ok)

            onView(withId(R.id.diagnosticsMetricsPanel))
                .check(matches(isDisplayed()))
            onView(withText("Напряжение"))
                .check(matches(isDisplayed()))
            onView(withText(expectedStatus))
                .check(matches(isDisplayed()))

            scenario.onActivity {
                app.resetDiagnosticsMetrics()
                app.clearDiagnosticsDefinitions()
            }

            instrumentation.waitForIdleSync()

            onView(withId(R.id.diagnosticsMetricsPanel))
                .check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun recommendationSelectionSynchronizesHighlighting() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val app = instrumentationApp()

            scenario.onActivity {
                val definition = DiagnosticsMetricDefinition(
                    id = "engine_coolant_temperature",
                    mode = "01",
                    pid = "05",
                    label = "Температура охлаждающей жидкости",
                    unit = "°C",
                    thresholds = DiagnosticsMetricThresholds(
                        warningHigh = 105.0,
                        criticalHigh = 115.0
                    ),
                    advice = DiagnosticsMetricAdvice(
                        messages = mapOf(
                            DiagnosticsMetricStatus.WARNING_HIGH to "Температура выше нормы",
                            DiagnosticsMetricStatus.CRITICAL_HIGH to "Критический перегрев"
                        ),
                        defaultMessage = "Проверьте систему охлаждения",
                        noDataMessage = "Нет данных"
                    )
                )
                app.installDiagnosticsDefinitions(definition)
            }

            instrumentation.waitForIdleSync()

            scenario.onActivity {
                val sample = ObdPidSample(
                    definition = ObdPidDefinition(
                        mode = "01",
                        pid = "05",
                        label = "Температура охлаждающей жидкости",
                        unit = "°C"
                    ),
                    rawPayload = byteArrayOf(0x00.toByte()),
                    rawHex = "00",
                    value = 120.0,
                    unit = "°C",
                    timestampMillis = 2_000L
                )
                app.evaluateDiagnosticsMetrics(listOf(sample))
            }

            instrumentation.waitForIdleSync()

            val recommendationMatcher = allOf(
                withId(R.id.recommendationTileRoot),
                hasDescendant(withText("Температура охлаждающей жидкости"))
            )
            val metricMatcher = allOf(
                withId(R.id.metricTileRoot),
                hasDescendant(withText("Температура охлаждающей жидкости"))
            )

            onView(recommendationMatcher).check(matches(isDisplayed()))
            onView(metricMatcher).check(matches(isDisplayed()))

            onView(recommendationMatcher).perform(click())
            onView(metricMatcher).check(matches(isSelected()))
            onView(recommendationMatcher).check(matches(isSelected()))

            onView(recommendationMatcher).perform(click())
            onView(metricMatcher).check(matches(not(isSelected())))
            onView(recommendationMatcher).check(matches(not(isSelected())))

            onView(metricMatcher).perform(click())
            onView(metricMatcher).check(matches(isSelected()))
            onView(recommendationMatcher).check(matches(isSelected()))

            scenario.onActivity {
                app.resetDiagnosticsMetrics()
                app.clearDiagnosticsDefinitions()
            }
        }
    }

    private fun instrumentationApp(): KioskApp {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        return context as? KioskApp ?: error("Application context is not KioskApp")
    }
}

private fun KioskApp.installDiagnosticsDefinitions(vararg definitions: DiagnosticsMetricDefinition) {
    val repositoryField = KioskApp::class.java.getDeclaredField("diagnosticsMetricRepository")
    repositoryField.isAccessible = true
    repositoryField.set(this, DiagnosticsMetricProfileRepository(definitions.toList()))

    val snapshotField = KioskApp::class.java.getDeclaredField("diagnosticsMetricSnapshot")
    snapshotField.isAccessible = true
    val state = snapshotField.get(this) as MutableStateFlow<*>
    @Suppress("UNCHECKED_CAST")
    (state as MutableStateFlow<DiagnosticsProfileSnapshot?>).value = null

    val trendsField = KioskApp::class.java.getDeclaredField("diagnosticsMetricTrends")
    trendsField.isAccessible = true
    val trendsState = trendsField.get(this) as MutableStateFlow<*>
    @Suppress("UNCHECKED_CAST")
    (trendsState as MutableStateFlow<Map<String, DiagnosticsMetricTrend>>).value = emptyMap()

    val recorderField = KioskApp::class.java.getDeclaredField("diagnosticsMetricTrendRecorder")
    recorderField.isAccessible = true
    val recorder = recorderField.get(this) as DiagnosticsMetricTrendRecorder
    recorder.reset()
}

private fun KioskApp.clearDiagnosticsDefinitions() {
    val repositoryField = KioskApp::class.java.getDeclaredField("diagnosticsMetricRepository")
    repositoryField.isAccessible = true
    repositoryField.set(this, null)

    val snapshotField = KioskApp::class.java.getDeclaredField("diagnosticsMetricSnapshot")
    snapshotField.isAccessible = true
    val state = snapshotField.get(this) as MutableStateFlow<*>
    @Suppress("UNCHECKED_CAST")
    (state as MutableStateFlow<DiagnosticsProfileSnapshot?>).value = null

    val trendsField = KioskApp::class.java.getDeclaredField("diagnosticsMetricTrends")
    trendsField.isAccessible = true
    val trendsState = trendsField.get(this) as MutableStateFlow<*>
    @Suppress("UNCHECKED_CAST")
    (trendsState as MutableStateFlow<Map<String, DiagnosticsMetricTrend>>).value = emptyMap()

    val recorderField = KioskApp::class.java.getDeclaredField("diagnosticsMetricTrendRecorder")
    recorderField.isAccessible = true
    val recorder = recorderField.get(this) as DiagnosticsMetricTrendRecorder
    recorder.reset()
}
