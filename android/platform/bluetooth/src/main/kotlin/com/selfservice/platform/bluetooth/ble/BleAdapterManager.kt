package com.selfservice.platform.bluetooth.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Менеджер жизненного цикла BLE адаптера
 * 
 * Отслеживает состояние Bluetooth адаптера и предоставляет события
 * 
 * @since Session 07B
 */
interface BleAdapterManager {
    
    /**
     * Текущее состояние адаптера
     */
    val adapterState: Flow<BleAdapterState>
    
    /**
     * Проверить, включен ли адаптер
     */
    fun isEnabled(): Boolean
    
    /**
     * Проверить, поддерживается ли BLE
     */
    fun isSupported(): Boolean
    
    /**
     * Запросить включение адаптера
     */
    suspend fun requestEnable()
    
    /**
     * Запросить выключение адаптера
     */
    suspend fun requestDisable()
}

/**
 * Простая реализация BleAdapterManager для Android
 */
class AndroidBleAdapterManager(
    private val context: android.content.Context
) : BleAdapterManager {
    
    private val bluetoothManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) 
        as android.bluetooth.BluetoothManager
    
    private val bluetoothAdapter = bluetoothManager.adapter
    
    private val _adapterState = MutableSharedFlow<BleAdapterState>()
    override val adapterState: Flow<BleAdapterState> = _adapterState.asSharedFlow()
    
    private val stateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        android.bluetooth.BluetoothAdapter.EXTRA_STATE,
                        android.bluetooth.BluetoothAdapter.ERROR
                    )
                    
                    val bleState = when (state) {
                        android.bluetooth.BluetoothAdapter.STATE_OFF -> BleAdapterState.OFF
                        android.bluetooth.BluetoothAdapter.STATE_TURNING_ON -> BleAdapterState.TURNING_ON
                        android.bluetooth.BluetoothAdapter.STATE_ON -> BleAdapterState.ON
                        android.bluetooth.BluetoothAdapter.STATE_TURNING_OFF -> BleAdapterState.TURNING_OFF
                        else -> BleAdapterState.UNAVAILABLE
                    }
                    
                    kotlinx.coroutines.GlobalScope.launch {
                        _adapterState.emit(bleState)
                    }
                }
            }
        }
    }
    
    init {
        val filter = android.content.IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(stateReceiver, filter)
    }
    
    override fun isEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false
    
    override fun isSupported(): Boolean = 
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
    
    override suspend fun requestEnable() {
        if (!isEnabled()) {
            val intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
    
    override suspend fun requestDisable() {
        bluetoothAdapter?.disable()
    }
    
    fun release() {
        context.unregisterReceiver(stateReceiver)
    }
}
