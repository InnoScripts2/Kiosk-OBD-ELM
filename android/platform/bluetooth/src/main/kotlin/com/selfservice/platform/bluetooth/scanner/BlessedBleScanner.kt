package com.selfservice.platform.bluetooth.scanner

import android.content.Context
import com.selfservice.platform.bluetooth.ble.BleAdapterState
import com.selfservice.platform.bluetooth.ble.BleDevice
import com.selfservice.platform.bluetooth.ble.BleScanConfig
import com.selfservice.platform.bluetooth.ble.BleScanResult
import com.selfservice.platform.bluetooth.error.BleScanException
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.ScanResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Реализация BLE сканера на основе blessed-kotlin
 * 
 * Поддерживает:
 * - Фильтрацию по UUID сервисов
 * - Фильтрацию по RSSI
 * - Фильтрацию по имени устройства
 * - Автоматическую остановку по таймауту
 * 
 * @param context Android контекст
 * 
 * @since Session 07B
 */
class BlessedBleScanner(
    private val context: Context
) : BleScanner {
    
    private var centralManager: BluetoothCentralManager? = null
    private val _isScanning = MutableStateFlow(false)
    private var currentConfig: BleScanConfig? = null
    
    private val _scanResults = MutableStateFlow<BleScanResult?>(null)
    override val scanResults: Flow<BleScanResult> = callbackFlow {
        val callback = object : com.welie.blessed.BluetoothCentralManagerCallback() {
            override fun onDiscoveredPeripheral(peripheral: BluetoothPeripheral, scanResult: ScanResult) {
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
                    services = scanResult.scanRecord?.serviceUuids?.map { UUID.fromString(it.toString()) } ?: emptyList()
                )
                
                val result = BleScanResult(
                    device = device,
                    serviceUuids = device.services,
                    seenAtMillis = System.currentTimeMillis()
                )
                
                trySend(result)
            }
        }
        
        centralManager = BluetoothCentralManager(context, callback, android.os.Handler(context.mainLooper))
        
        awaitClose {
            centralManager?.stopScan()
            centralManager?.close()
            centralManager = null
        }
    }
    
    override suspend fun startScan(config: BleScanConfig) {
        if (_isScanning.value) {
            throw BleScanException.ScanAlreadyStarted
        }
        
        val central = centralManager ?: run {
            centralManager = BluetoothCentralManager(
                context, 
                object : com.welie.blessed.BluetoothCentralManagerCallback() {},
                android.os.Handler(context.mainLooper)
            )
            centralManager!!
        }
        
        currentConfig = config
        _isScanning.value = true
        
        // Запуск сканирования с фильтрами
        if (config.serviceUuids.isNotEmpty()) {
            val uuids = config.serviceUuids.map { 
                android.os.ParcelUuid(it) 
            }.toTypedArray()
            central.scanForPeripheralsWithServices(uuids)
        } else {
            central.scanForPeripherals()
        }
        
        // Автоматическая остановка по таймауту
        if (config.scanDuration > 0) {
            kotlinx.coroutines.GlobalScope.launch {
                kotlinx.coroutines.delay(config.scanDuration)
                stopScan()
            }
        }
    }
    
    override suspend fun stopScan() {
        centralManager?.stopScan()
        _isScanning.value = false
        currentConfig = null
    }
    
    override fun isScanning(): Boolean = _isScanning.value
    
    override fun release() {
        centralManager?.close()
        centralManager = null
        _isScanning.value = false
        currentConfig = null
    }
}

/**
 * Адаптер для BleScanner из blessed в интерфейс feature-obd-core
 * 
 * @since Session 07B
 */
class BlessedBleScannerAdapter(
    private val scanner: BlessedBleScanner
) : com.selfservice.obd.core.connection.BleScanner {
    
    override val scanResults: Flow<com.selfservice.obd.core.connection.BleScanResult> =
        scanner.scanResults.map { result ->
            com.selfservice.obd.core.connection.BleScanResult(
                device = com.selfservice.obd.core.connection.BleDevice(
                    address = result.device.address,
                    name = result.device.name,
                    rssi = result.device.rssi
                ),
                serviceUuids = result.serviceUuids,
                seenAtMillis = result.seenAtMillis
            )
        }
    
    override suspend fun startScan(serviceUuids: List<UUID>) {
        scanner.startScan(
            BleScanConfig(serviceUuids = serviceUuids)
        )
    }
    
    override suspend fun stopScan() {
        scanner.stopScan()
    }
    
    override fun isScanning(): Boolean = scanner.isScanning()
    
    override fun release() {
        scanner.release()
    }
}
