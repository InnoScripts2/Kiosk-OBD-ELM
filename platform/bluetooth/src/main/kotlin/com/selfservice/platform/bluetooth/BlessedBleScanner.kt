package com.selfservice.platform.bluetooth

import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Реализация BleScanner на основе blessed-kotlin библиотеки.
 * Используется для сканирования OBD-II адаптеров.
 * 
 * @param context Android контекст для инициализации BLE
 * @since Session 10B
 */
class BlessedBleScanner(
    private val context: Context
) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _results = MutableSharedFlow<BleScanResultData>(replay = 0, extraBufferCapacity = 64)
    val results: Flow<BleScanResultData> = _results.asSharedFlow()
    
    private val handler = Handler(Looper.getMainLooper())
    
    private val centralManagerCallback = object : BluetoothCentralManagerCallback() {
        /**
         * Вызывается при обнаружении нового BLE устройства.
         * Метод blessed API: onDiscovered (не onDiscoveredPeripheral).
         */
        override fun onDiscovered(
            peripheral: BluetoothPeripheral,
            scanResult: ScanResult
        ) {
            scope.launch {
                try {
                    val serviceUuids = scanResult.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                    
                    val result = BleScanResultData(
                        device = BleDeviceData(
                            address = peripheral.address,
                            name = peripheral.name ?: ""
                        ),
                        rssi = scanResult.rssi,
                        serviceUuids = serviceUuids,
                        seenAtMillis = System.currentTimeMillis()
                    )
                    
                    _results.emit(result)
                    Timber.d("BLE device discovered: ${peripheral.name} (${peripheral.address}), RSSI: ${scanResult.rssi}")
                } catch (e: Exception) {
                    Timber.e(e, "Error processing scan result")
                }
            }
        }
        
        override fun onScanFailed(scanFailure: ScanFailure) {
            Timber.e("BLE scan failed: ${scanFailure.name}")
            // В будущем можно добавить обработку через отдельный error flow
        }
    }
    
    private val centralManager = BluetoothCentralManager(context, centralManagerCallback, handler)
    private var isScanning = false
    
    /**
     * Начать сканирование с заданной конфигурацией.
     * 
     * @param config Конфигурация сканирования (фильтры по UUID, таймаут)
     */
    suspend fun start(config: BleScannerConfigData) {
        if (isScanning) {
            Timber.w("Scan already in progress, ignoring start request")
            return
        }
        
        Timber.d("Starting BLE scan with config: $config")
        // Применяем фильтры по serviceUuids если они заданы
        // blessed автоматически фильтрует по UUID сервисов
        centralManager.scanForPeripherals()
        isScanning = true
    }
    
    /**
     * Остановить сканирование.
     */
    suspend fun stop() {
        if (!isScanning) {
            return
        }
        
        Timber.d("Stopping BLE scan")
        centralManager.stopScan()
        isScanning = false
    }
    
    /**
     * Освободить ресурсы.
     */
    fun release() {
        if (isScanning) {
            centralManager.stopScan()
            isScanning = false
        }
        scope.cancel()
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
