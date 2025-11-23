package com.selfservice.platform.bluetooth.scanner

import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import com.selfservice.platform.bluetooth.ble.BleDevice
import com.selfservice.platform.bluetooth.ble.BleScanConfig
import com.selfservice.platform.bluetooth.ble.BleScanResult
import com.selfservice.platform.bluetooth.error.BleScanException
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Реализация BLE сканера на основе blessed-kotlin.
 * 
 * Поддерживает:
 * - Фильтрацию по UUID сервисов
 * - Фильтрацию по RSSI
 * - Фильтрацию по имени устройства
 * - Автоматическую остановку по таймауту
 * 
 * @param context Android контекст
 * @since Session 07B, updated Session 10B
 */
class BlessedBleScanner(
    private val context: Context
) : BleScanner {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var centralManager: BluetoothCentralManager? = null
    private val _isScanning = MutableStateFlow(false)
    private var currentConfig: BleScanConfig? = null
    
    override val scanResults: Flow<BleScanResult> = callbackFlow {
        val callback = object : BluetoothCentralManagerCallback() {
            override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
                val config = currentConfig ?: return
                
                // Фильтрация по RSSI
                if (scanResult.rssi < config.minRssi) {
                    return
                }
                
                // Фильтрация по имени
                val name = peripheral.name
                if (config.nameFilter != null && name != null && !config.nameFilter.matches(name)) {
                    return
                }
                
                val device = BleDevice(
                    address = peripheral.address,
                    name = name,
                    rssi = scanResult.rssi,
                    services = scanResult.scanRecord?.serviceUuids?.map { 
                        UUID.fromString(it.toString()) 
                    } ?: emptyList()
                )
                
                val result = BleScanResult(
                    device = device,
                    serviceUuids = device.services,
                    seenAtMillis = System.currentTimeMillis()
                )
                
                trySend(result).isSuccess.also { success ->
                    if (success) {
                        Timber.d("BLE device scanned: ${device.name} (${device.address})")
                    }
                }
            }
            
            override fun onScanFailed(scanFailure: ScanFailure) {
                Timber.e("BLE scan failed: ${scanFailure.name}")
            }
        }
        
        centralManager = BluetoothCentralManager(
            context, 
            callback, 
            android.os.Handler(context.mainLooper)
        )
        
        awaitClose {
            centralManager?.stopScan()
            centralManager?.close()
            centralManager = null
        }
    }
    
    override suspend fun startScan(config: BleScanConfig) {
        if (_isScanning.value) {
            Timber.w("Scan already started")
            throw BleScanException.ScanAlreadyStarted
        }
        
        val central = centralManager ?: run {
            val callback = object : BluetoothCentralManagerCallback() {
                override fun onDiscovered(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
                    // Handled in callbackFlow
                }
            }
            BluetoothCentralManager(
                context, 
                callback,
                android.os.Handler(context.mainLooper)
            ).also { centralManager = it }
        }
        
        currentConfig = config
        _isScanning.value = true
        
        Timber.d("Starting BLE scan with config: $config")
        
        // Запуск сканирования с фильтрами
        if (config.serviceUuids.isNotEmpty()) {
            val uuids = config.serviceUuids.map { ParcelUuid(it) }.toTypedArray()
            central.scanForPeripheralsWithServices(uuids)
        } else {
            central.scanForPeripherals()
        }
        
        // Автоматическая остановка по таймауту
        if (config.scanDuration > 0) {
            scope.launch {
                delay(config.scanDuration)
                stopScan()
            }
        }
    }
    
    override suspend fun stopScan() {
        Timber.d("Stopping BLE scan")
        centralManager?.stopScan()
        _isScanning.value = false
        currentConfig = null
    }
    
    override fun isScanning(): Boolean = _isScanning.value
    
    override fun release() {
        Timber.d("Releasing BLE scanner")
        centralManager?.close()
        centralManager = null
        _isScanning.value = false
        currentConfig = null
        scope.cancel()
    }
}

/**
 * Адаптер для BleScanner из blessed в интерфейс feature-obd-core.
 * 
 * @param scanner Blessed BLE scanner
 * @since Session 07B, updated Session 10B
 */
class BlessedBleScannerAdapter(
    private val scanner: BlessedBleScanner
) : com.selfservice.obd.core.connection.BleScanner {
    
    override val results: Flow<com.selfservice.obd.core.connection.BleScanResult>
        get() = kotlinx.coroutines.flow.map(scanner.scanResults) { result ->
            com.selfservice.obd.core.connection.BleScanResult(
                device = com.selfservice.obd.core.connection.BleDevice(
                    address = result.device.address,
                    name = result.device.name,
                    rssi = result.device.rssi
                ),
                serviceUuids = result.serviceUuids.map { it.toString() },
                seenAtMillis = result.seenAtMillis
            )
        }
    
    override suspend fun start(config: com.selfservice.obd.core.connection.BleScannerConfig) {
        scanner.startScan(
            BleScanConfig(
                serviceUuids = config.serviceUuids.map { UUID.fromString(it) },
                minRssi = -100,
                scanDuration = config.timeoutMs
            )
        )
    }
    
    override suspend fun stop() {
        scanner.stopScan()
    }
}
