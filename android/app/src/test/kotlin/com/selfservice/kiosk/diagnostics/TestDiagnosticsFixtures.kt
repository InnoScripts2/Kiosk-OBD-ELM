package com.selfservice.kiosk.diagnostics

import com.selfservice.kiosk.KioskApp
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricProfileRepository
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrendRecorder
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationEngine
import kotlinx.coroutines.flow.MutableStateFlow

internal fun KioskApp.installDiagnosticsDefinitions(vararg definitions: DiagnosticsMetricDefinition) {
    installDiagnosticsDefinitions(definitions.toList())
}

internal fun KioskApp.installDiagnosticsDefinitions(definitions: List<DiagnosticsMetricDefinition>) {
    val repositoryField = KioskApp::class.java.getDeclaredField("diagnosticsMetricRepository").apply {
        isAccessible = true
    }
    repositoryField.set(this, DiagnosticsMetricProfileRepository(definitions))

    val engineField = KioskApp::class.java.getDeclaredField("diagnosticsRecommendationEngine").apply {
        isAccessible = true
    }
    engineField.set(this, DiagnosticsRecommendationEngine())

    resetDiagnosticsState()
}

internal fun KioskApp.clearDiagnosticsDefinitions() {
    val repositoryField = KioskApp::class.java.getDeclaredField("diagnosticsMetricRepository").apply {
        isAccessible = true
    }
    repositoryField.set(this, null)

    val engineField = KioskApp::class.java.getDeclaredField("diagnosticsRecommendationEngine").apply {
        isAccessible = true
    }
    engineField.set(this, null)

    resetDiagnosticsState()
}

private fun KioskApp.resetDiagnosticsState() {
    val snapshotField = KioskApp::class.java.getDeclaredField("diagnosticsMetricSnapshot").apply {
        isAccessible = true
    }
    @Suppress("UNCHECKED_CAST")
    val snapshotState = snapshotField.get(this) as MutableStateFlow<DiagnosticsProfileSnapshot?>
    snapshotState.value = null

    val recommendationsField = KioskApp::class.java.getDeclaredField("diagnosticsRecommendations").apply {
        isAccessible = true
    }
    @Suppress("UNCHECKED_CAST")
    val recommendationsState = recommendationsField.get(this) as MutableStateFlow<List<DiagnosticsRecommendation>>
    recommendationsState.value = emptyList()

    val trendsField = KioskApp::class.java.getDeclaredField("diagnosticsMetricTrends").apply {
        isAccessible = true
    }
    @Suppress("UNCHECKED_CAST")
    val trendsState = trendsField.get(this) as MutableStateFlow<Map<String, DiagnosticsMetricTrend>>
    trendsState.value = emptyMap()

    val recorderField = KioskApp::class.java.getDeclaredField("diagnosticsMetricTrendRecorder").apply {
        isAccessible = true
    }
    val recorder = recorderField.get(this) as DiagnosticsMetricTrendRecorder
    recorder.reset()
}
