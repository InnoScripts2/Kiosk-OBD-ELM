package com.selfservice.obd.core.passthru

/**
 * Centralises installation of the native PassThru bridge. The installer loads the JNI library and
 * registers a provider inside [PassThruNativeBridgeRegistry].
 */
object PassThruNativeBridgeInstaller {

    data class Config(
            val libraryName: String = PassThruNativeBindings.DEFAULT_LIBRARY_NAME,
            val loader: NativeLibraryLoader = NativeLibraryLoader.system(),
            val adapter: PassThruJniBridge.NativeAdapter = PassThruNativeBindings,
        val bridgeFactory: (PassThruJniBridge.NativeAdapter) -> PassThruNativeBridge =
            { native -> PassThruJniBridge(native) },
        val registrar: ((() -> PassThruNativeBridge) -> Unit) =
            PassThruNativeBridgeRegistry::register
    )

    fun install(config: Config = Config()): () -> PassThruNativeBridge {
        val adapter = config.adapter
        if (adapter === PassThruNativeBindings) {
            PassThruNativeBindings.ensureLoaded(config.libraryName, config.loader)
        }
        val provider: () -> PassThruNativeBridge = { config.bridgeFactory(adapter) }
        config.registrar(provider)
        return provider
    }
}
