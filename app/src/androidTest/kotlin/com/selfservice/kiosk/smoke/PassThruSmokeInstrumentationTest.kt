package com.selfservice.kiosk.smoke

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.selfservice.core.DefaultDispatchersProvider
import com.selfservice.obd.core.passthru.PassThruChannelConfig
import com.selfservice.obd.core.passthru.PassThruNativeBridgeInstaller
import com.selfservice.obd.core.passthru.PassThruNativeBridgeRegistry
import com.selfservice.obd.core.passthru.PassThruSmokeOrchestrator
import com.selfservice.obd.core.passthru.PassThruSmokePlan
import com.selfservice.obd.core.passthru.PassThruSmokeRunner
import com.selfservice.obd.core.passthru.PassThruSmokeService
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.fail

@RunWith(AndroidJUnit4::class)
class PassThruSmokeInstrumentationTest {

    @After
    fun tearDown() {
        PassThruNativeBridgeRegistry.reset()
    }

    @Test
    fun runSmokePlanWhenEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = instrumentation.arguments
        val enabled = parseBooleanFlag(arguments.getString(ARG_ENABLE)) ?: false
        assumeTrue("PassThru smoke instrumentation disabled", enabled)

        val libraryName = arguments.getString(ARG_LIBRARY)?.takeIf { it.isNotBlank() } ?: DEFAULT_LIBRARY
        val timeoutMillis = arguments.getString(ARG_TIMEOUT)?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_TIMEOUT_MS
        val payload = parsePayload(arguments.getString(ARG_PAYLOAD))
        val protocolOverride = arguments.getString(ARG_PROTOCOL)?.toIntOrNull()
        val baudOverride = arguments.getString(ARG_BAUD)?.toIntOrNull()
        val flagsOverride = arguments.getString(ARG_FLAGS)?.toIntOrNull()

        Log.i(TAG, "Starting PassThru smoke plan (library=$libraryName timeoutMs=$timeoutMillis payload=${payload.toHexString()})")

        PassThruNativeBridgeRegistry.reset()
        val provider = PassThruNativeBridgeInstaller.install(
            PassThruNativeBridgeInstaller.Config(libraryName = libraryName)
        )

        val basePlan = PassThruSmokePlan.default()
        val plan = PassThruSmokePlan(
            channelConfig = PassThruChannelConfig(
                protocolId = protocolOverride ?: basePlan.channelConfig.protocolId,
                baudRate = baudOverride ?: basePlan.channelConfig.baudRate,
                flags = flagsOverride ?: basePlan.channelConfig.flags
            ),
            commands = basePlan.commands
        )

        val orchestrator = PassThruSmokeOrchestrator(
            dispatchers = DefaultDispatchersProvider(),
            service = PassThruSmokeService(provider),
            defaultTimeoutMillis = timeoutMillis
        )

        val result = runBlocking {
            orchestrator.run(plan, payloadSupplier = { payload }, timeoutMillis = timeoutMillis)
        }

        if (!result.succeeded) {
            val failure = result.failure!!
            val message = "PassThru smoke failed at ${failure.command.type}: ${failure.command.description}."
            Log.e(TAG, message, failure.error)
            fail(message)
        }

        Log.i(TAG, "PassThru smoke plan completed (${result.executed.size} commands)")
    }

    private fun parsePayload(spec: String?): ByteArray {
        if (spec.isNullOrBlank()) {
            return DEFAULT_PAYLOAD
        }
        val tokens = spec.split(payloadDelimiters).filter { it.isNotEmpty() }
        require(tokens.isNotEmpty()) { "Payload specification is empty" }
        return ByteArray(tokens.size) { index ->
            val token = tokens[index]
            val normalized = token.removePrefix("0x").removePrefix("0X")
            val value = normalized.toIntOrNull(16) ?: token.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid payload token '$token'")
            require(value in 0..0xFF) { "Payload token '$token' out of byte range" }
            value.toByte()
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte ->
            String.format(Locale.US, "%02X", byte.toInt() and 0xFF)
        }

    private fun parseBooleanFlag(value: String?): Boolean? {
        if (value == null) {
            return null
        }
        return when (value.lowercase(Locale.US)) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> null
        }
    }

    companion object {
        private const val TAG = "PassThruSmokeTest"
        private const val DEFAULT_LIBRARY = "pass_thru_jni"
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private val DEFAULT_PAYLOAD = byteArrayOf(0x02, 0x3E, 0x00)
        private val payloadDelimiters = Regex("[\\s,;]+")
        private const val ARG_ENABLE = "passthruSmoke"
        private const val ARG_LIBRARY = "passthruLibrary"
        private const val ARG_PAYLOAD = "passthruPayload"
        private const val ARG_TIMEOUT = "passthruTimeoutMs"
        private const val ARG_PROTOCOL = "passthruProtocolId"
        private const val ARG_BAUD = "passthruBaudRate"
        private const val ARG_FLAGS = "passthruFlags"
    }
}
