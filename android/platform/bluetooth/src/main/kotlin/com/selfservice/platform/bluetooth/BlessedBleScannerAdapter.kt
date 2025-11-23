package com.selfservice.platform.bluetooth

import android.content.Context
import com.selfservice.obd.core.connection.BleDevice
import com.selfservice.obd.core.connection.BleScanResult
import com.selfservice.obd.core.connection.BleScanner
import com.selfservice.obd.core.connection.BleScannerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Адаптер между blessed-kotlin сканером и интерфейсом BleScanner из feature-obd-core.
 * Преобразует типы данных между platform/bluetooth и feature-obd-core.
 */
class BlessedBleScannerAdapter(
    context: Context
) : BleScanner {
    
    private val blessedScanner = BlessedBleScanner(context)
    
    override val results: Flow<BleScanResult> = blessedScanner.results.map { blessedResult ->
        BleScanResult(
            device = BleDevice(
                address = blessedResult.device.address,
                name = blessedResult.device.name
            ),
            rssi = blessedResult.rssi,
            serviceUuids = blessedResult.serviceUuids,
            seenAtMillis = blessedResult.seenAtMillis
        )
    }
    
    override suspend fun start(config: BleScannerConfig) {
        val blessedConfig = BleScannerConfigData(
            targetSerialPattern = config.targetSerialPattern,
            serviceUuids = config.serviceUuids,
            timeoutMs = config.timeoutMs
        )
        blessedScanner.start(blessedConfig)
    }
    
    override suspend fun stop() {
        blessedScanner.stop()
    }
}
