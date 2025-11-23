package com.selfservice.platform.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.Context
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Адаптер для работы с OBD-II устройствами через BLE.
 * Специализированный wrapper для OBD протокола.
 * 
 * @param context Android контекст
 * @since Session 10B
 */
class ObdBleAdapter(private val context: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bleManager = BleConnectionManager(context)
    
    private val _obdData = MutableStateFlow<ObdData?>(null)
    val obdData: StateFlow<ObdData?> = _obdData.asStateFlow()
    
    private var currentPeripheral: BluetoothPeripheral? = null
    
    // UUIDs для OBD-II сервиса (стандартные или кастомные в зависимости от адаптера)
    companion object {
        // Пример UUID для OBD service (может отличаться для разных адаптеров)
        val OBD_SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val OBD_RX_CHARACTERISTIC: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val OBD_TX_CHARACTERISTIC: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
    }
    
    private val peripheralCallback = object : BluetoothPeripheralCallback() {
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral) {
            Timber.d("Services discovered for OBD adapter: ${peripheral.name}")
            
            // Найти OBD сервис и характеристики
            val obdService = peripheral.getService(OBD_SERVICE_UUID)
            if (obdService != null) {
                Timber.d("OBD service found")
                // Подписаться на уведомления от адаптера
                val txCharacteristic = obdService.getCharacteristic(OBD_TX_CHARACTERISTIC)
                if (txCharacteristic != null) {
                    peripheral.setNotify(txCharacteristic, true)
                    Timber.d("Subscribed to OBD TX notifications")
                }
            } else {
                Timber.w("OBD service not found")
            }
        }
        
        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            if (status == GattStatus.SUCCESS) {
                scope.launch {
                    try {
                        // Парсинг OBD ответа
                        val response = String(value, Charsets.UTF_8)
                        _obdData.value = parseObdResponse(response)
                        Timber.d("OBD data received: $response")
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing OBD response")
                    }
                }
            }
        }
    }
    
    /**
     * Подключиться к OBD адаптеру.
     * 
     * @param deviceAddress MAC-адрес устройства
     */
    suspend fun connect(deviceAddress: String) {
        Timber.d("Connecting to OBD adapter: $deviceAddress")
        bleManager.connectToDevice(deviceAddress)
    }
    
    /**
     * Отправить команду адаптеру.
     * 
     * @param command OBD команда (например, "01 00" для получения поддерживаемых PID)
     */
    suspend fun sendCommand(command: String) {
        val peripheral = currentPeripheral
        if (peripheral == null) {
            Timber.w("Cannot send command: not connected")
            return
        }
        
        try {
            val service = peripheral.getService(OBD_SERVICE_UUID)
            val rxCharacteristic = service?.getCharacteristic(OBD_RX_CHARACTERISTIC)
            if (rxCharacteristic != null) {
                peripheral.writeCharacteristic(
                    rxCharacteristic, 
                    command.toByteArray(), 
                    com.welie.blessed.WriteType.WITH_RESPONSE
                )
                Timber.d("OBD command sent: $command")
            } else {
                Timber.w("RX characteristic not found")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error sending OBD command")
        }
    }
    
    /**
     * Отключиться от адаптера.
     */
    fun disconnect() {
        Timber.d("Disconnecting from OBD adapter")
        bleManager.disconnect()
        currentPeripheral = null
    }
    
    /**
     * Парсинг OBD ответа.
     * Простой парсер для примера - в реальности нужен полноценный ELM327 парсер.
     */
    private fun parseObdResponse(response: String): ObdData {
        // В реальности здесь должен быть полноценный ELM327 протокол парсер
        return ObdData(
            raw = response,
            parsed = mapOf("response" to response)
        )
    }
    
    /**
     * Освободить ресурсы.
     */
    fun release() {
        disconnect()
    }
}

/**
 * Данные от OBD адаптера.
 * 
 * @property raw Сырые данные от адаптера
 * @property parsed Распарсенные данные в виде key-value пар
 */
data class ObdData(
    val raw: String,
    val parsed: Map<String, String>
)
