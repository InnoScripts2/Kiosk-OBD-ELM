package com.selfservice.obd.core.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.selfservice.core.DispatchersProvider
import com.selfservice.obd.core.connection.BleDevice
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Android implementation of [BleGattClient] backed by [BluetoothGatt].
 */
class AndroidBleGattClient(
    context: Context,
    private val dispatchers: DispatchersProvider,
    private val logger: (message: String, error: Throwable?) -> Unit = { _, _ -> }
) : BleGattClient {

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val callbackScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val notificationsFlow = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val notifications: Flow<ByteArray> = notificationsFlow.asSharedFlow()

    private val stateLock = Any()
    @Volatile private var bluetoothGatt: BluetoothGatt? = null
    @Volatile private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile private var isConnected: Boolean = false
    @Volatile private var activeConfig: BleGattTransportConfig? = null

    private var connectionDeferred: CompletableDeferred<Boolean>? = null
    private var servicesDeferred: CompletableDeferred<Boolean>? = null

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            logger("onConnectionStateChange status=$status state=$newState", null)
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                completeConnectionDeferred(success = true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                completeConnectionDeferred(success = false)
                completeServicesDeferred(success = false)
                synchronized(stateLock) {
                    if (bluetoothGatt == gatt) {
                        closeGattLocked()
                    } else {
                        runCatching { gatt?.close() }
                    }
                }
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                completeConnectionDeferred(success = false)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            logger("onServicesDiscovered status=$status", null)
            completeServicesDeferred(success = status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val data = characteristic?.value ?: return
            val matches = synchronized(stateLock) {
                notifyCharacteristic?.uuid == characteristic.uuid
            }
            if (!matches) return
            emitNotification(data)
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            logger("onMtuChanged mtu=$mtu status=$status", null)
        }
    }

    override suspend fun connect(
        device: BleDevice,
        config: BleGattTransportConfig
    ): BleGattClient.ConnectionResult = withContext(dispatchers.io) {
        val bluetoothAdapter = adapter
            ?: return@withContext BleGattClient.ConnectionResult.Failure(
                IllegalStateException("bluetooth_adapter_unavailable")
            )
        val remote = try {
            bluetoothAdapter.getRemoteDevice(device.address)
        } catch (error: IllegalArgumentException) {
            return@withContext BleGattClient.ConnectionResult.Failure(error)
        }
        val connectionDeferredLocal = CompletableDeferred<Boolean>()
        val servicesDeferredLocal = CompletableDeferred<Boolean>()
        val gatt = synchronized(stateLock) {
            if (isConnected) {
                return@synchronized bluetoothGatt
            }
            closeGattLocked()
            activeConfig = config
            this@AndroidBleGattClient.connectionDeferred = connectionDeferredLocal
            this@AndroidBleGattClient.servicesDeferred = servicesDeferredLocal
            createGatt(remote, config)
                ?.also { bluetoothGatt = it }
        }
        if (isConnected && gatt != null) {
            return@withContext BleGattClient.ConnectionResult.Success
        }
        val activeGatt = gatt ?: return@withContext BleGattClient.ConnectionResult.Failure(
            IllegalStateException("connect_gatt_failed")
        )
        val connectTimeout = effectiveTimeout(config.connectTimeoutMs)
        val connected = awaitStage(connectionDeferredLocal, connectTimeout)
        if (!connected) {
            logger("BLE connection failed or timed out", null)
            synchronized(stateLock) { closeGattLocked() }
            return@withContext BleGattClient.ConnectionResult.Failure(
                IllegalStateException("ble_connection_failed")
            )
        }
        activeGatt.requestMtu(config.mtu.coerceAtLeast(MIN_MTU))
        if (!activeGatt.discoverServices()) {
            logger("Failed to start service discovery", null)
            synchronized(stateLock) { closeGattLocked() }
            return@withContext BleGattClient.ConnectionResult.Failure(
                IllegalStateException("service_discovery_start_failed")
            )
        }
        val servicesReady = awaitStage(servicesDeferredLocal, connectTimeout)
        if (!servicesReady) {
            logger("Service discovery timed out", null)
            synchronized(stateLock) { closeGattLocked() }
            return@withContext BleGattClient.ConnectionResult.Failure(
                IllegalStateException("service_discovery_failed")
            )
        }
        val characteristicsReady = synchronized(stateLock) {
            val configured = setupCharacteristicsLocked(activeGatt)
            if (configured) {
                isConnected = true
                this@AndroidBleGattClient.connectionDeferred = null
                this@AndroidBleGattClient.servicesDeferred = null
            }
            configured
        }
        if (!characteristicsReady) {
            logger("Failed to configure UART characteristics", null)
            synchronized(stateLock) { closeGattLocked() }
            return@withContext BleGattClient.ConnectionResult.Failure(
                IllegalStateException("characteristics_unavailable")
            )
        }
        BleGattClient.ConnectionResult.Success
    }

    override suspend fun write(payload: ByteArray): BleGattClient.WriteResult =
        withContext(dispatchers.io) {
            val (gatt, characteristic) = synchronized(stateLock) {
                val currentGatt = bluetoothGatt
                val writeChar = writeCharacteristic
                if (!isConnected || currentGatt == null || writeChar == null) {
                    null
                } else {
                    currentGatt to writeChar
                }
            } ?: return@withContext BleGattClient.WriteResult.Failure(
                IllegalStateException("transport_not_connected")
            )
            val buffer = payload.copyOf()
            val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    characteristic,
                    buffer,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                characteristic.value = buffer
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
            if (success) {
                BleGattClient.WriteResult.Success
            } else {
                BleGattClient.WriteResult.Failure(
                    IllegalStateException("write_failed")
                )
            }
        }

    override suspend fun disconnect() {
        withContext(dispatchers.io) {
            synchronized(stateLock) {
                closeGattLocked()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createGatt(
        device: BluetoothDevice,
        config: BleGattTransportConfig
    ): BluetoothGatt? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, config.autoReconnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(appContext, config.autoReconnect, gattCallback)
            }
        }.getOrElse { error ->
            logger("connectGatt threw", error)
            null
        }
    }

    private fun setupCharacteristicsLocked(gatt: BluetoothGatt): Boolean {
        val cfg = activeConfig ?: return false
        val service = gatt.getService(cfg.serviceUuid) ?: return false
        val notifyChar = service.getCharacteristic(cfg.notifyCharacteristicUuid) ?: return false
        val writeChar = service.getCharacteristic(cfg.writeCharacteristicUuid) ?: return false
        if (!gatt.setCharacteristicNotification(notifyChar, true)) {
            return false
        }
        val descriptor = notifyChar.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val status = gatt.writeDescriptor(descriptor, value)
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    logger("writeDescriptor failed with status=$status", null)
                }
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
        notifyCharacteristic = notifyChar
        writeCharacteristic = writeChar
        return true
    }

    private suspend fun awaitStage(
        deferred: CompletableDeferred<Boolean>,
        timeoutMs: Long
    ): Boolean {
        return try {
            if (timeoutMs > 0L) {
                withTimeout(timeoutMs) { deferred.await() }
            } else {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    private fun completeConnectionDeferred(success: Boolean) {
        val deferred = synchronized(stateLock) { connectionDeferred }
        if (deferred != null && !deferred.isCompleted && !deferred.isCancelled) {
            deferred.complete(success)
        }
    }

    private fun completeServicesDeferred(success: Boolean) {
        val deferred = synchronized(stateLock) { servicesDeferred }
        if (deferred != null && !deferred.isCompleted && !deferred.isCancelled) {
            deferred.complete(success)
        }
    }

    private fun emitNotification(payload: ByteArray) {
        val copy = payload.copyOf()
        if (!notificationsFlow.tryEmit(copy)) {
            callbackScope.launch { notificationsFlow.emit(copy) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGattLocked() {
        val connection = connectionDeferred
        val services = servicesDeferred
        connectionDeferred = null
        servicesDeferred = null
        isConnected = false
        notifyCharacteristic = null
        writeCharacteristic = null
        val gatt = bluetoothGatt
        bluetoothGatt = null
        if (gatt != null) {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        connection?.let { deferred ->
            if (!deferred.isCompleted && !deferred.isCancelled) {
                deferred.complete(false)
            }
        }
        services?.let { deferred ->
            if (!deferred.isCompleted && !deferred.isCancelled) {
                deferred.complete(false)
            }
        }
    }

    private fun effectiveTimeout(configured: Long): Long {
        return if (configured > 0L) configured else DEFAULT_TIMEOUT_MS
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 15_000L
        private const val MIN_MTU = 64
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

class AndroidBleGattClientFactory(
    private val context: Context,
    private val dispatchers: DispatchersProvider,
    private val logger: (String, Throwable?) -> Unit = { _, _ -> }
) : BleGattClient.Factory {
    override fun create(): BleGattClient = AndroidBleGattClient(context, dispatchers, logger)
}
