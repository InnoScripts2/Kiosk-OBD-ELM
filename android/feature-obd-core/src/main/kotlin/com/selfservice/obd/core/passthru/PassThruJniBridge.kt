package com.selfservice.obd.core.passthru

/**
 * [PassThruNativeBridge] implementation backed by a JNI adapter. The bridge converts Kotlin
 * objects to raw frames understood by the native layer and wraps native exceptions into
 * [Result] values expected by the higher-level transport.
 */
class PassThruJniBridge(
        private val nativeAdapter: NativeAdapter,
        private val encoder: (PassThruMessage) -> ByteArray = PassThruFrameCodec::encode,
        private val decoder: (ByteArray) -> PassThruMessage = PassThruFrameCodec::decode
) : PassThruNativeBridge {

    override fun openChannel(config: PassThruChannelConfig): Result<Int> {
        return runCatching {
            nativeAdapter.openChannel(config.protocolId, config.baudRate, config.flags)
        }
    }

    override fun closeChannel(channelId: Int): Result<Unit> {
        return runCatching { nativeAdapter.closeChannel(channelId) }
    }

    override fun setReferenceVoltage(channelId: Int, millivolts: Int): Result<Unit> {
        return runCatching { nativeAdapter.setReferenceVoltage(channelId, millivolts) }
    }

    override fun writeMessage(message: PassThruMessage, timeoutMs: Int): Result<Unit> {
        return runCatching {
            val encoded = encoder.invoke(message)
            nativeAdapter.writeMessage(message.channelId, encoded, timeoutMs)
        }
    }

    override fun readMessage(channelId: Int, timeoutMs: Int): Result<PassThruMessage?> {
        return runCatching {
            val encoded = nativeAdapter.readMessage(channelId, timeoutMs)
            encoded?.let { decoder.invoke(it) }
        }
    }

    /**
     * Abstraction over JNI entry points. Separate from the bridge to simplify testing without
     * loading the native library.
     */
    interface NativeAdapter {
        fun openChannel(protocolId: Int, baudRate: Int, flags: Int): Int

        fun closeChannel(channelId: Int)

        fun setReferenceVoltage(channelId: Int, millivolts: Int)

        fun writeMessage(channelId: Int, encodedFrame: ByteArray, timeoutMs: Int)

        fun readMessage(channelId: Int, timeoutMs: Int): ByteArray?
    }
}
