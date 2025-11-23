package com.selfservice.kiosk.selftest

import com.selfservice.obd.core.passthru.PassThruChannelConfig
import com.selfservice.obd.core.passthru.PassThruMessage
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import kotlin.jvm.Volatile

/**
 * Lightweight PassThru bridge used in DEV builds to execute self-tests without real hardware.
 * Echoes every write back into the read queue, enabling smoke checks for transport wiring.
 */
class DevMockPassThruBridge : PassThruNativeBridge {

    private val nextChannelId = AtomicInteger(1_000)
    private val responses: ArrayDeque<Result<PassThruMessage?>> = ArrayDeque()
    private var openChannelId: Int? = null

    @Volatile var failNextRead: Throwable? = null
    @Volatile var failNextWrite: Throwable? = null

    override fun openChannel(config: PassThruChannelConfig): Result<Int> {
        val current = openChannelId
        if (current != null) {
            return Result.failure(IllegalStateException("Channel already opened ($current)"))
        }
        val channelId = nextChannelId.getAndIncrement()
        openChannelId = channelId
        return Result.success(channelId)
    }

    override fun closeChannel(channelId: Int): Result<Unit> {
        if (openChannelId == channelId) {
            openChannelId = null
        }
        return Result.success(Unit)
    }

    override fun setReferenceVoltage(channelId: Int, millivolts: Int): Result<Unit> =
            Result.success(Unit)

    override fun writeMessage(message: PassThruMessage, timeoutMs: Int): Result<Unit> {
        val failure = failNextWrite
        if (failure != null) {
            failNextWrite = null
            return Result.failure(failure)
        }
        val channelId = openChannelId
        if (channelId == null) {
            return Result.failure(IllegalStateException("Channel not opened"))
        }
        if (channelId != message.channelId) {
            return Result.failure(IllegalArgumentException("Unexpected channel ${message.channelId}"))
        }
    val echo =
        PassThruMessage(
            channelId = message.channelId,
            protocolId = message.protocolId,
            timestampNanos = System.nanoTime(),
            flags = message.flags,
            payload = message.payload.copyOf()
        )
    responses.addLast(Result.success(echo))
        return Result.success(Unit)
    }

    override fun readMessage(channelId: Int, timeoutMs: Int): Result<PassThruMessage?> {
        val failure = failNextRead
        if (failure != null) {
            failNextRead = null
            return Result.failure(failure)
        }
        if (openChannelId != channelId) {
            return Result.failure(IllegalArgumentException("Channel $channelId not open"))
        }
        return if (responses.isEmpty()) {
            Result.success(null)
        } else {
            responses.removeFirst()
        }
    }

    fun enqueueResponse(message: PassThruMessage?) {
        responses.addLast(Result.success(message))
    }

    companion object {
        fun create(): PassThruNativeBridge = DevMockPassThruBridge()
    }
}
