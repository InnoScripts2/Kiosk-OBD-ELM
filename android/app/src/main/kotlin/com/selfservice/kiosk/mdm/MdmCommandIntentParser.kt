package com.selfservice.kiosk.mdm

import android.content.Intent
import java.util.UUID

internal object MdmCommandIntentParser {

    fun fromIntent(intent: Intent, fallbackSource: String?): MdmCommand? {
        val type = intent.getStringExtra(MdmCommandIntents.EXTRA_COMMAND_TYPE)?.trim().orEmpty()
        if (type.isEmpty()) return null
        val id = intent.getStringExtra(MdmCommandIntents.EXTRA_COMMAND_ID).orEmpty()
        val payload = MdmCommandPayloadParser.parseJsonMap(intent.getStringExtra(MdmCommandIntents.EXTRA_COMMAND_PAYLOAD))
        val metadata = MdmCommandPayloadParser.parseJsonMap(intent.getStringExtra(MdmCommandIntents.EXTRA_COMMAND_METADATA))
        val issuedAt = intent.getLongExtra(MdmCommandIntents.EXTRA_COMMAND_ISSUED_AT, System.currentTimeMillis())
        val source = intent.getStringExtra(MdmCommandIntents.EXTRA_COMMAND_SOURCE)
            ?.takeIf { it.isNotBlank() }
            ?: fallbackSource
            ?: "mdm"
        return MdmCommand(
            id = id.ifBlank { UUID.randomUUID().toString() },
            type = type,
            payload = payload,
            issuedAtMillis = issuedAt,
            source = source,
            metadata = metadata
        )
    }
}
