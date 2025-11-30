package com.selfservice.obd.core.passthru

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PassThruJniBridgeTest {

    @Test
    fun `bridge delegates operations to native adapter`() {
        val adapter = RecordingAdapter()
        val channelConfig = PassThruChannelConfig(protocolId = 6, baudRate = 500_000, flags = 1)
    val message = PassThruMessage(
        channelId = 42,
        protocolId = 6,
        timestampNanos = 123L,
        flags = 0,
        payload = byteArrayOf(2, 16)
    )
    val encodedFrame = byteArrayOf(0x7E.toByte(), 0x8A.toByte())
        val decoded = PassThruMessage(
                channelId = 42,
                protocolId = 6,
                timestampNanos = 456L,
                flags = 0,
        payload = byteArrayOf(6, 98)
        )
        adapter.readResult = encodedFrame
        val bridge =
                PassThruJniBridge(
                        nativeAdapter = adapter,
                        encoder = { msg ->
                            assertSame(message, msg)
                            encodedFrame
                        },
                        decoder = { bytes ->
                            assertTrue(bytes.contentEquals(encodedFrame))
                            decoded
                        }
                )

        val openResult = bridge.openChannel(channelConfig)
        val voltageResult = bridge.setReferenceVoltage(42, 12_000)
        val writeResult = bridge.writeMessage(message, timeoutMs = 150)
        val readResult = bridge.readMessage(42, timeoutMs = 200)
        val closeResult = bridge.closeChannel(42)

        assertEquals(adapter.openResult, openResult.getOrNull())
        assertTrue(voltageResult.isSuccess)
        assertTrue(writeResult.isSuccess)
        assertSame(decoded, readResult.getOrNull())
        assertTrue(closeResult.isSuccess)
        val callNames = adapter.calls.map { it.name }
        assertEquals(
                listOf("openChannel", "setReferenceVoltage", "writeMessage", "readMessage", "closeChannel"),
                callNames
        )
    }

    @Test
    fun `bridge surfaces native failures`() {
        val adapter = RecordingAdapter().apply { failOnWrite = IllegalStateException("write boom") }
    val message = PassThruMessage(
        channelId = 99,
        protocolId = 1,
        timestampNanos = 9L,
        flags = 0,
        payload = byteArrayOf(1)
    )
        val bridge = PassThruJniBridge(adapter)

        val writeResult = bridge.writeMessage(message, timeoutMs = 50)

        assertTrue(writeResult.isFailure)
        val error = writeResult.exceptionOrNull()
        assertEquals("write boom", error?.message)
    }

    private class RecordingAdapter : PassThruJniBridge.NativeAdapter {
        data class Call(val name: String, val args: List<Any?>)

        val calls: MutableList<Call> = mutableListOf()
        var openResult: Int = 101
        var readResult: ByteArray? = null
        var failOnOpen: Throwable? = null
        var failOnClose: Throwable? = null
        var failOnVoltage: Throwable? = null
        var failOnWrite: Throwable? = null
        var failOnRead: Throwable? = null

        override fun openChannel(protocolId: Int, baudRate: Int, flags: Int): Int {
            calls += Call("openChannel", listOf(protocolId, baudRate, flags))
            failOnOpen?.let { throw it }
            return openResult
        }

        override fun closeChannel(channelId: Int) {
            calls += Call("closeChannel", listOf(channelId))
            failOnClose?.let { throw it }
        }

        override fun setReferenceVoltage(channelId: Int, millivolts: Int) {
            calls += Call("setReferenceVoltage", listOf(channelId, millivolts))
            failOnVoltage?.let { throw it }
        }

        override fun writeMessage(channelId: Int, encodedFrame: ByteArray, timeoutMs: Int) {
            calls += Call("writeMessage", listOf(channelId, encodedFrame, timeoutMs))
            failOnWrite?.let { throw it }
        }

        override fun readMessage(channelId: Int, timeoutMs: Int): ByteArray? {
            calls += Call("readMessage", listOf(channelId, timeoutMs))
            failOnRead?.let { throw it }
            return readResult
        }
    }
}
