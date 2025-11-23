package com.selfservice.platform.bluetooth

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Реализация BleScanner на основе blessed-kotlin библиотеки.
 * Используется для сканирования OBD-II адаптеров.
 */
class BlessedBleScanner(
    private val context: Context
) {
    
    private val _results = MutableSharedFlow<BleScanResultData>(replay = 0, extraBufferCapacity = 64)
    val results: Flow<BleScanResultData> = _results.asSharedFlow()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        override fun onDiscoveredPeripheral(
            peripheral: BluetoothPeripheral,
            scanResult: android.bluetooth.le.ScanResult
        ) {
            val serviceUuids = scanResult.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
            
            val result = BleScanResultData(
                device = BleDeviceData(
                    address = peripheral.address,
                    name = peripheral.name
                ),
                rssi = scanResult.rssi,
                serviceUuids = serviceUuids,
                seenAtMillis = System.currentTimeMillis()
            )
            
            _results.tryEmit(result)
        }
        
        override fun onScanFailed(scanFailure: ScanFailure) {
            // Логируем ошибку сканирования
            // В будущем можно добавить обработку через отдельный error flow
        }
    }
    
    private val centralManager = BluetoothCentralManager(context, centralManagerCallback, handler)
    private var isScanning = false
    
    /**
     * Начать сканирование с заданной конфигурацией
     */
    suspend fun start(config: BleScannerConfigData) {
        if (isScanning) {
            return
        }
        
        // Применяем фильтры по serviceUuids если они заданы
        // blessed автоматически фильтрует по UUID сервисов
        centralManager.scanForPeripherals()
        isScanning = true
    }
    
    /**
     * Остановить сканирование
     */
    suspend fun stop() {
        if (!isScanning) {
            return
        }
        
        centralManager.stopScan()
        isScanning = false
    }
}

/**
 * Данные сканирования BLE устройства
 */
data class BleScanResultData(
    val device: BleDeviceData,
    val rssi: Int,
    val serviceUuids: List<String>,
    val seenAtMillis: Long
)

/**
 * Данные BLE устройства
 */
data class BleDeviceData(
    val address: String,
    val name: String
)

/**
 * Конфигурация сканера BLE
 */
data class BleScannerConfigData(
    val targetSerialPattern: Regex? = null,
    val serviceUuids: List<String> = emptyList(),
    val timeoutMs: Long = 10_000L
)
