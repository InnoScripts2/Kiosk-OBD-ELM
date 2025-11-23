package com.selfservice.obd.core.passthru

import com.selfservice.obd.core.selftest.AdapterSelfTestPlan
import com.selfservice.obd.core.selftest.AdapterSelfTestRun
import com.selfservice.obd.core.selftest.AdapterSelfTestRunner
import com.selfservice.obd.core.selftest.AdapterSelfTestUploader
import com.selfservice.obd.core.selftest.AdapterSelfTestUploadMetadata
import com.selfservice.obd.core.session.ConnectedAdapter

/**
 * Facade around [AdapterSelfTestRunner] tailored for PassThru adapters. It wires the runner with
 * [PassThruSelfTestExecutor], executes the supplied plan and optionally uploads results through the
 * provided [AdapterSelfTestUploader].
 */
class PassThruSelfTestService(
        private val environmentProvider: () -> PassThruSessionEnvironment,
        private val adapterProvider: () -> ConnectedAdapter,
        private val stepHandler: PassThruSelfTestExecutor.StepHandler,
        private val metadataProvider: MetadataProvider? = null,
        private val uploader: AdapterSelfTestUploader? = null,
        private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun interface MetadataProvider {
        suspend fun provide(
                run: AdapterSelfTestRun,
                adapter: ConnectedAdapter,
                startedAtMillis: Long,
                completedAtMillis: Long
        ): AdapterSelfTestUploadMetadata?
    }

    suspend fun run(plan: AdapterSelfTestPlan): AdapterSelfTestRun {
        val adapter = adapterProvider()
        val executor =
                PassThruSelfTestExecutor(
                        environmentProvider = environmentProvider,
                        adapterProvider = { adapter },
                        handler = stepHandler,
                        clock = clock
                )
        val runner = AdapterSelfTestRunner(executor)
        val startedAt = clock()
        val run = runner.run(plan)
        val completedAt = clock()
        val provider = metadataProvider
        val sink = uploader
        if (provider != null && sink != null) {
            val metadata = provider.provide(run, adapter, startedAt, completedAt)
            if (metadata != null) {
                sink.upload(run, metadata)
            }
        }
        return run
    }
}
