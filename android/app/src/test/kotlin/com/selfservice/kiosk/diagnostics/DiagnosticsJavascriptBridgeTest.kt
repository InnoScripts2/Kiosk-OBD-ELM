package com.selfservice.kiosk.diagnostics

import com.autoservice.diagnostics.obd.DiagnosticsPreviewRuntime
import com.autoservice.diagnostics.obd.ObdProtocol
import com.autoservice.diagnostics.obd.ObdSample
import com.autoservice.diagnostics.obd.StandardPids
import com.selfservice.feature.reports.DiagnosticsReport
import com.selfservice.feature.reports.DiagnosticsReportCustomer
import com.selfservice.feature.reports.DiagnosticsReportInput
import com.selfservice.feature.reports.DiagnosticsReportVehicle
import com.selfservice.kiosk.KioskApp.DiagnosticsConnectionOptions
import com.selfservice.kiosk.KioskApp.DiagnosticsMetricEvaluation
import com.selfservice.kiosk.diagnostics.DiagnosticsReportWorkflow
import com.selfservice.obd.core.dictionary.ObdDictionaryManager
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsRequest
import com.selfservice.obd.core.diagnostics.ObdDiagnosticsResult
import com.selfservice.obd.core.dtc.ObdDtcDefinition
import com.selfservice.obd.core.protocol.ObdDtcBatch
import com.selfservice.obd.core.protocol.ObdDtcEntry
import com.selfservice.obd.core.passthru.PassThruDiagnosticsCoordinator
import com.selfservice.obd.core.pid.ObdPidDefinition
import com.selfservice.obd.core.protocol.ObdPidSample
import com.selfservice.obd.core.session.ObdSessionState
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricAdvice
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricDefinition
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricInsight
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricStatus
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricThresholds
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsProfileSnapshot
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrend
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsMetricTrendPoint
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendation
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationPriority
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationSeverity
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThreshold
import com.selfservice.platform.data.diagnostics.profile.DiagnosticsRecommendationThresholdType
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryRecord
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetryStore
import com.selfservice.platform.data.diagnostics.DiagnosticsTelemetrySummary
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONObject

class DiagnosticsJavascriptBridgeTest {

    @Test
    fun runReportParsesPayloadAndEmitsSuccess() = runTest {
        val dispatcher = RecordingDispatcher()
        val runnerArgs = CapturedArgs()
        val sample = sampleResult()
        val runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { request, metadata, options ->
            runnerArgs.request = request
            runnerArgs.metadata = metadata
            runnerArgs.options = options
            sample
        }
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = runner,
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        val message = """
            {
              "requestId": "req-1",
              "action": "run_report",
              "payload": {
                "vehicle": {
                  "make": "Tesla",
                  "model": "Model S",
                  "year": 2022,
                  "vin": "5YJSA1E26HF000000"
                },
                "customer": {
                  "phone": "+70000000000",
                  "email": "owner@example.com"
                },
                "request": {
                  "readTroubleCodes": true,
                  "clearTroubleCodes": true,
                  "responseTimeoutMs": 2000,
                  "vehicleManufacturer": "TESLA",
                  "pidRequests": [
                    {
                      "mode": "0x01",
                      "pid": "0x0C",
                      "timeoutMs": 900
                    }
                  ]
                },
                "connection": {
                                    "forceReconnect": true,
                                    "adapter": {
                                        "address": "AA:BB:CC:DD:EE:FF",
                                        "name": "PassThru",
                                        "rssi": -58,
                                        "protocol": "J2534"
                                    }
                }
              }
            }
        """.trimIndent()

        bridge.postMessage(message)
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"), "Expected success event but got $detail")
        assertEquals("req-1", detail.getString("requestId"))
        val data = detail.getJSONObject("data")
        assertEquals(sample.sessionId, data.getString("sessionId"))
        val report = data.getJSONObject("report")
        val expectedPdf = Base64.getEncoder().encodeToString(sample.report.pdfBytes)
        assertEquals(expectedPdf, report.getString("pdfBase64"))
        val snapshot = data.getJSONObject("snapshot")
        assertEquals(sample.reportInput.snapshot.metrics.size, snapshot.getJSONArray("metrics").length())
        val diagnostics = data.getJSONObject("diagnostics")
        assertEquals("completed", diagnostics.getJSONObject("sessionState").getString("state"))
        val pidSamples = diagnostics.getJSONArray("pidSamples")
        assertEquals(sample.diagnosticsRun.diagnostics.pidSamples.size, pidSamples.length())
        val pidSampleJson = pidSamples.getJSONObject(0)
        assertEquals("01:05", pidSampleJson.getString("pidKey"))

        val capturedRequest = requireNotNull(runnerArgs.request)
        assertTrue(capturedRequest.clearTroubleCodes)
        assertEquals("TESLA", capturedRequest.vehicleManufacturer)
        assertEquals(1, capturedRequest.pidRequests.size)
        val pidRequest = capturedRequest.pidRequests.first()
        assertEquals("0x01", pidRequest.mode)
        assertEquals("0x0C", pidRequest.pid)
        assertEquals(900L, pidRequest.timeoutMillis)

        val capturedMetadata = requireNotNull(runnerArgs.metadata)
        assertEquals("Tesla", capturedMetadata.vehicle?.make)
        assertEquals("owner@example.com", capturedMetadata.customer?.email)

        val capturedOptions = requireNotNull(runnerArgs.options)
        assertTrue(capturedOptions.forceReconnect)
        val adapter = requireNotNull(capturedOptions.adapter)
        assertEquals("AA:BB:CC:DD:EE:FF", adapter.device.address)
        assertEquals("PassThru", adapter.device.name)
        assertEquals(-58, adapter.device.rssi)
        assertEquals("J2534", adapter.protocol)
    }

