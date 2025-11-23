package com.selfservice.obd.core.passthru

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class PassThruNativeBridgeInstallerTest {

    @AfterTest
    fun tearDown() {
        PassThruNativeBridgeRegistry.reset()
        PassThruNativeBindings.resetForTests()
    }

    @Test
    fun `install loads default library and registers provider`() {
        val loaded = mutableListOf<String>()
        val loader = NativeLibraryLoader { name -> loaded += name }

        val provider = PassThruNativeBridgeInstaller.install(
                PassThruNativeBridgeInstaller.Config(loader = loader)
        )

        assertEquals(
                listOf(PassThruNativeBindings.DEFAULT_LIBRARY_NAME),
                loaded
        )
        val resolved = PassThruNativeBridgeRegistry.resolve()
        assertIs<PassThruJniBridge>(resolved)
        val created = provider.invoke()
        assertIs<PassThruJniBridge>(created)
        assertNotSame(resolved, created)
    }

    @Test
    fun `install with custom adapter skips library load`() {
        val loaded = mutableListOf<String>()
        val loader = NativeLibraryLoader { name -> loaded += name }
        val adapter = RecordingNativeAdapter()

        val provider = PassThruNativeBridgeInstaller.install(
                PassThruNativeBridgeInstaller.Config(
                        loader = loader,
                        adapter = adapter
                )
        )

        assertTrue(loaded.isEmpty(), "Native library should not load for custom adapter")
        val bridge = provider.invoke()
        val config = PassThruChannelConfig(protocolId = 1, baudRate = 500_000, flags = 0)
        val channelId = bridge.openChannel(config).getOrThrow()
        assertEquals(RecordingNativeAdapter.OPEN_RESULT, channelId)
        assertEquals(1, adapter.openCalls)
    }

    private class RecordingNativeAdapter : PassThruJniBridge.NativeAdapter {

        var openCalls: Int = 0
            private set

        override fun openChannel(protocolId: Int, baudRate: Int, flags: Int): Int {
            openCalls += 1
            return OPEN_RESULT
        }

        override fun closeChannel(channelId: Int) = Unit

        override fun setReferenceVoltage(channelId: Int, millivolts: Int) = Unit

        override fun writeMessage(channelId: Int, encodedFrame: ByteArray, timeoutMs: Int) = Unit

        override fun readMessage(channelId: Int, timeoutMs: Int): ByteArray? = null

        companion object {
            const val OPEN_RESULT: Int = 512
        }
    }
}
