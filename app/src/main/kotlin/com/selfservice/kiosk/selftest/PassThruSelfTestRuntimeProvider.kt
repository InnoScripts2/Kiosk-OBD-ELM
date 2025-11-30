package com.selfservice.kiosk.selftest

import android.content.Context
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.core.DispatchersProvider
import com.selfservice.kiosk.dictionary.LocalDictionaryUpdateProvider
import com.selfservice.obd.core.dictionary.DictionarySyncDefaults
import com.selfservice.obd.core.dictionary.DictionarySyncPolicy
import com.selfservice.obd.core.dictionary.DictionarySyncSchedule
import com.selfservice.obd.core.dictionary.DictionaryUpdateProvider
import com.selfservice.obd.core.passthru.PassThruNativeBridge
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.passthru.PassThruSelfTestExecutor
import com.selfservice.obd.core.passthru.PassThruSelfTestMetadataProvider
import com.selfservice.obd.core.passthru.PassThruSelfTestService
import com.selfservice.obd.core.session.ObdSessionController
import com.selfservice.obd.core.session.telemetry.ObdSessionTelemetry
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralises creation of [PassThruSelfTestRuntime] so instrumentation tests can override runtime
 * wiring without touching application initialisation logic.
 */
object PassThruSelfTestRuntimeProvider {

    data class Config(
        val context: Context,
        val dispatchers: DispatchersProvider = DefaultDispatchersProvider(),
        val dictionaryUpdateProvider: DictionaryUpdateProvider = LocalDictionaryUpdateProvider(context),
        val metadataProvider: PassThruSelfTestService.MetadataProvider = PassThruSelfTestMetadataProvider(),
        val dictionarySchedule: DictionarySyncSchedule? = DictionarySyncDefaults.schedule,
        val dictionaryPolicy: DictionarySyncPolicy = DictionarySyncDefaults.policy,
        val bridgeProvider: () -> PassThruNativeBridge = PassThruNativeBridgeRegistry::resolve,
        val controllerBuilder: (() -> ObdSessionController)? = null,
        val telemetry: ObdSessionTelemetry = ObdSessionTelemetry.NoOp,
        val stepHandlerFactory: () -> PassThruSelfTestExecutor.StepHandler =
            { PassThruSelfTestStepLibrary().createStepHandler() }
    )

    private val overrideFactory = AtomicReference<(Config) -> PassThruSelfTestRuntime?>(null)

    fun override(factory: (Config) -> PassThruSelfTestRuntime?) {
        overrideFactory.set(factory)
    }

    fun resetOverride() {
        overrideFactory.set(null)
    }

    fun create(config: Config): PassThruSelfTestRuntime {
        overrideFactory.get()?.let { factory ->
            factory(config)?.let { runtime -> return runtime }
        }
        return PassThruSelfTestRuntime(
            context = config.context,
            dispatchers = config.dispatchers,
            dictionaryUpdateProvider = config.dictionaryUpdateProvider,
            metadataProvider = config.metadataProvider,
            dictionarySchedule = config.dictionarySchedule,
            dictionaryPolicy = config.dictionaryPolicy,
            bridgeProvider = config.bridgeProvider,
            controllerBuilder = config.controllerBuilder,
            telemetry = config.telemetry,
            stepHandlerFactory = config.stepHandlerFactory
        )
    }
}
