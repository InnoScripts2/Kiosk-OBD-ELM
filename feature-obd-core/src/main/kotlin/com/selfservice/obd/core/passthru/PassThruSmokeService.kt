package com.selfservice.obd.core.passthru

/**
 * Convenience facade that resolves the native passthru bridge and executes a smoke plan through
 * [PassThruSmokeRunner]. Keeps orchestration close to the JVM layer so tooling and scripts can
 * reuse the same entry point without wiring the registry manually.
 */
class PassThruSmokeService(
        private val bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
        private val executor:
                suspend (PassThruNativeBridge, PassThruSmokePlan, () -> ByteArray) -> PassThruSmokeRunner.Result =
                { bridge, plan, payloadSupplier ->
                    PassThruSmokeRunner(bridge).execute(plan, payloadSupplier)
                }
) {

    suspend fun run(
            plan: PassThruSmokePlan,
            payloadSupplier: () -> ByteArray = { ByteArray(0) }
    ): PassThruSmokeRunner.Result {
        val bridge = bridgeProvider()
        return executor.invoke(bridge, plan, payloadSupplier)
    }
}
