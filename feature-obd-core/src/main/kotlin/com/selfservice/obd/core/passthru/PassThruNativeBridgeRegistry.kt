package com.selfservice.obd.core.passthru

import java.util.concurrent.atomic.AtomicReference

/** Central registry for resolving the active [PassThruNativeBridge] implementation. */
object PassThruNativeBridgeRegistry {

    private val providerRef: AtomicReference<() -> PassThruNativeBridge> = AtomicReference()

    fun register(provider: () -> PassThruNativeBridge) {
        providerRef.set(provider)
    }

    fun reset() {
        providerRef.set(null)
    }

    fun resolve(): PassThruNativeBridge {
        return providerRef.get()?.invoke()
                ?: error(
                        "PassThruNativeBridge is not registered; call PassThruNativeBridgeRegistry.register first"
                )
    }
}
