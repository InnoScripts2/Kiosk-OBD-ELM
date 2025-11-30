@file:Suppress("DEPRECATION")

package com.selfservice.kiosk.logging

@Deprecated(
    message = "Diagnostics logging moved to platform/logging module",
    replaceWith = ReplaceWith(
        expression = "DiagnosticsLogService",
        imports = ["com.selfservice.platform.logging.DiagnosticsLogService"]
    )
)
typealias DiagnosticsLogService = com.selfservice.platform.logging.DiagnosticsLogService
