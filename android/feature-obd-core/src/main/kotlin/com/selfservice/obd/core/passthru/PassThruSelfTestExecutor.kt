package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.selftest.AdapterSelfTestExecution
import com.selfservice.obd.core.selftest.AdapterSelfTestExecutor
import com.selfservice.obd.core.selftest.AdapterSelfTestStep
import com.selfservice.obd.core.session.ConnectedAdapter
import com.selfservice.obd.core.transport.ObdTransport
import kotlin.math.max
import kotlinx.coroutines.runBlocking

/** Adapter self-test executor that reuses PassThru session orchestration for each step. */
class PassThruSelfTestExecutor(
        private val environmentProvider: () -> PassThruSessionEnvironment,
        private val adapterProvider: () -> ConnectedAdapter,
        private val handler: StepHandler,
        private val clock: () -> Long = { System.currentTimeMillis() }
) : AdapterSelfTestExecutor {

    fun interface StepHandler {
        suspend fun perform(
                step: AdapterSelfTestStep,
                transport: ObdTransport
        ): AdapterSelfTestExecution.Outcome
    }

    override fun execute(step: AdapterSelfTestStep): AdapterSelfTestExecution {
        val environment = environmentProvider()
        val adapter = adapterProvider()
        val dispatcher = environment.dispatchers.io
        return runBlocking(dispatcher) {
            val startedAt = clock()
            val bootstrap = environment.configFactory.prepareSession(adapter)
            try {
                environment.manager.startWithBootstrap(bootstrap)
                val outcome = handler.perform(step, bootstrap.transport)
                environment.manager.completeSuccessfully()
                AdapterSelfTestExecution(
                        step = step,
                        outcome = outcome,
                        attempts = 1,
                        durationMs = max(clock() - startedAt, 0L),
                        message = null
                )
            } catch (error: Throwable) {
                environment.manager.fail(error)
                AdapterSelfTestExecution(
                        step = step,
                        outcome = AdapterSelfTestExecution.Outcome.FAILED,
                        attempts = 1,
                        durationMs = max(clock() - startedAt, 0L),
                        message = error.message ?: error::class.simpleName
                )
            }
        }
    }
}
