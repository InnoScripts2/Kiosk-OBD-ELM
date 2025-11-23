package com.selfservice.platform.bluetooth

import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.HciStatus
import com.welie.blessed.ScanFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Менеджер BLE-соединений для OBD адаптеров и толщиномеров.
 * Обёртка над blessed-kotlin библиотекой с Flow-интерфейсом.
 * 
 * @param context Android контекст
 * @since Session 10B
 */
class BleConnectionManager(private val context: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()
    
    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
            scope.launch {
                try {
                    val device = BleDevice(
                        address = peripheral.address,
                        name = peripheral.name ?: "Unknown",
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
                    Timber.d("BLE device discovered: ${device.name} (${device.address})")
                } catch (e: Exception) {
                    Timber.e(e, "Error processing discovered peripheral")
                }
            }
        }
        
        override fun onConnected(peripheral: BluetoothPeripheral) {
            Timber.d("Connected to ${peripheral.name} (${peripheral.address})")
            _connectionState.value = BleConnectionState.Connected(peripheral.address)
        }
        
        override fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.d("Disconnected from ${peripheral.name}, status: ${status.name}")
            _connectionState.value = BleConnectionState.Disconnected
        }
        
        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.e("Connection failed to ${peripheral.name}, status: ${status.name}")
            _connectionState.value = BleConnectionState.Error("Connection failed: ${status.name}")
        }
        
        override fun onScanFailed(scanFailure: ScanFailure) {
            Timber.e("Scan failed: ${scanFailure.name}")
            _connectionState.value = BleConnectionState.Error("Scan failed: ${scanFailure.name}")
        }
    }
    
    private val centralManager = BluetoothCentralManager(context, centralManagerCallback, handler)
    
    /**
     * Начать сканирование BLE устройств.
     */
    fun startScanning() {
        Timber.d("Starting BLE scan")
        _connectionState.value = BleConnectionState.Scanning
        centralManager.scanForPeripherals()
    }
    
    /**
     * Остановить сканирование.
     */
    fun stopScanning() {
        Timber.d("Stopping BLE scan")
        centralManager.stopScan()
        if (_connectionState.value is BleConnectionState.Scanning) {
            _connectionState.value = BleConnectionState.Disconnected
        }
    }
    
    /**
     * Подключиться к устройству по адресу.
     * 
     * @param address MAC-адрес устройства
     */
    fun connectToDevice(address: String) {
        Timber.d("Connecting to device: $address")
        _connectionState.value = BleConnectionState.Connecting
        val peripheral = centralManager.getPeripheral(address)
        centralManager.connectPeripheral(peripheral)
    }
    
    /**
     * Отключиться от текущего устройства.
     */
    fun disconnect() {
        val state = _connectionState.value
        if (state is BleConnectionState.Connected) {
            Timber.d("Disconnecting from device: ${state.address}")
            val peripheral = centralManager.getPeripheral(state.address)
            centralManager.cancelConnection(peripheral)
        }
    }
    
    /**
     * Освободить ресурсы.
     */
    fun release() {
        stopScanning()
        disconnect()
    }
}

/**
 * Состояния BLE-соединения.
 */
sealed class BleConnectionState {
    /** Отключено */
    object Disconnected : BleConnectionState()
    
    /** Сканирование устройств */
    object Scanning : BleConnectionState()
    
    /** Подключение */
    object Connecting : BleConnectionState()
    
    /** Подключено к устройству */
    data class Connected(val address: String) : BleConnectionState()
    
    /** Ошибка */
    data class Error(val message: String) : BleConnectionState()
}

/**
 * Модель BLE-устройства.
 * 
 * @property address MAC-адрес устройства
 * @property name Имя устройства
 * @property rssi Уровень сигнала в dBm
 */
data class BleDevice(
    val address: String,
    val name: String,
    val rssi: Int
)
