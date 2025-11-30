package com.selfservice.kiosk.mdm

import java.util.Locale

class DefaultMdmCommandHandler(
    private val supabaseRefresher: () -> Boolean,
    private val dictionarySyncRequester: () -> Boolean,
    private val heartbeatRequester: () -> Boolean,
    private val obdConnector: (force: Boolean) -> Boolean,
    private val obdDisconnector: () -> Boolean
) : MdmCommandHandler {

    override suspend fun handle(command: MdmCommand): MdmCommandResult {
        val normalizedType = command.type.lowercase(Locale.US)
        return when (normalizedType) {
            "supabase_sync" -> {
                val triggered = supabaseRefresher()
                MdmCommandResult.success(
                    message = if (triggered) "Supabase refresh scheduled" else "Supabase monitor not available",
                    payload = mapOf("triggered" to triggered)
                )
            }
            "dictionary_sync" -> {
                val triggered = dictionarySyncRequester()
                MdmCommandResult.success(
                    message = if (triggered) "Dictionary sync requested" else "Dictionary runtime offline",
                    payload = mapOf("triggered" to triggered)
                )
            }
            "heartbeat" -> {
                val triggered = heartbeatRequester()
                MdmCommandResult.success(
                    message = if (triggered) "Heartbeat enqueued" else "Heartbeat reporter unavailable",
                    payload = mapOf("triggered" to triggered)
                )
            }
            "obd_disconnect" -> {
                val triggered = obdDisconnector()
                MdmCommandResult.success(
                    message = if (triggered) "OBD disconnect triggered" else "OBD controller inactive",
                    payload = mapOf("triggered" to triggered)
                )
            }
            "obd_connect" -> {
                val force = command.payload.boolean("force", defaultValue = false)
                val triggered = obdConnector(force)
                MdmCommandResult.success(
                    message = if (triggered) "OBD connect requested" else "OBD controller inactive",
                    payload = mapOf("force" to force, "triggered" to triggered)
                )
            }
            "noop" -> MdmCommandResult.success(message = "No-op command completed")
            else -> MdmCommandResult.failure("Unsupported command: ${command.type}")
        }
    }

}

private fun Map<String, Any?>.boolean(key: String, defaultValue: Boolean = false): Boolean {
    val raw = this[key] ?: return defaultValue
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw.equals("true", true) || raw == "1"
        else -> defaultValue
    }
}
