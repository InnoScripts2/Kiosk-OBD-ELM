package com.selfservice.lockcontrol

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Реальная реализация UsbSerialAdapter через usb-serial-for-android
 * Управляет подключением к Arduino через USB и обменом командами
 */
class UsbSerialAdapterImpl(
    private val context: Context,
    private val config: UsbSerialConfig,
    private val scope: CoroutineScope
) : UsbSerialAdapter {
    
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    private var usbPort: UsbSerialPort? = null
    private var connected = false
    
    private val _events = MutableSharedFlow<UsbSerialEvent>(replay = 0, extraBufferCapacity = 10)
    override val events: SharedFlow<UsbSerialEvent> = _events
    
    private val pendingCommands = ConcurrentHashMap<String, CommandCallback>()
    private val commandMutex = Mutex()
    
    private var readJob: Job? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    
    private val lineBuffer = StringBuilder()
    
    override suspend fun connect() = withContext(Dispatchers.IO) {
        if (connected) {
            Timber.d("[USB-SERIAL] Already connected")
            return@withContext
        }
        
        try {
            // Найти подключенное USB устройство
            val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            
            if (availableDrivers.isEmpty()) {
                throw UsbSerialException.DeviceNotFound("No USB serial devices found")
            }
            
            val driver: UsbSerialDriver = availableDrivers[0]
            val device: UsbDevice = driver.device
            
            Timber.d("[USB-SERIAL] Found device: ${device.deviceName}")
            
            // Проверить разрешения
            if (!usbManager.hasPermission(device)) {
                throw UsbSerialException.DeviceNotFound("No USB permission for device ${device.deviceName}")
            }
            
            // Открыть соединение
            val connection = usbManager.openDevice(device)
                ?: throw UsbSerialException.PortOpenFailed("Failed to open USB device")
            
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                config.baudRate,
                8, // data bits
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            
            usbPort = port
            connected = true
            
            Timber.i("[USB-SERIAL] Connected to ${device.deviceName} at ${config.baudRate} baud")
            
            // Запустить чтение
            startReading()
            
            // Запустить heartbeat
            startHeartbeat()
            
            // Подождать READY сообщение от Arduino
            delay(2000)
            
            // Проверить соединение через PING
            val pingResponse = sendCommand(ArduinoCommand.PING)
            if (pingResponse !is ArduinoResponse.Pong) {
                throw UsbSerialException.UnexpectedResponse("Expected PONG, got ${pingResponse.raw}")
            }
            
            Timber.i("[USB-SERIAL] Connection verified with PING")
            _events.emit(UsbSerialEvent.Connected)
            
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "[USB-SERIAL] Failed to connect")
            cleanup()
            throw when (e) {
                is UsbSerialException -> e
                is IOException -> UsbSerialException.PortOpenFailed("IO error during connect", e)
                else -> UsbSerialException.PortOpenFailed("Failed to connect: ${e.message}", e)
            }
        }
    }
    
    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        Timber.d("[USB-SERIAL] Disconnecting...")
        cleanup()
        _events.emit(UsbSerialEvent.Disconnected)
    }
    
    override suspend fun sendCommand(command: ArduinoCommand): ArduinoResponse {
        if (!connected || usbPort == null) {
            throw UsbSerialException.IoError("Not connected")
        }
        
        return commandMutex.withLock {
            val commandId = "${command.commandText}_${System.currentTimeMillis()}"
            val callback = CommandCallback()
            pendingCommands[commandId] = callback
            
            try {
                // Отправить команду
                val commandBytes = "${command.commandText}\n".toByteArray(StandardCharsets.UTF_8)
                usbPort?.write(commandBytes, config.commandTimeout.toInt())
                
                Timber.d("[USB-SERIAL] Sent: ${command.commandText}")
                
                // Ждать ответ с таймаутом
                withTimeout(config.commandTimeout) {
                    callback.await()
                }
            } catch (e: Exception) {
                Timber.e(e, "[USB-SERIAL] Command ${command.commandText} failed")
                pendingCommands.remove(commandId)
                
                when (e) {
                    is CancellationException -> throw e
                    is kotlinx.coroutines.TimeoutCancellationException -> 
                        throw UsbSerialException.CommandTimeout(command, config.commandTimeout)
                    is IOException -> throw UsbSerialException.IoError("IO error during command", e)
                    is UsbSerialException -> throw e
                    else -> throw UsbSerialException.IoError("Command failed: ${e.message}", e)
                }
            } finally {
                pendingCommands.remove(commandId)
            }
        }
    }
    
    override fun isConnected(): Boolean = connected
    
    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            
            while (connected) {
                try {
                    val bytesRead = usbPort?.read(buffer, 100) ?: 0
                    
                    if (bytesRead > 0) {
                        val data = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                        processIncomingData(data)
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: IOException) {
                    Timber.e(e, "[USB-SERIAL] Read error")
                    handleDisconnect()
                    break
                }
            }
        }
    }
    
    private fun processIncomingData(data: String) {
        lineBuffer.append(data)
        
        var newlineIndex: Int
        while (lineBuffer.indexOf('\n').also { newlineIndex = it } != -1) {
            val line = lineBuffer.substring(0, newlineIndex).trim()
            lineBuffer.delete(0, newlineIndex + 1)
            
            if (line.isNotEmpty()) {
                handleResponse(line)
            }
        }
    }
    
    private fun handleResponse(line: String) {
        Timber.d("[USB-SERIAL] Received: $line")
        
        val response = parseResponse(line)
        
        scope.launch {
            _events.emit(UsbSerialEvent.Response(response))
        }
        
        // Уведомить ожидающие команды
        pendingCommands.values.forEach { callback ->
            callback.complete(response)
        }
    }
    
    private fun parseResponse(line: String): ArduinoResponse {
        return when {
            line.startsWith("OK:") -> {
                val parts = line.substring(3).split(":")
                ArduinoResponse.Ok(
                    raw = line,
                    action = parts.getOrNull(0),
                    device = parts.getOrNull(1)
                )
            }
            line.startsWith("ERROR:") -> {
                val parts = line.substring(6).split(":")
                ArduinoResponse.Error(
                    raw = line,
                    errorType = parts.getOrNull(0) ?: "UNKNOWN",
                    device = parts.getOrNull(1)
                )
            }
            line.startsWith("STATUS:") -> {
                val statusData = line.substring(7)
                val parts = statusData.split(",")
                val thicknessOpen = parts.any { it.contains("THICKNESS=OPEN") }
                val obdOpen = parts.any { it.contains("OBD=OPEN") }
                
                ArduinoResponse.Status(
                    raw = line,
                    thicknessOpen = thicknessOpen,
                    obdOpen = obdOpen
                )
            }
            line == "PONG" -> {
                ArduinoResponse.Pong(raw = line)
            }
            line.startsWith("READY:") -> {
                val parts = line.substring(6).split(":")
                ArduinoResponse.Ready(
                    raw = line,
                    version = parts.getOrNull(1)
                )
            }
            line.startsWith("[LOG]") -> {
                val logData = line.substring(5).trim()
                val parts = logData.split(" ", limit = 2)
                ArduinoResponse.Log(
                    raw = line,
                    timestamp = parts.getOrNull(0),
                    message = parts.getOrNull(1) ?: logData
                )
            }
            else -> {
                Timber.w("[USB-SERIAL] Unknown response format: $line")
                ArduinoResponse.Ok(raw = line, action = null, device = null)
            }
        }
    }
    
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (connected) {
                delay(config.heartbeatInterval)
                
                try {
                    sendCommand(ArduinoCommand.PING)
                } catch (e: Exception) {
                    Timber.e(e, "[USB-SERIAL] Heartbeat failed")
                    handleDisconnect()
                    break
                }
            }
        }
    }
    
    private fun handleDisconnect() {
        Timber.w("[USB-SERIAL] Disconnected unexpectedly")
        cleanup()
        
        scope.launch {
            _events.emit(UsbSerialEvent.Disconnected)
        }
        
        scheduleReconnect()
    }
    
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            Timber.i("[USB-SERIAL] Scheduling reconnect in ${config.reconnectDelay}ms")
            delay(config.reconnectDelay)
            
            try {
                connect()
            } catch (e: Exception) {
                Timber.e(e, "[USB-SERIAL] Reconnect failed, will retry")
                scheduleReconnect()
            }
        }
    }
    
    private fun cleanup() {
        connected = false
        
        readJob?.cancel()
        readJob = null
        
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        reconnectJob?.cancel()
        reconnectJob = null
        
        pendingCommands.values.forEach { callback ->
            callback.completeExceptionally(UsbSerialException.IoError("Disconnected"))
        }
        pendingCommands.clear()
        
        try {
            usbPort?.close()
        } catch (e: Exception) {
            Timber.e(e, "[USB-SERIAL] Error closing port")
        }
        usbPort = null
        
        lineBuffer.clear()
        
        Timber.d("[USB-SERIAL] Cleanup complete")
    }
    
    private class CommandCallback {
        private val mutex = Mutex(locked = true)
        private var result: Result<ArduinoResponse>? = null
        
        suspend fun await(): ArduinoResponse {
            mutex.lock()
            return result?.getOrThrow() ?: throw UsbSerialException.IoError("Command callback lost")
        }
        
        fun complete(response: ArduinoResponse) {
            result = Result.success(response)
            mutex.unlock()
        }
        
        fun completeExceptionally(exception: UsbSerialException) {
            result = Result.failure(exception)
            if (mutex.isLocked) {
                mutex.unlock()
            }
        }
    }
}
