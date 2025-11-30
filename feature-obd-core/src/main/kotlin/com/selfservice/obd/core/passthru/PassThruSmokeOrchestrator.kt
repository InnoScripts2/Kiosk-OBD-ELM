package com.selfservice.obd.core.passthru

import com.selfservice.core.DispatchersProvider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Executes passthru smoke plans with watchdog semantics to prevent long-running diagnostics from
 * hanging unattended. The orchestrator delegates to [PassThruSmokeService] while enforcing
 * timeouts and dispatcher selection.
 */
class PassThruSmokeOrchestrator(
    private val dispatchers: DispatchersProvider,
    private val executor: suspend (PassThruSmokePlan, () -> ByteArray) -> PassThruSmokeRunner.Result,
    private val defaultTimeoutMillis: Long = DEFAULT_TIMEOUT_MS
) {

    constructor(
        dispatchers: DispatchersProvider,
        service: PassThruSmokeService = PassThruSmokeService(),
        defaultTimeoutMillis: Long = DEFAULT_TIMEOUT_MS
    ) : this(
        dispatchers = dispatchers,
        executor = { plan, payload -> service.run(plan, payload) },
        defaultTimeoutMillis = defaultTimeoutMillis
    )

    suspend fun run(
            plan: PassThruSmokePlan,
            payloadSupplier: () -> ByteArray = { ByteArray(0) },
            timeoutMillis: Long = defaultTimeoutMillis
    ): PassThruSmokeRunner.Result {
        require(timeoutMillis > 0) { "timeoutMillis must be > 0" }
        return try {
            withContext(dispatchers.io) {
                withTimeout(timeoutMillis) {
                    executor.invoke(plan, payloadSupplier)
                }
            }
        } catch (error: TimeoutCancellationException) {
            PassThruSmokeRunner.Result(
                    executed = emptyList(),
                    failure =
                            PassThruSmokeRunner.Result.Failure(
                    PassThruSmokeCommand(
                        type = PassThruSmokeCommandType.WATCHDOG_TIMEOUT,
                        description =
                            "Smoke plan watchdog exceeded ${timeoutMillis} ms"
                    ),
                                    error
                            )
            )
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
