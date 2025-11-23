@file:Suppress("DEPRECATION")

package com.selfservice.kiosk.logging

@Deprecated(
    message = "Diagnostics logging moved to platform/logging module",
    replaceWith = ReplaceWith(
        expression = "DiagnosticsLogRetentionCoordinator",
        imports = ["com.selfservice.platform.logging.DiagnosticsLogRetentionCoordinator"]
    )
)
typealias DiagnosticsLogRetentionCoordinator = com.selfservice.platform.logging.DiagnosticsLogRetentionCoordinator
