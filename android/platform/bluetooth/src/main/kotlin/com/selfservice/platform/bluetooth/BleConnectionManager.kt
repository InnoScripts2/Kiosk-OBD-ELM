package com.selfservice.platform.bluetooth

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Менеджер BLE-соединений для OBD адаптеров и толщиномеров.
 * Обёртка над blessed-kotlin библиотекой с Flow-интерфейсом.
 */
class BleConnectionManager(private val context: Context) {
    
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()
    
    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: android.bluetooth.le.ScanResult) {
            val device = BleDevice(
                address = peripheral.address,
                name = peripheral.name,
                rssi = scanResult.rssi
            )
            val currentDevices = _scannedDevices.value.toMutableList()
            val existingIndex = currentDevices.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                currentDevices[existingIndex] = device
            } else {
                currentDevices.add(device)
            }
            _scannedDevices.value = currentDevices
        }
        
        override fun onConnectedPeripheral(peripheral: BluetoothPeripheral) {
            _connectionState.value = BleConnectionState.Connected(peripheral.address)
        }
        
        override fun onDisconnectedPeripheral(peripheral: BluetoothPeripheral, status: com.welie.blessed.GattStatus) {
            _connectionState.value = BleConnectionState.Disconnected
        }
        
        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: com.welie.blessed.GattStatus) {
            _connectionState.value = BleConnectionState.Error("Connection failed: ${status.name}")
        }
        
        override fun onScanFailed(scanFailure: ScanFailure) {
            _connectionState.value = BleConnectionState.Error("Scan failed: ${scanFailure.name}")
        }
    }
    
    private val centralManager = BluetoothCentralManager(context, centralManagerCallback, handler)
    
    /**
     * Начать сканирование BLE устройств
     */
    fun startScanning() {
        _connectionState.value = BleConnectionState.Scanning
        centralManager.scanForPeripherals()
    }
    
    /**
     * Остановить сканирование
     */
    fun stopScanning() {
        centralManager.stopScan()
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }
    
    /**
     * Подключиться к устройству по адресу
     */
    fun connectToDevice(address: String) {
        _connectionState.value = BleConnectionState.Connecting
        val peripheral = centralManager.getPeripheral(address)
        centralManager.connectPeripheral(peripheral)
    }
    
    /**
     * Отключиться от текущего устройства
     */
    fun disconnect() {
        val state = _connectionState.value
        if (state is BleConnectionState.Connected) {
            val peripheral = centralManager.getPeripheral(state.address)
            centralManager.cancelConnection(peripheral)
        }
    }
}

/**
 * Состояния BLE-соединения
 */
sealed class BleConnectionState {
    object Disconnected : BleConnectionState()
    object Scanning : BleConnectionState()
    object Connecting : BleConnectionState()
    data class Connected(val address: String) : BleConnectionState()
    data class Error(val message: String) : BleConnectionState()
}

/**
 * Модель BLE-устройства
 */
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int
)
