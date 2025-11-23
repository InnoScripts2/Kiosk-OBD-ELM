package com.autoservice.diagnostics.obd

import com.autoservice.diagnostics.obd.commands.SimpleObdCommand
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Coordinates polling of OBD-II PIDs using an {@link ObdTransport} implementation.
 */
class ObdConnectionManager @JvmOverloads constructor(
    private val transport: ObdTransport = SimulatedObdTransport(),
    private val pollingPids: List<PID> = StandardPids.defaultSet,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cacheTtlMillis: Long = 0L,
) {

    private val sampleCache = if (cacheTtlMillis > 0L) {
        ObdSampleCache(cacheTtlMillis, clock)
    } else {
        null
    }
    private var isConnected = false
    private var lastProtocol: ObdProtocol? = null

    suspend fun connect(
        protocol: ObdProtocol = ObdProtocol.AUTO,
        forceReconnect: Boolean = false
    ): Boolean = withContext(dispatcher) {
        val connected = transport.connect(protocol)
        if (connected) {
            val shouldReset = forceReconnect || !isConnected || lastProtocol != protocol
            isConnected = true
            if (shouldReset) {
                sampleCache?.clear()
            }
            lastProtocol = protocol
        } else {
            isConnected = false
        }
        connected
    }

    suspend fun read(pid: PID): ObdSample = withContext(dispatcher) {
        val fetcher: suspend () -> ObdSample = {
            val command = SimpleObdCommand(pid)
            val response = transport.execute(command.buildFrame())
            val value = runCatching { command.parseResult(response) }.getOrElse { Double.NaN }
            ObdSample(
                pid = pid,
                value = value,
                rawResponse = response,
                timestampMillis = clock()
            )
        }
        sampleCache?.getOrFetch(pid, fetcher) ?: fetcher()
    }

    suspend fun invalidateCache(pid: PID) {
        sampleCache?.invalidate(pid)
    }

    suspend fun clearCache() {
        sampleCache?.clear()
    }

    fun observePid(pid: PID, intervalMs: Long = TimeUnit.SECONDS.toMillis(1)): Flow<ObdSample> = flow {
        while (currentCoroutineContext().isActive) {
            emit(read(pid))
            delay(intervalMs)
        }
    }

    fun observeStandardPids(pollIntervalMs: Long = TimeUnit.SECONDS.toMillis(1)): Flow<List<ObdSample>> = flow {
        while (currentCoroutineContext().isActive) {
            val batch = mutableListOf<ObdSample>()
            for (pid in pollingPids) {
                batch += read(pid)
            }
            emit(batch.toList())
            delay(pollIntervalMs)
        }
    }
}

private class SimulatedObdTransport(
    private val random: Random = Random(System.currentTimeMillis())
) : ObdTransport {

    override suspend fun connect(protocol: ObdProtocol): Boolean {
        delay(150)
        return protocol.command.isNotEmpty()
    }

    override suspend fun execute(frame: String): String {
        delay(25)
        val sanitized = frame.trim().uppercase()
        require(sanitized.length >= 4) { "Invalid frame: $frame" }
        val mode = sanitized.substring(0, 2)
        val pid = sanitized.substring(2)
        val responseMode = incrementMode(mode)
        val payload = when (pid) {
            "06" -> {
                val trim = random.nextDouble(-8.0, 8.0)
                listOf(toHexByte(encodeFuelTrim(trim)))
            }
            "07" -> {
                val trim = random.nextDouble(-6.0, 6.0)
                listOf(toHexByte(encodeFuelTrim(trim)))
            }
            "05" -> listOf(toHexByte(randomInRange(70, 105) + 40))
            "0C" -> {
                val rpm = randomInRange(700, 3000)
                val raw = rpm * 4
                listOf(toHexByte(raw shr 8), toHexByte(raw and 0xFF))
            }
            "0D" -> listOf(toHexByte(randomInRange(0, 130)))
            "0B" -> listOf(toHexByte(randomInRange(30, 115)))
            "0F" -> listOf(toHexByte(randomInRange(20, 60) + 40))
            "10" -> {
                val maf = random.nextDouble(2.0, 80.0)
                val raw = (maf * 100).roundToInt().coerceIn(0, 65535)
                listOf(toHexByte(raw shr 8), toHexByte(raw and 0xFF))
            }
            "11" -> {
                val throttle = random.nextDouble(5.0, 75.0)
                listOf(toHexByte((throttle * 255 / 100).roundToInt()))
            }
            "3C" -> {
                val catalystTemp = random.nextDouble(450.0, 750.0)
                val raw = encodeCatalystTemperature(catalystTemp)
                listOf(toHexByte(raw shr 8), toHexByte(raw and 0xFF))
            }
            "2F" -> {
                val level = random.nextDouble(10.0, 90.0)
                listOf(toHexByte((level * 255 / 100).roundToInt()))
            }
            "5C" -> listOf(toHexByte(randomInRange(90, 120) + 40))
            "42" -> {
                val voltage = random.nextDouble(11.8, 14.8)
                val raw = (voltage * 1000).roundToInt().coerceIn(0, 65535)
                listOf(toHexByte(raw shr 8), toHexByte(raw and 0xFF))
            }
            else -> List(2) { toHexByte(random.nextInt(0, 256)) }
        }
        return buildString {
            append(responseMode)
            append(' ')
            append(pid)
            payload.forEach { chunk ->
                append(' ')
                append(chunk)
            }
        }
    }

    private fun incrementMode(mode: String): String {
        val value = mode.toInt(16)
        val response = (value + 0x40) and 0xFF
        return response.toString(16).uppercase().padStart(2, '0')
    }

    private fun encodeFuelTrim(trimPercent: Double): Int {
        val raw = ((trimPercent * 128.0) / 100.0 + 128.0).roundToInt()
        return raw.coerceIn(0, 255)
    }

    private fun encodeCatalystTemperature(temperatureCelsius: Double): Int {
        val raw = ((temperatureCelsius + 40.0) * 10).roundToInt()
        return raw.coerceIn(0, 65535)
    }

    private fun randomInRange(min: Int, max: Int): Int = random.nextInt(from = min, until = max + 1)

    private fun toHexByte(value: Int): String = value.coerceIn(0, 255)
        .toString(16)
        .uppercase()
        .padStart(2, '0')
}
