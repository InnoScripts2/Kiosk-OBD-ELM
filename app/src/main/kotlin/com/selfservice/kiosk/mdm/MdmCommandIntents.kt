package com.selfservice.kiosk.mdm

/** Shared action and extra names for dispatching MDM commands into the kiosk runtime. */
object MdmCommandIntents {
    const val ACTION_MANAGED_COMMAND = "com.selfservice.kiosk.mdm.COMMAND_DISPATCH"
    const val ACTION_DEBUG_COMMAND = "com.selfservice.kiosk.mdm.COMMAND"

    const val EXTRA_COMMAND_ID = "command_id"
    const val EXTRA_COMMAND_TYPE = "command_type"
    const val EXTRA_COMMAND_PAYLOAD = "payload_json"
    const val EXTRA_COMMAND_METADATA = "metadata_json"
    const val EXTRA_COMMAND_SOURCE = "command_source"
    const val EXTRA_COMMAND_ISSUED_AT = "command_issued_at"
}