    @Test
    fun invalidJsonEmitsError() = runTest {
        val dispatcher = RecordingDispatcher()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        bridge.postMessage(null)

        val detail = dispatcher.last()
        assertFalse(detail.getBoolean("ok"))
        val error = detail.getJSONObject("error")
        assertEquals("invalid_request", error.getString("code"))
    }

    @Test
    fun getLastReportReturnsErrorWhenMissing() = runTest {
        val dispatcher = RecordingDispatcher()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        bridge.postMessage("""{"requestId":"req-2","action":"get_last_report","payload":{}}""".trimIndent())
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertFalse(detail.getBoolean("ok"))
        val error = detail.getJSONObject("error")
        assertEquals("not_ready", error.getString("code"))
    }

    @Test
    fun getLastReportReturnsLatestResult() = runTest {
        val dispatcher = RecordingDispatcher()
        val sample = sampleResult()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sample },
            latestReportProvider = { sample },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        bridge.postMessage("""{"requestId":"req-3","action":"get_last_report"}""".trimIndent())
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        assertEquals("req-3", detail.getString("requestId"))
        val data = detail.getJSONObject("data")
        assertEquals(sample.sessionId, data.getString("sessionId"))
        val report = data.getJSONObject("report")
        assertEquals(sample.report.html, report.getString("html"))
        assertEquals(sample.report.pdfBytes.size, report.getInt("pdfBytesLength"))
        val diagnostics = data.getJSONObject("diagnostics")
        assertEquals("completed", diagnostics.getJSONObject("sessionState").getString("state"))
    }

    @Test
    fun runReportIncludesPidSampleMetadata() = runTest {
        val dispatcher = RecordingDispatcher()
        val sample = sampleResult()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sample },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        bridge.postMessage("""{"requestId":"meta-1","action":"run_report","payload":{}}""".trimIndent())
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val diagnostics = detail.getJSONObject("data").getJSONObject("diagnostics")
        val pidSample = diagnostics
            .getJSONArray("pidSamples")
            .getJSONObject(0)
        assertEquals("01:05", pidSample.getString("pidKey"))
        assertEquals("Engine coolant temperature", pidSample.getString("description"))
        assertEquals("°C", pidSample.getString("expectedUnit"))
        assertEquals("°C", pidSample.getString("unit"))
        assertEquals(100.0, pidSample.getDouble("value"), 0.0)
        assertTrue(pidSample.getBoolean("valid"))
        assertEquals("64", pidSample.getString("rawResponse"))
        val thresholds = pidSample.getJSONObject("thresholds")
        assertEquals(115.0, thresholds.getDouble("criticalHigh"), 0.0)
        assertEquals(105.0, thresholds.getDouble("warningHigh"), 0.0)
    }

    @Test
    fun runReportIncludesMetricTrends() = runTest {
        val dispatcher = RecordingDispatcher()
        val sample = sampleResult()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sample },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        bridge.postMessage("""{"requestId":"trend-1","action":"run_report","payload":{}}""".trimIndent())
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val snapshot = detail.getJSONObject("data").getJSONObject("snapshot")
        val metric = snapshot.getJSONArray("metrics").getJSONObject(0)
        assertTrue(metric.has("trend"))
        assertEquals("01:05", metric.getString("pidKey"))
        assertEquals("general", metric.getString("category"))
        assertEquals(500L, metric.getLong("sourceTimestampMillis"))
        val trend = metric.getJSONObject("trend")
        assertEquals("engine_temp", trend.getString("metricId"))
        val points = trend.getJSONArray("points")
        assertEquals(2, points.length())
        val firstPoint = points.getJSONObject(0)
        assertEquals(500L, firstPoint.getLong("timestampMillis"))
        assertEquals(98.0, firstPoint.getDouble("value"), 0.0)
    }

    @Test
    fun busyErrorWhenDiagnosticsAlreadyRunning() = runTest {
        val dispatcher = RecordingDispatcher()
        val startSignal = CompletableDeferred<Unit>()
        val finishSignal = CompletableDeferred<Unit>()
        val runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ ->
            startSignal.complete(Unit)
            finishSignal.await()
            sampleResult()
        }
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = runner,
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        val payload = """{"requestId":"run-1","action":"run_report","payload":{}}""".trimIndent()
        bridge.postMessage(payload)
        startSignal.await()

        bridge.postMessage("""{"requestId":"run-2","action":"run_report","payload":{}}""".trimIndent())

        val busyDetail = dispatcher.last()
        assertFalse(busyDetail.getBoolean("ok"))
        assertEquals("run-2", busyDetail.getString("requestId"))
        assertEquals("busy", busyDetail.getJSONObject("error").getString("code"))

        finishSignal.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, dispatcher.events.size)
    }

    @Test
    fun cancelReportStopsActiveRun() = runTest {
        val dispatcher = RecordingDispatcher()
        var invocationCount = 0
        val startSignal = CompletableDeferred<Unit>()
        val runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ ->
            invocationCount += 1
            if (invocationCount == 1) {
                startSignal.complete(Unit)
                suspendCancellableCoroutine<DiagnosticsReportWorkflow.Result> { _ -> }
            } else {
                sampleResult()
            }
        }
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = runner,
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        val payload = """{"requestId":"run-1","action":"run_report","payload":{}}""".trimIndent()
        bridge.postMessage(payload)
        startSignal.await()

        bridge.postMessage("""{"requestId":"cancel-1","action":"cancel_report","payload":{}}""".trimIndent())
        advanceUntilIdle()

        val cancelDetail = dispatcher.last()
        assertTrue(cancelDetail.getBoolean("ok"))
        assertEquals("cancel-1", cancelDetail.getString("requestId"))
        assertTrue(cancelDetail.getJSONObject("data").getBoolean("cancelled"))

        val secondPayload = """{"requestId":"run-2","action":"run_report","payload":{}}""".trimIndent()
        bridge.postMessage(secondPayload)
        advanceUntilIdle()

        val secondDetail = dispatcher.last()
        assertTrue(secondDetail.getBoolean("ok"))
        assertEquals("run-2", secondDetail.getString("requestId"))
    }

    @Test
    fun cancelReportWithoutActiveRunEmitsError() = runTest {
        val dispatcher = RecordingDispatcher()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
        )

        bridge.postMessage("""{"requestId":"cancel-1","action":"cancel_report","payload":{}}""".trimIndent())

        val detail = dispatcher.last()
        assertFalse(detail.getBoolean("ok"))
        val error = detail.getJSONObject("error")
        assertEquals("not_running", error.getString("code"))
    }

    @Test
    fun previewSnapshotEmitsSamples() = runTest {
        val dispatcher = RecordingDispatcher()
        var capturedRequest: DiagnosticsPreviewController.Request? = null
        val snapshot = DiagnosticsPreviewRuntime.Snapshot(
            generatedAtMillis = 1_234L,
            protocol = ObdProtocol.ISO_15765_4_CAN_11_500,
            samples = listOf(
                ObdSample(
                    pid = StandardPids.ENGINE_RPM,
                    value = 1726.0,
                    unit = "rpm",
                    rawResponse = "41 0C 1A F8",
                    timestampMillis = 1_200L,
                )
            )
        )
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = DiagnosticsJavascriptBridge.DiagnosticsPreviewRunner { request ->
                capturedRequest = request
                snapshot
            },
        )

        val payload = """
            {
              "requestId":"preview-1",
              "action":"preview_snapshot",
              "payload":{
                "protocol":"iso_15765_4_can_11_500",
                "pidSelectors":[{"mode":"0x01","pid":"0x0C"}]
              }
            }
        """.trimIndent()

        bridge.postMessage(payload)
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val data = detail.getJSONObject("data")
        val snapshotJson = data.getJSONObject("snapshot")
        assertEquals("iso_15765_4_can_11_500", snapshotJson.getString("protocol"))
        assertEquals(1, snapshotJson.getJSONArray("samples").length())
        val sampleJson = snapshotJson.getJSONArray("samples").getJSONObject(0)
        assertEquals("01:0C", sampleJson.getString("pidKey"))
        assertFalse(sampleJson.has("thresholds"))
        val request = capturedRequest
        assertNotNull(request)
        assertEquals(ObdProtocol.ISO_15765_4_CAN_11_500, request.protocol)
        val selectors = requireNotNull(request.pidSelectors)
        assertEquals(1, selectors.size)
        assertEquals("0x01", selectors.first().mode)
        assertEquals("0x0C", selectors.first().pid)
    }

    @Test
    fun previewSnapshotIncludesThresholdsWhenAvailable() = runTest {
        val dispatcher = RecordingDispatcher()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = DiagnosticsJavascriptBridge.DiagnosticsPreviewRunner {
                samplePreviewSnapshot()
            },
        )

        val payload = """
            {
              "requestId":"preview-thresholds",
              "action":"preview_snapshot",
              "payload":{
                "protocol":"auto",
                "pidSelectors":[{"mode":"0x01","pid":"0x05"}]
              }
            }
        """.trimIndent()

        bridge.postMessage(payload)
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val data = detail.getJSONObject("data")
        val snapshotJson = data.getJSONObject("snapshot")
        val sampleJson = snapshotJson.getJSONArray("samples").getJSONObject(0)
        assertEquals("01:05", sampleJson.getString("pidKey"))
        val thresholds = sampleJson.getJSONObject("thresholds")
        assertEquals(105.0, thresholds.getDouble("warningHigh"), 0.0)
        assertEquals(115.0, thresholds.getDouble("criticalHigh"), 0.0)
        assertFalse(thresholds.has("warningLow"))
        assertFalse(thresholds.has("criticalLow"))
    }

    @Test
    fun previewSnapshotWithUnknownProtocolEmitsError() = runTest {
        val dispatcher = RecordingDispatcher()
        var invoked = false
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = DiagnosticsJavascriptBridge.DiagnosticsPreviewRunner {
                invoked = true
                samplePreviewSnapshot()
            },
        )

        val payload = """
            {
              "requestId":"preview-err",
              "action":"preview_snapshot",
              "payload":{
                "protocol":"invalid",
                "pidSelectors":[{"mode":"0x01","pid":"0x05"}]
              }
            }
        """.trimIndent()

        bridge.postMessage(payload)
        advanceUntilIdle()

        assertFalse(invoked)
        val detail = dispatcher.last()
        assertFalse(detail.getBoolean("ok"))
        val error = detail.getJSONObject("error")
        assertEquals("invalid_argument", error.getString("code"))
    }

    @Test
    fun dictionaryLookupReturnsDefinitions() = runTest {
        val dispatcher = RecordingDispatcher()
        val dictionary = ObdDictionaryManager.default().apply {
            refreshPid(
                definitions = sequenceOf(
                    ObdPidDefinition(
                        mode = "0x01",
                        pid = "0x0C",
                        label = "Engine RPM",
                        unit = "rpm",
                        min = 0.0,
                        max = 8_000.0,
                        notes = "Crankshaft speed",
                    )
                ),
                source = "test",
                versionLabel = "pid-v1",
            )
            refreshDtc(
                definitions = sequenceOf(
                    ObdDtcDefinition(
                        code = "P0420",
                        system = ObdDtcDefinition.System.POWERTRAIN,
                        label = "Catalyst efficiency",
                        notes = "Bank 1"
                    )
                ),
                source = "test",
                versionLabel = "dtc-v1",
            )
        }
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = dictionary,
        )

        val payload = """
            {
              "requestId":"dict-1",
              "action":"dictionary_lookup",
              "payload":{
                "pids":["01:0C"],
                "dtcs":[{"code":"P0420"}],
                "includeManufacturers":true
              }
            }
        """.trimIndent()

        bridge.postMessage(payload)
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val data = detail.getJSONObject("data")
        val pidEntry = data.getJSONArray("pids").getJSONObject(0)
        assertEquals("01:0C", pidEntry.getString("pidKey"))
        assertTrue(pidEntry.getBoolean("found"))
        assertEquals("rpm", pidEntry.getString("unit"))
        val dtcEntry = data.getJSONArray("dtcs").getJSONObject(0)
        assertEquals("P0420", dtcEntry.getString("code"))
        assertEquals("standard", dtcEntry.getString("source"))
        assertTrue(dtcEntry.getBoolean("found"))
        val meta = data.getJSONObject("meta")
        val pidRevision = meta.getJSONObject("pidRevision")
        assertEquals("test", pidRevision.getString("source"))
        assertEquals(1, pidRevision.getInt("entryCount"))
        assertTrue(meta.getInt("manufacturerCount") >= 0)
        assertTrue(meta.has("manufacturers"))
    }

    @Test
    fun dictionaryLookupRecordsTelemetry() = runTest {
        val dispatcher = RecordingDispatcher()
        val dictionary = ObdDictionaryManager.default().apply {
            refreshPid(
                definitions = sequenceOf(
                    ObdPidDefinition(
                        mode = "0x01",
                        pid = "0x05",
                        label = "Engine coolant temp"
                    )
                ),
                source = "test",
                versionLabel = "pid-v2",
            )
            refreshDtc(
                definitions = sequenceOf(
                    ObdDtcDefinition(
                        code = "P0300",
                        system = ObdDtcDefinition.System.POWERTRAIN,
                        label = "Random misfire"
                    )
                ),
                source = "test",
                versionLabel = "dtc-v2",
            )
        }
        val telemetryStore = RecordingTelemetryStore()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = dictionary,
            telemetryStore = telemetryStore,
        )

        val payload = """
            {
              "requestId":"dict-telemetry",
              "action":"dictionary_lookup",
              "payload":{
                "pids":[{"mode":"0x01","pid":"0x05"}],
                "dtcs":["P0300"],
                "includeManufacturers":false
              }
            }
        """.trimIndent()

        bridge.postMessage(payload)
        advanceUntilIdle()

        assertEquals(1, telemetryStore.records.size)
        val record = telemetryStore.records.single()
        assertEquals("DictionaryLookup", record.eventType)
        assertEquals(1, record.metadata["pidCount"])
        assertEquals(1, record.metadata["dtcCount"])
        assertEquals(listOf("01:05"), record.metadata["pidKeys"])
        assertEquals(listOf("P0300"), record.metadata["dtcCodes"])
        assertEquals(1, record.metadata["foundPidCount"])
        assertEquals(1, record.metadata["foundDtcCount"])
        assertEquals(false, record.metadata["includeManufacturers"])
    }

    @Test
    fun dictionaryLookupValidatesInput() = runTest {
        val dispatcher = RecordingDispatcher()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = ObdDictionaryManager.default(),
        )

        bridge.postMessage("""{"requestId":"dict-err","action":"dictionary_lookup","payload":{}}""".trimIndent())
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertFalse(detail.getBoolean("ok"))
        val error = detail.getJSONObject("error")
        assertEquals("invalid_argument", error.getString("code"))
    }

    @Test
    fun dictionarySuggestReturnsPidMatches() = runTest {
        val dispatcher = RecordingDispatcher()
        val dictionary = ObdDictionaryManager.default().apply {
            refreshPid(
                definitions = sequenceOf(
                    ObdPidDefinition(
                        mode = "0x01",
                        pid = "0x0C",
                        label = "Engine RPM",
                        unit = "rpm",
                        notes = "Crankshaft speed",
                    )
                ),
                source = "test",
                versionLabel = "pid-v3",
            )
            refreshDtc(definitions = emptySequence(), source = "test", versionLabel = "dtc-v3")
        }
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = dictionary,
        )

        bridge.postMessage(
            """
            {
              "requestId":"suggest-pid",
              "action":"dictionary_suggest",
              "payload":{
                "query":"rpm",
                "types":["pid"],
                "limit":5
              }
            }
            """.trimIndent()
        )
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val data = detail.getJSONObject("data")
        val pids = data.getJSONArray("pids")
        assertEquals(1, pids.length())
        val suggestion = pids.getJSONObject(0)
        assertEquals("01:0C", suggestion.getString("pidKey"))
        val match = suggestion.getJSONObject("match")
        assertEquals("label_contains", match.getString("reason"))
        val meta = data.getJSONObject("meta")
        assertEquals("rpm", meta.getString("query"))
        assertEquals(1, meta.getInt("pidCount"))
        assertFalse(data.has("dtcs"))
    }

    @Test
    fun dictionarySuggestReturnsDtcMatches() = runTest {
        val dispatcher = RecordingDispatcher()
        val dictionary = ObdDictionaryManager.default().apply {
            refreshPid(definitions = emptySequence(), source = "test", versionLabel = "pid-v4")
            refreshDtc(
                definitions = sequenceOf(
                    ObdDtcDefinition(
                        code = "P0420",
                        system = ObdDtcDefinition.System.POWERTRAIN,
                        label = "Catalyst efficiency",
                        notes = "Bank 1"
                    )
                ),
                source = "test",
                versionLabel = "dtc-v4",
            )
        }
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = dictionary,
        )

        bridge.postMessage(
            """
            {
              "requestId":"suggest-dtc",
              "action":"dictionary_suggest",
              "payload":{
                "query":"P042",
                "types":["dtc"],
                "limit":3
              }
            }
            """.trimIndent()
        )
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertTrue(detail.getBoolean("ok"))
        val data = detail.getJSONObject("data")
        assertFalse(data.has("pids"))
        val dtcs = data.getJSONArray("dtcs")
        assertEquals(1, dtcs.length())
        val suggestion = dtcs.getJSONObject(0)
        assertEquals("P0420", suggestion.getString("code"))
        val match = suggestion.getJSONObject("match")
        assertEquals("code_prefix", match.getString("reason"))
        val meta = data.getJSONObject("meta")
        assertEquals("P042", meta.getString("query"))
        assertEquals(1, meta.getInt("dtcCount"))
    }

    @Test
    fun dictionarySuggestValidatesInput() = runTest {
        val dispatcher = RecordingDispatcher()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = ObdDictionaryManager.default(),
        )

        bridge.postMessage("""{"requestId":"suggest-err","action":"dictionary_suggest","payload":{}}""".trimIndent())
        advanceUntilIdle()

        val detail = dispatcher.last()
        assertFalse(detail.getBoolean("ok"))
        val error = detail.getJSONObject("error")
        assertEquals("invalid_argument", error.getString("code"))
    }

    @Test
    fun dictionarySuggestRecordsTelemetry() = runTest {
        val dispatcher = RecordingDispatcher()
        val dictionary = ObdDictionaryManager.default().apply {
            refreshPid(
                definitions = sequenceOf(
                    ObdPidDefinition(
                        mode = "0x01",
                        pid = "0x21",
                        label = "Throttle sensor",
                        unit = "V"
                    )
                ),
                source = "test",
                versionLabel = "pid-v5",
            )
            refreshDtc(
                definitions = sequenceOf(
                    ObdDtcDefinition(
                        code = "P0420",
                        system = ObdDtcDefinition.System.POWERTRAIN,
                        label = "Catalyst efficiency"
                    )
                ),
                source = "test",
                versionLabel = "dtc-v5",
            )
        }
        val telemetryStore = RecordingTelemetryStore()
        val bridge = DiagnosticsJavascriptBridge(
            dispatcher = dispatcher,
            scope = this,
            runner = DiagnosticsJavascriptBridge.DiagnosticsReportRunner { _, _, _ -> sampleResult() },
            latestReportProvider = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
            previewRunner = noopPreviewRunner(),
            dictionary = dictionary,
            telemetryStore = telemetryStore,
        )

        bridge.postMessage(
            """
            {
              "requestId":"suggest-telemetry-pid",
              "action":"dictionary_suggest",
              "payload":{
                "query":"01:21",
                "types":["pid","dtc"],
                "manufacturer":"TESLA",
                "limit":5
              }
            }
            """.trimIndent()
        )
        advanceUntilIdle()

        assertEquals(1, telemetryStore.records.size)
        val pidRecord = telemetryStore.records.single()
        assertEquals("DictionarySuggest", pidRecord.eventType)
        assertEquals("01:21", pidRecord.metadata["query"])
        assertEquals("01:21", pidRecord.metadata["normalizedQuery"])
        assertEquals("0121", pidRecord.metadata["compactQuery"])
        assertEquals(5, pidRecord.metadata["limit"])
        assertEquals(listOf("dtc", "pid"), pidRecord.metadata["types"])
        assertEquals("TESLA", pidRecord.metadata["manufacturer"])
        assertEquals("01:21", pidRecord.metadata["pidKeyHint"])
        assertEquals(1, pidRecord.metadata["pidResults"])
        assertEquals(1, pidRecord.metadata["pidCount"])
        assertEquals("01:21", (pidRecord.metadata["pidKeys"] as List<*>).single())
        assertEquals(listOf("pid_key_exact"), pidRecord.metadata["pidMatchReasons"])
        assertFalse(pidRecord.metadata.containsKey("dtcResults"))

        telemetryStore.records.clear()

        bridge.postMessage(
            """
            {
              "requestId":"suggest-telemetry-dtc",
              "action":"dictionary_suggest",
              "payload":{
                "query":"P042",
                "types":["dtc"],
                "limit":4
              }
            }
            """.trimIndent()
        )
        advanceUntilIdle()

        assertEquals(1, telemetryStore.records.size)
        val dtcRecord = telemetryStore.records.single()
        assertEquals("DictionarySuggest", dtcRecord.eventType)
        assertEquals("P042", dtcRecord.metadata["query"])
        assertEquals("p042", dtcRecord.metadata["normalizedQuery"])
        assertEquals("p042", dtcRecord.metadata["compactQuery"])
        assertEquals(4, dtcRecord.metadata["limit"])
        assertEquals(listOf("dtc"), dtcRecord.metadata["types"])
        assertEquals(1, dtcRecord.metadata["dtcResults"])
        assertEquals(1, dtcRecord.metadata["dtcCount"])
        assertEquals(listOf("P0420"), dtcRecord.metadata["dtcCodes"])
        assertEquals(listOf("code_prefix"), dtcRecord.metadata["dtcMatchReasons"])
        assertFalse(dtcRecord.metadata.containsKey("pidResults"))
    }

    private fun noopPreviewRunner(): DiagnosticsJavascriptBridge.DiagnosticsPreviewRunner =
        DiagnosticsJavascriptBridge.DiagnosticsPreviewRunner {
            DiagnosticsPreviewRuntime.Snapshot(
                generatedAtMillis = 0L,
                protocol = ObdProtocol.AUTO,
                samples = emptyList()
            )
        }

    private fun samplePreviewSnapshot(): DiagnosticsPreviewRuntime.Snapshot =
        DiagnosticsPreviewRuntime.Snapshot(
            generatedAtMillis = 10L,
            protocol = ObdProtocol.AUTO,
            samples = listOf(
                ObdSample(
                    pid = StandardPids.ENGINE_COOLANT_TEMP,
                    value = 90.0,
                    unit = "°C",
                    rawResponse = "41 05 5A",
                    timestampMillis = 10L
                )
            )
        )

    private class RecordingDispatcher : DiagnosticsJavascriptBridge.EventDispatcher {
        private val _events = mutableListOf<JSONObject>()
        val events: List<JSONObject> get() = _events
        override fun dispatch(detail: JSONObject) {
            _events += detail
        }
        fun last(): JSONObject = events.last()
    }

    private class RecordingTelemetryStore : DiagnosticsTelemetryStore {
        val records = mutableListOf<DiagnosticsTelemetryRecord>()
        override suspend fun record(record: DiagnosticsTelemetryRecord) {
            records += record
        }

        override suspend fun pending(): List<DiagnosticsTelemetryRecord> = emptyList()

        override suspend fun markExportedUpTo(timestampMillis: Long) = Unit

        override suspend fun deleteOlderThan(thresholdMillis: Long) = Unit

        override suspend fun summary(): DiagnosticsTelemetrySummary = DiagnosticsTelemetrySummary.empty()
    }

    private class CapturedArgs {
        var request: ObdDiagnosticsRequest? = null
        var metadata: DiagnosticsReportWorkflow.Metadata? = null
        var options: DiagnosticsConnectionOptions? = null
    }

    private fun sampleResult(): DiagnosticsReportWorkflow.Result {
        val definition = DiagnosticsMetricDefinition(
            id = "engine_temp",
            mode = "0x01",
            pid = "0x05",
            label = "Температура",
            unit = "°C",
            thresholds = DiagnosticsMetricThresholds(
                warningHigh = 95.0,
                criticalHigh = 110.0,
            ),
            advice = DiagnosticsMetricAdvice(
                messages = mapOf(
                    DiagnosticsMetricStatus.WARNING_HIGH to "Проверьте охлаждение"
                ),
                defaultMessage = "Наблюдается отклонение",
                noDataMessage = "Нет данных",
            )
        )
        val insight = DiagnosticsMetricInsight(
            definition = definition,
            value = 102.0,
            unit = "°C",
            status = DiagnosticsMetricStatus.WARNING_HIGH,
            advice = "Проверьте охлаждение",
            sourceTimestampMillis = 500L,
        )
        val snapshot = DiagnosticsProfileSnapshot(
            timestampMillis = 1_000L,
            metrics = listOf(insight),
        )
        val recommendation = DiagnosticsRecommendation(
            metricId = "engine_temp",
            pidKey = DiagnosticsMetricDefinition.normalizeKey("0x01", "0x05"),
            title = "Перегрев",
            message = "Снизьте нагрузку",
            severity = DiagnosticsRecommendationSeverity.WARNING,
            status = DiagnosticsMetricStatus.WARNING_HIGH,
            priority = DiagnosticsRecommendationPriority.MEDIUM,
            threshold = DiagnosticsRecommendationThreshold(
                type = DiagnosticsRecommendationThresholdType.GREATER_OR_EQUAL,
                value = 95.0,
                unit = "°C",
            )
        )
        val pidDef = ObdPidDefinition(
            mode = "0x01",
            pid = "0x05",
            label = "Temp",
        )
        val pidSample = ObdPidSample(
            definition = pidDef,
            rawPayload = byteArrayOf(0x64),
            rawHex = "64",
            value = 100.0,
            unit = "°C",
            timestampMillis = 500L,
        )
        val diagnosticsResult = ObdDiagnosticsResult(
            pidSamples = listOf(pidSample),
            troubleCodes = ObdDtcBatch(
                entries = listOf(
                    ObdDtcEntry(
                        code = "P0420",
                        definition = ObdDtcDefinition(
                            code = "P0420",
                            system = ObdDtcDefinition.System.POWERTRAIN,
                            label = "Catalyst efficiency",
                            notes = "Bank 1",
                        )
                    )
                ),
                timestampMillis = 700L,
            ),
            clearPerformed = false,
            failures = emptyList(),
            durationMillis = 12_000L,
        )
        val diagnosticsRun = PassThruDiagnosticsCoordinator.DiagnosticsRun(
            sessionState = ObdSessionState.Completed,
            diagnostics = diagnosticsResult,
        )
        val trend = DiagnosticsMetricTrend(
            metricId = definition.id,
            points = listOf(
                DiagnosticsMetricTrendPoint(timestampMillis = 500L, value = 98.0),
                DiagnosticsMetricTrendPoint(timestampMillis = snapshot.timestampMillis, value = 102.0)
            )
        )
        val evaluation = DiagnosticsMetricEvaluation(
            snapshot = snapshot,
            recommendations = listOf(recommendation),
            trends = mapOf(trend.metricId to trend)
        )
        val vehicle = DiagnosticsReportVehicle(
            make = "Tesla",
            model = "Model S",
            year = 2022,
            vin = "5YJSA1E26HF000000",
        )
        val customer = DiagnosticsReportCustomer(
            phone = "+70000000000",
            email = "owner@example.com",
        )
        val input = DiagnosticsReportInput(
            sessionId = "session-123",
            generatedAtMillis = 1_000L,
            snapshot = snapshot,
            recommendations = listOf(recommendation),
            vehicle = vehicle,
            customer = customer,
        )
        val report = DiagnosticsReport(
            html = "<html>ok</html>",
            pdfBytes = byteArrayOf(1, 2, 3),
        )
        return DiagnosticsReportWorkflow.Result(
            sessionId = input.sessionId,
            diagnosticsRun = diagnosticsRun,
            evaluation = evaluation,
            reportInput = input,
            report = report,
        )
    }
}
