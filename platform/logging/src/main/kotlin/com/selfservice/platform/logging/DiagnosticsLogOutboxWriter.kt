package com.selfservice.platform.logging

/**
 * Minimal abstraction over the Supabase outbox writer so diagnostics logging can remain in a
 * platform module without referencing the application layer directly.
 */
fun interface DiagnosticsLogOutboxWriter {
    fun enqueue(table: String, payload: Map<String, Any?>, operation: String)

    object Operation {
        const val UPSERT: String = "upsert"
    }
}

fun DiagnosticsLogOutboxWriter.enqueue(table: String, payload: Map<String, Any?>) {
    enqueue(table, payload, DiagnosticsLogOutboxWriter.Operation.UPSERT)
}
