package com.selfservice.lockcontrol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Реализация LockController для управления замками через Arduino
 * 
 * @param usbSerialAdapter адаптер для работы с Arduino
 * @param logDir директория для сохранения логов операций
 * @param sessionIdProvider функция для получения текущего ID сессии
 * @param scope coroutine scope для фоновых операций
 * @param retryCount количество повторных попыток при ошибке
 * @param retryDelay задержка между попытками (мс)
 */
class LockControllerImpl(
    private val usbSerialAdapter: UsbSerialAdapter,
    private val logDir: File,
    private val sessionIdProvider: () -> String,
    private val scope: CoroutineScope,
    private val retryCount: Int = 3,
    private val retryDelay: Long = 1000
) : LockController {
    
    private var currentStatus = LockStatus(
        thickness = LockState.CLOSED,
        adapter = LockState.CLOSED,
        connected = false
    )
    
    private val _operationLogs = MutableSharedFlow<LockOperationLog>(replay = 0, extraBufferCapacity = 100)
    override val operationLogs: Flow<LockOperationLog> = _operationLogs
    
    init {
        // Подписаться на события адаптера
        usbSerialAdapter.events
            .onEach { event ->
                when (event) {
                    is UsbSerialEvent.Connected -> {
                        currentStatus = currentStatus.copy(connected = true, error = null)
                        Timber.i("[LOCK] Arduino connected")
                    }
                    is UsbSerialEvent.Disconnected -> {
                        currentStatus = currentStatus.copy(
                            connected = false,
                            error = "Arduino disconnected"
                        )
                        Timber.w("[LOCK] Arduino disconnected")
                    }
                    is UsbSerialEvent.Error -> {
                        currentStatus = currentStatus.copy(
                            error = event.exception.message
                        )
                        Timber.e(event.exception, "[LOCK] Arduino error")
                    }
                    is UsbSerialEvent.Response -> {
                        // Обновить статус на основе ответов
                        if (event.response is ArduinoResponse.Status) {
                            currentStatus = currentStatus.copy(
                                thickness = if (event.response.thicknessOpen) LockState.OPEN else LockState.CLOSED,
                                adapter = if (event.response.obdOpen) LockState.OPEN else LockState.CLOSED
                            )
                        }
                    }
                }
            }
            .catch { e ->
                Timber.e(e, "[LOCK] Error processing USB serial events")
            }
            .launchIn(scope)
    }
    
    override suspend fun initialize() {
        try {
            Timber.d("[LOCK] Initializing controller...")
            
            // Подключиться к Arduino
            usbSerialAdapter.connect()
            
            // Запросить текущий статус замков
            val statusResponse = usbSerialAdapter.sendCommand(ArduinoCommand.STATUS)
            
            if (statusResponse is ArduinoResponse.Status) {
                currentStatus = currentStatus.copy(
                    thickness = if (statusResponse.thicknessOpen) LockState.OPEN else LockState.CLOSED,
                    adapter = if (statusResponse.obdOpen) LockState.OPEN else LockState.CLOSED,
                    connected = true,
                    error = null
                )
            }
            
            Timber.i("[LOCK] Controller initialized, status: $currentStatus")
            
        } catch (e: Exception) {
            Timber.e(e, "[LOCK] Failed to initialize")
            currentStatus = currentStatus.copy(
                error = e.message ?: "Initialization failed"
            )
            throw e
        }
    }
    
    override suspend fun openSlot(deviceType: DeviceType) {
        Timber.i("[LOCK] Opening slot for $deviceType")
        
        val command = ArduinoCommand.openCommand(deviceType)
        val startTime = System.currentTimeMillis()
        
        try {
            val response = sendCommandWithRetry(command)
            
            when (response) {
                is ArduinoResponse.Ok -> {
                    if (response.action?.contains("OPEN") == true) {
                        // Успешно открыто
                        val newState = LockState.OPEN
                        updateStatus(deviceType, newState)
                        
                        logOperation(
                            action = "open",
                            deviceType = deviceType,
                            success = true,
                            status = newState
                        )
                        
                        Timber.i("[LOCK] Slot $deviceType opened successfully")
                    } else {
                        throw UsbSerialException.UnexpectedResponse(response.raw)
                    }
                }
                is ArduinoResponse.Error -> {
                    throw UsbSerialException.IoError("Arduino error: ${response.errorType}")
                }
                else -> {
                    throw UsbSerialException.UnexpectedResponse(response.raw)
                }
            }
            
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            Timber.e(e, "[LOCK] Failed to open $deviceType")
            
            currentStatus = currentStatus.copy(error = errorMsg)
            
            logOperation(
                action = "open",
                deviceType = deviceType,
                success = false,
                status = getDeviceState(deviceType),
                error = errorMsg
            )
            
            throw e
        }
    }
    
    override suspend fun closeSlot(deviceType: DeviceType) {
        Timber.i("[LOCK] Closing slot for $deviceType")
        
        val command = ArduinoCommand.closeCommand(deviceType)
        
        try {
            val response = sendCommandWithRetry(command)
            
            when (response) {
                is ArduinoResponse.Ok -> {
                    if (response.action?.contains("CLOSE") == true) {
                        // Успешно закрыто
                        val newState = LockState.CLOSED
                        updateStatus(deviceType, newState)
                        
                        logOperation(
                            action = "close",
                            deviceType = deviceType,
                            success = true,
                            status = newState
                        )
                        
                        Timber.i("[LOCK] Slot $deviceType closed successfully")
                    } else {
                        throw UsbSerialException.UnexpectedResponse(response.raw)
                    }
                }
                is ArduinoResponse.Error -> {
                    throw UsbSerialException.IoError("Arduino error: ${response.errorType}")
                }
                else -> {
                    throw UsbSerialException.UnexpectedResponse(response.raw)
                }
            }
            
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            Timber.e(e, "[LOCK] Failed to close $deviceType")
            
            currentStatus = currentStatus.copy(error = errorMsg)
            
            logOperation(
                action = "close",
                deviceType = deviceType,
                success = false,
                status = getDeviceState(deviceType),
                error = errorMsg
            )
            
            throw e
        }
    }
    
    override suspend fun getStatus(): LockStatus {
        if (usbSerialAdapter.isConnected()) {
            try {
                val response = usbSerialAdapter.sendCommand(ArduinoCommand.STATUS)
                
                if (response is ArduinoResponse.Status) {
                    currentStatus = currentStatus.copy(
                        thickness = if (response.thicknessOpen) LockState.OPEN else LockState.CLOSED,
                        adapter = if (response.obdOpen) LockState.OPEN else LockState.CLOSED,
                        connected = true
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "[LOCK] Failed to get status")
                currentStatus = currentStatus.copy(error = e.message)
            }
        }
        
        return currentStatus
    }
    
    override suspend fun shutdown() {
        Timber.i("[LOCK] Shutting down controller")
        
        try {
            usbSerialAdapter.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "[LOCK] Error during shutdown")
        }
        
        currentStatus = currentStatus.copy(connected = false)
    }
    
    /**
     * Отправить команду с повторными попытками
     */
    private suspend fun sendCommandWithRetry(
        command: ArduinoCommand,
        attempt: Int = 1
    ): ArduinoResponse {
        return try {
            usbSerialAdapter.sendCommand(command)
        } catch (e: Exception) {
            if (attempt < retryCount) {
                Timber.w("[LOCK] Retry $attempt/$retryCount for command $command")
                kotlinx.coroutines.delay(retryDelay * attempt)
                sendCommandWithRetry(command, attempt + 1)
            } else {
                throw e
            }
        }
    }
    
    /**
     * Обновить статус устройства
     */
    private fun updateStatus(deviceType: DeviceType, newState: LockState) {
        currentStatus = when (deviceType) {
            DeviceType.THICKNESS -> currentStatus.copy(thickness = newState)
            DeviceType.ADAPTER -> currentStatus.copy(adapter = newState)
        }
    }
    
    /**
     * Получить текущее состояние устройства
     */
    private fun getDeviceState(deviceType: DeviceType): LockState {
        return when (deviceType) {
            DeviceType.THICKNESS -> currentStatus.thickness
            DeviceType.ADAPTER -> currentStatus.adapter
        }
    }
    
    /**
     * Логировать операцию
     */
    private suspend fun logOperation(
        action: String,
        deviceType: DeviceType,
        success: Boolean,
        status: LockState,
        error: String? = null
    ) {
        val log = LockOperationLog(
            timestamp = System.currentTimeMillis(),
            action = action,
            deviceType = deviceType,
            success = success,
            status = status,
            error = error,
            sessionId = sessionIdProvider()
        )
        
        // Emit to flow
        _operationLogs.emit(log)
        
        // Log to console
        Timber.d("[LOCK-LOG] $log")
        
        // Save to file
        saveLogToFile(log)
    }
    
    /**
     * Сохранить лог в файл
     */
    private fun saveLogToFile(log: LockOperationLog) {
        try {
            // Создать директорию если не существует
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            // Имя файла с датой
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = dateFormat.format(Date(log.timestamp))
            val logFile = File(logDir, "lock-operations-$date.json")
            
            // JSON строка
            val jsonLog = buildString {
                append("{")
                append("\"timestamp\":\"${Date(log.timestamp)}\",")
                append("\"action\":\"${log.action}\",")
                append("\"deviceType\":\"${log.deviceType}\",")
                append("\"success\":${log.success},")
                append("\"status\":\"${log.status}\",")
                if (log.error != null) {
                    append("\"error\":\"${log.error}\",")
                }
                append("\"sessionId\":\"${log.sessionId}\"")
                append("}")
            }
            
            // Добавить строку в файл
            FileOutputStream(logFile, true).use { fos ->
                fos.write((jsonLog + "\n").toByteArray())
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[LOCK] Failed to write log to file")
        }
    }
}
