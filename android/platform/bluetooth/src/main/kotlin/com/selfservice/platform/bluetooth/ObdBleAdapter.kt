package com.selfservice.platform.bluetooth

import android.content.Context
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.BluetoothPeripheralCallback
import com.welie.blessed.GattStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Адаптер для работы с OBD-II устройствами через BLE.
 * Специализированный wrapper для OBD протокола.
 */
class ObdBleAdapter(private val context: Context) {
    
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
        override fun onServicesDiscovered(peripheral: BluetoothPeripheral, services: List<android.bluetooth.BluetoothGattService>) {
            // Найти OBD сервис и характеристики
            val obdService = services.find { it.uuid == OBD_SERVICE_UUID }
            if (obdService != null) {
                // Подписаться на уведомления от адаптера
                val txCharacteristic = obdService.getCharacteristic(OBD_TX_CHARACTERISTIC)
                if (txCharacteristic != null) {
                    peripheral.setNotify(txCharacteristic, true)
                }
            }
        }
        
        override fun onCharacteristicUpdate(
            peripheral: BluetoothPeripheral,
            value: ByteArray,
            characteristic: android.bluetooth.BluetoothGattCharacteristic,
            status: GattStatus
        ) {
            if (status == GattStatus.SUCCESS) {
                // Парсинг OBD ответа
                val response = String(value, Charsets.UTF_8)
                _obdData.value = parseObdResponse(response)
            }
        }
    }
    
    /**
     * Подключиться к OBD адаптеру
     */
    suspend fun connect(deviceAddress: String) {
        bleManager.connectToDevice(deviceAddress)
    }
    
    /**
     * Отправить команду адаптеру
     */
    suspend fun sendCommand(command: String) {
        currentPeripheral?.let { peripheral ->
            val service = peripheral.getService(OBD_SERVICE_UUID)
            val rxCharacteristic = service?.getCharacteristic(OBD_RX_CHARACTERISTIC)
            if (rxCharacteristic != null) {
                peripheral.writeCharacteristic(rxCharacteristic, command.toByteArray(), com.welie.blessed.WriteType.WITH_RESPONSE)
            }
        }
    }
    
    /**
     * Отключиться от адаптера
     */
    fun disconnect() {
        bleManager.disconnect()
        currentPeripheral = null
    }
    
    private fun parseObdResponse(response: String): ObdData {
        // Простой парсер для примера - в реальности нужен полноценный ELM327 парсер
        return ObdData(
            raw = response,
            parsed = mapOf("response" to response)
        )
    }
}

/**
 * Данные от OBD адаптера
 */
data class ObdData(
    val raw: String,
    val parsed: Map<String, String>
)
