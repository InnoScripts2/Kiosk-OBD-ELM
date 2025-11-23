package com.selfservice.obd.core.passthru

import java.util.concurrent.atomic.AtomicBoolean

/** Loader abstraction to allow swapping the mechanism in tests. */
fun interface NativeLibraryLoader {
    fun load(libraryName: String)

    companion object {
        fun system(): NativeLibraryLoader = NativeLibraryLoader { java.lang.System.loadLibrary(it) }
    }
}

/**
 * Default JNI adapter implementation. Delegates all calls to native functions that live in the
 * `pass_thru_jni` shared library.
 */
internal object PassThruNativeBindings : PassThruJniBridge.NativeAdapter {

    const val DEFAULT_LIBRARY_NAME: String = "pass_thru_jni"

    private val isLoaded = AtomicBoolean(false)

    fun ensureLoaded(
            libraryName: String = DEFAULT_LIBRARY_NAME,
            loader: NativeLibraryLoader = NativeLibraryLoader.system()
    ) {
        if (isLoaded.get()) {
            return
        }
        synchronized(this) {
            if (isLoaded.get()) {
                return
            }
            try {
                loader.load(libraryName)
                isLoaded.set(true)
            } catch (error: Throwable) {
                isLoaded.set(false)
                throw error
            }
        }
    }

    fun resetForTests() {
        isLoaded.set(false)
    }

    override fun openChannel(protocolId: Int, baudRate: Int, flags: Int): Int {
        return nativeOpenChannel(protocolId, baudRate, flags)
    }

    override fun closeChannel(channelId: Int) {
        nativeCloseChannel(channelId)
    }

    override fun setReferenceVoltage(channelId: Int, millivolts: Int) {
        nativeSetReferenceVoltage(channelId, millivolts)
    }

    override fun writeMessage(channelId: Int, encodedFrame: ByteArray, timeoutMs: Int) {
        nativeWriteMessage(channelId, encodedFrame, timeoutMs)
    }

    override fun readMessage(channelId: Int, timeoutMs: Int): ByteArray? {
        return nativeReadMessage(channelId, timeoutMs)
    }

    private external fun nativeOpenChannel(protocolId: Int, baudRate: Int, flags: Int): Int

    private external fun nativeCloseChannel(channelId: Int)

    private external fun nativeSetReferenceVoltage(channelId: Int, millivolts: Int)

    private external fun nativeWriteMessage(channelId: Int, encodedFrame: ByteArray, timeoutMs: Int)

    private external fun nativeReadMessage(channelId: Int, timeoutMs: Int): ByteArray?
}
