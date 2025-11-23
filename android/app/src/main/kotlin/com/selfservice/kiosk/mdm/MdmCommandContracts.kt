package com.selfservice.kiosk.mdm

import com.selfservice.kiosk.mdm.DeviceCommandSupabaseSchema.Status
import java.util.UUID

/** Wire representation of an MDM command delivered to the kiosk runtime. */
data class MdmCommand(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
    val issuedAtMillis: Long = System.currentTimeMillis(),
    val source: String = "mdm",
    val metadata: Map<String, Any?> = emptyMap()
)

fun interface MdmCommandHandler {
    suspend fun handle(command: MdmCommand): MdmCommandResult
}

data class MdmCommandResult(
    val status: Status,
    val message: String? = null,
    val resultPayload: Map<String, Any?>? = null,
    val errorMessage: String? = null,
    val metadata: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun success(
            message: String? = null,
            payload: Map<String, Any?>? = null,
            metadata: Map<String, Any?> = emptyMap()
        ): MdmCommandResult = MdmCommandResult(
            status = Status.SUCCEEDED,
            message = message,
            resultPayload = payload,
            metadata = metadata
        )

        fun failure(
            message: String,
            errorPayload: Map<String, Any?>? = null,
            metadata: Map<String, Any?> = emptyMap()
        ): MdmCommandResult = MdmCommandResult(
            status = Status.FAILED,
            message = message,
            errorMessage = message,
            resultPayload = errorPayload,
            metadata = metadata
        )

        fun cancelled(
            message: String,
            payload: Map<String, Any?>? = null,
            metadata: Map<String, Any?> = emptyMap()
        ): MdmCommandResult = MdmCommandResult(
            status = Status.CANCELLED,
            message = message,
            resultPayload = payload,
            metadata = metadata
        )
    }
}
