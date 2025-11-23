@file:Suppress("DEPRECATION")

package com.selfservice.kiosk.logging

@Deprecated(
    message = "Diagnostics logging moved to platform/logging module",
    replaceWith = ReplaceWith(
        expression = "DiagnosticsLogOutboxExporter",
        imports = ["com.selfservice.platform.logging.DiagnosticsLogOutboxExporter"]
    )
)
typealias DiagnosticsLogOutboxExporter = com.selfservice.platform.logging.DiagnosticsLogOutboxExporter
