package com.selfservice.kiosk.diagnostics

import com.autoservice.diagnostics.obd.DiagnosticsPreviewRuntime
import com.autoservice.diagnostics.obd.ObdProtocol
import com.autoservice.diagnostics.obd.PID
import com.autoservice.diagnostics.obd.StandardPids

class DiagnosticsPreviewController(
    private val runtime: DiagnosticsPreviewRuntime = DiagnosticsPreviewRuntime()
) {

    data class Request(
        val protocol: ObdProtocol? = null,
        val pidSelectors: List<PidSelector>? = null
    ) {
        data class PidSelector(val mode: String, val pid: String)

        companion object {
            val Empty = Request()
        }
    }

    suspend fun capture(request: Request = Request.Empty): DiagnosticsPreviewRuntime.Snapshot {
        val protocol = request.protocol ?: ObdProtocol.AUTO
        val pids = resolvePids(request.pidSelectors)
        return runtime.capture(protocol = protocol, pids = pids)
    }

    private fun resolvePids(selectors: List<Request.PidSelector>?): List<PID> {
        if (selectors.isNullOrEmpty()) {
            return StandardPids.defaultSet
        }
        val resolved = selectors.mapNotNull { selector ->
            StandardPids.lookup(selector.mode, selector.pid)
        }
        return if (resolved.isNotEmpty()) resolved else StandardPids.defaultSet
    }
}
