package com.autoservice.diagnostics.obd

/**
 * Minimal abstraction over the physical transport (Bluetooth, PassThru, USB) used by the
 * diagnostics layer. Implementations translate ASCII ELM327-style frames to the underlying bus.
 */
interface ObdTransport {
    /** Establishes a link using the provided protocol (default AUTO). */
    suspend fun connect(protocol: ObdProtocol = ObdProtocol.AUTO): Boolean

    /** Executes a single frame and returns the raw ASCII response as provided by the adapter. */
    suspend fun execute(frame: String): String
}
