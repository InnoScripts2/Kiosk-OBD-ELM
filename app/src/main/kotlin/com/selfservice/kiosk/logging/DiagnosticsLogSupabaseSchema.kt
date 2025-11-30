@file:Suppress("DEPRECATION")

package com.selfservice.kiosk.logging

@Deprecated(
    message = "Diagnostics logging moved to platform/logging module",
    replaceWith = ReplaceWith(
        expression = "DiagnosticsLogSupabaseSchema",
        imports = ["com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema"]
    )
)
typealias DiagnosticsLogSupabaseSchema = com.selfservice.platform.logging.DiagnosticsLogSupabaseSchema
