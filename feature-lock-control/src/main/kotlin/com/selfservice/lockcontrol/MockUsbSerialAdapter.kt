package com.selfservice.lockcontrol

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import timber.log.Timber

/**
 * Mock реализация UsbSerialAdapter для тестирования и DEV-режима
 * Имитирует работу Arduino без реального устройства
 */
class MockUsbSerialAdapter(
    private val config: UsbSerialConfig
) : UsbSerialAdapter {
    
    private var connected = false
    private var thicknessOpen = false
    private var obdOpen = false
    
    private val _events = MutableSharedFlow<UsbSerialEvent>(replay = 0, extraBufferCapacity = 10)
    override val events: SharedFlow<UsbSerialEvent> = _events
    
    override suspend fun connect() {
        if (connected) {
            Timber.d("[USB-SERIAL-MOCK] Already connected")
            return
        }
        
        // Имитация задержки подключения
        delay(500)
        
        connected = true
        Timber.i("[USB-SERIAL-MOCK] Connected (mock mode)")
        
        _events.emit(UsbSerialEvent.Connected)
        
        // Имитация READY сообщения
        _events.emit(UsbSerialEvent.Response(
            ArduinoResponse.Ready(raw = "READY:DISPENCER:v1.0-mock", version = "v1.0-mock")
        ))
    }
    
    override suspend fun disconnect() {
        if (!connected) {
            return
        }
        
        connected = false
        Timber.i("[USB-SERIAL-MOCK] Disconnected")
        _events.emit(UsbSerialEvent.Disconnected)
    }
    
    override suspend fun sendCommand(command: ArduinoCommand): ArduinoResponse {
        if (!connected) {
            throw UsbSerialException.IoError("Not connected")
        }
        
        // Имитация задержки обработки команды
        delay(100)
        
        Timber.d("[USB-SERIAL-MOCK] Sent: ${command.commandText}")
        
        val response = when (command) {
            ArduinoCommand.OPEN_THICKNESS -> {
                thicknessOpen = true
                ArduinoResponse.Ok(
                    raw = "OK:OPENED:THICKNESS",
                    action = "OPENED",
                    device = "THICKNESS"
                )
            }
            ArduinoCommand.OPEN_OBD -> {
                obdOpen = true
                ArduinoResponse.Ok(
                    raw = "OK:OPENED:OBD",
                    action = "OPENED",
                    device = "OBD"
                )
            }
            ArduinoCommand.CLOSE_THICKNESS -> {
                thicknessOpen = false
                ArduinoResponse.Ok(
                    raw = "OK:CLOSED:THICKNESS",
                    action = "CLOSED",
                    device = "THICKNESS"
                )
            }
            ArduinoCommand.CLOSE_OBD -> {
                obdOpen = false
                ArduinoResponse.Ok(
                    raw = "OK:CLOSED:OBD",
                    action = "CLOSED",
                    device = "OBD"
                )
            }
            ArduinoCommand.STATUS -> {
                val thicknessStatus = if (thicknessOpen) "OPEN" else "CLOSED"
                val obdStatus = if (obdOpen) "OPEN" else "CLOSED"
                ArduinoResponse.Status(
                    raw = "STATUS:THICKNESS=$thicknessStatus,OBD=$obdStatus",
                    thicknessOpen = thicknessOpen,
                    obdOpen = obdOpen
                )
            }
            ArduinoCommand.PING -> {
                ArduinoResponse.Pong(raw = "PONG")
            }
        }
        
        Timber.d("[USB-SERIAL-MOCK] Received: ${response.raw}")
        _events.emit(UsbSerialEvent.Response(response))
        
        return response
    }
    
    override fun isConnected(): Boolean = connected
}
