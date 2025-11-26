package com.selfservice.platform.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothCentralManagerCallback
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import com.welie.blessed.HciStatus
import com.welie.blessed.ScanFailure
import com.welie.blessed.WriteType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID

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
    private val mapMutex = Mutex()
    private val connectedPeripherals = mutableMapOf<String, BluetoothPeripheral>()
    private val pendingConnections = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val pendingServiceDiscovery = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val pendingReads = mutableMapOf<CharacteristicKey, CompletableDeferred<ByteArray>>()
    private val notificationFlows = mutableMapOf<CharacteristicKey, MutableSharedFlow<ByteArray>>()

    private val peripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            Timber.d("Services discovered for ${peripheral.address}")
            scope.launch {
                mapMutex.withLock {
                    pendingServiceDiscovery.remove(peripheral.address)?.complete(Unit)
                }
            }
        }

        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            if (status != GattStatus.SUCCESS) {
                Timber.w(
                    "Characteristic update failed: ${characteristic.uuid} status=${status.name}"
                )
                return
            }

            val serviceUuid = characteristic.service?.uuid ?: return
            val key = CharacteristicKey(peripheral.address, serviceUuid, characteristic.uuid)
            val payload = value.copyOf()

            scope.launch {
                val readDeferred = mapMutex.withLock {
                    pendingReads.remove(key)
                }
                if (readDeferred != null) {
                    readDeferred.complete(payload)
                    return@launch
                }

                val flow = mapMutex.withLock {
                    notificationFlows[key]
                }
                flow?.tryEmit(payload)
            }
        }
    }
    
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
            scope.launch {
                mapMutex.withLock {
                    connectedPeripherals[peripheral.address] = peripheral
                    pendingConnections.remove(peripheral.address)?.complete(Unit)
                }
            }
            _connectionState.value = BleConnectionState.Connected(peripheral.address)
        }
        
        override fun onDisconnected(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.d("Disconnected from ${peripheral.name}, status: ${status.name}")
            scope.launch {
                mapMutex.withLock {
                    connectedPeripherals.remove(peripheral.address)
                    pendingConnections.remove(peripheral.address)?.completeExceptionally(
                        IllegalStateException("Disconnected: ${status.name}")
                    )
                    pendingServiceDiscovery.remove(peripheral.address)?.completeExceptionally(
                        IllegalStateException("Services not discovered: ${status.name}")
                    )
                    val keysToRemove = notificationFlows.keys.filter { it.address == peripheral.address }
                    keysToRemove.forEach { notificationFlows.remove(it) }
                }
            }
            _connectionState.value = BleConnectionState.Disconnected
        }
        
        override fun onConnectionFailed(peripheral: BluetoothPeripheral, status: HciStatus) {
            Timber.e("Connection failed to ${peripheral.name}, status: ${status.name}")
            scope.launch {
                mapMutex.withLock {
                    pendingConnections.remove(peripheral.address)?.completeExceptionally(
                        IllegalStateException("Connection failed: ${status.name}")
                    )
                }
            }
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
     * Подключиться к устройству по адресу с таймаутом.
     */
    suspend fun connect(address: String, timeoutMs: Long) {
        Timber.d("Connecting to device: $address")
        _connectionState.value = BleConnectionState.Connecting
        val peripheral = centralManager.getPeripheral(address)
        peripheral.peripheralCallback = peripheralCallback

        val connectionDeferred = CompletableDeferred<Unit>()
        val servicesDeferred = CompletableDeferred<Unit>()
        mapMutex.withLock {
            pendingConnections[address] = connectionDeferred
            pendingServiceDiscovery[address] = servicesDeferred
        }

        withContext(Dispatchers.Main) {
            centralManager.connect(peripheral, peripheralCallback)
        }

        try {
            withTimeout(timeoutMs) { connectionDeferred.await() }
            withTimeout(timeoutMs) { servicesDeferred.await() }
        } finally {
            mapMutex.withLock {
                pendingConnections.remove(address)
                pendingServiceDiscovery.remove(address)
            }
        }
    }

    /**
     * Сохранённый синхронный API для обратной совместимости.
     * Запускает подключение в фоне без ожидания результата.
     */
    @Deprecated("Use suspend connect", ReplaceWith("connect(address, timeoutMs)"))
    fun connectToDevice(address: String, timeoutMs: Long = DEFAULT_OPERATION_TIMEOUT_MS) {
        scope.launch {
            connect(address, timeoutMs)
        }
    }

    /**
     * Проверить наличие GATT сервиса.
     */
    suspend fun hasService(serviceUuid: String): Boolean {
        val state = connectionState.value
        val address = (state as? BleConnectionState.Connected)?.address ?: return false
        val peripheral = mapMutex.withLock { connectedPeripherals[address] }
            ?: centralManager.getPeripheral(address)
        return peripheral.getService(UUID.fromString(serviceUuid)) != null
    }

    /**
     * Отключиться от устройства по адресу.
     */
    suspend fun disconnect(address: String) {
        Timber.d("Disconnecting from device: $address")
        val peripheral = mapMutex.withLock { connectedPeripherals.remove(address) }
            ?: centralManager.getPeripheral(address)
        withContext(Dispatchers.Main) {
            centralManager.cancelConnection(peripheral)
        }
        _connectionState.value = BleConnectionState.Disconnected
    }
    
    /**
     * Отключиться от текущего устройства.
     */
    fun disconnect() {
        val state = _connectionState.value
        if (state is BleConnectionState.Connected) {
            scope.launch {
                disconnect(state.address)
            }
        }
    }

    suspend fun write(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        data: ByteArray
    ) {
        val characteristic = getCharacteristic(address, serviceUuid, characteristicUuid)
        withContext(Dispatchers.Main) {
            val success = characteristic.first.writeCharacteristic(characteristic.second, data, WriteType.WITH_RESPONSE)
            if (!success) {
                throw IllegalStateException("Write failed for $characteristicUuid")
            }
        }
    }

    suspend fun read(
        address: String,
        serviceUuid: String,
        characteristicUuid: String,
        timeoutMs: Long = DEFAULT_OPERATION_TIMEOUT_MS
    ): ByteArray {
        val (peripheral, characteristic) = getCharacteristic(address, serviceUuid, characteristicUuid)
        val key = CharacteristicKey(address, characteristic.service!!.uuid, characteristic.uuid)
        val deferred = CompletableDeferred<ByteArray>()
        mapMutex.withLock {
            pendingReads[key] = deferred
        }
        withContext(Dispatchers.Main) {
            val initiated = peripheral.readCharacteristic(characteristic)
            if (!initiated) {
                mapMutex.withLock { pendingReads.remove(key) }
                throw IllegalStateException("Unable to read characteristic $characteristicUuid")
            }
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            mapMutex.withLock { pendingReads.remove(key) }
        }
    }

    suspend fun notifications(
        address: String,
        serviceUuid: String,
        characteristicUuid: String
    ): kotlinx.coroutines.flow.Flow<ByteArray> {
        val (peripheral, characteristic) = getCharacteristic(address, serviceUuid, characteristicUuid)
        val key = CharacteristicKey(address, characteristic.service!!.uuid, characteristic.uuid)
        val sharedFlow = mapMutex.withLock {
            notificationFlows.getOrPut(key) {
                MutableSharedFlow(extraBufferCapacity = 64)
            }
        }
        withContext(Dispatchers.Main) {
            val started = peripheral.startNotify(characteristic)
            if (!started) {
                throw IllegalStateException("Unable to enable notifications for $characteristicUuid")
            }
        }
        return sharedFlow.asSharedFlow()
    }

    suspend fun unsubscribe(
        address: String,
        serviceUuid: String,
        characteristicUuid: String
    ) {
        val (peripheral, characteristic) = getCharacteristic(address, serviceUuid, characteristicUuid)
        mapMutex.withLock {
            notificationFlows.remove(
                CharacteristicKey(address, characteristic.service!!.uuid, characteristic.uuid)
            )
        }
        withContext(Dispatchers.Main) {
            peripheral.stopNotify(characteristic)
        }
    }
    
    /**
     * Освободить ресурсы.
     */
    fun release() {
        stopScanning()
        disconnect()
        scope.cancel()
    }

    private suspend fun getCharacteristic(
        address: String,
        serviceUuid: String,
        characteristicUuid: String
    ): Pair<BluetoothPeripheral, BluetoothGattCharacteristic> {
        val peripheral = mapMutex.withLock { connectedPeripherals[address] }
            ?: centralManager.getPeripheral(address)
        val service = peripheral.getService(UUID.fromString(serviceUuid))
            ?: throw IllegalStateException("Service $serviceUuid not found")
        val characteristic = service.getCharacteristic(UUID.fromString(characteristicUuid))
            ?: throw IllegalStateException("Characteristic $characteristicUuid not found")
        return peripheral to characteristic
    }

    companion object {
        private const val DEFAULT_OPERATION_TIMEOUT_MS = 5_000L
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

private data class CharacteristicKey(
    val address: String,
    val serviceUuid: UUID,
    val characteristicUuid: UUID
)
