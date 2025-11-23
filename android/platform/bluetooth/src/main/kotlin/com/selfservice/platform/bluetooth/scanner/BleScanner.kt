package com.selfservice.platform.bluetooth.scanner

import com.selfservice.platform.bluetooth.ble.BleScanConfig
import com.selfservice.platform.bluetooth.ble.BleScanResult
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс для BLE сканеров
 * 
 * Абстракция над blessed-kotlin и kable для унифицированного API сканирования
 * 
 * @since Session 07B
 */
interface BleScanner {
    
    /**
     * Поток результатов сканирования
     */
    val scanResults: Flow<BleScanResult>
    
    /**
     * Запустить сканирование с указанной конфигурацией
     * 
     * @param config Конфигурация сканирования
     */
    suspend fun startScan(config: BleScanConfig = BleScanConfig())
    
    /**
     * Остановить сканирование
     */
    suspend fun stopScan()
    
    /**
     * Проверить, запущено ли сканирование
     */
    fun isScanning(): Boolean
    
    /**
     * Освободить ресурсы сканера
     */
    fun release()
}

/**
 * Менеджер сканирования с поддержкой нескольких реализаций
 * 
 * Координирует работу blessed и kable сканеров, выбирая оптимальный
 * в зависимости от платформы и доступности
 */
interface BleScannerManager {
    
    /**
     * Получить доступный сканер
     */
    fun getScanner(): BleScanner
    
    /**
     * Получить preferred сканер (blessed для Android, kable для desktop)
     */
    fun getPreferredScanner(): BleScanner
    
    /**
     * Получить все доступные сканеры
     */
    fun getAllScanners(): List<BleScanner>
    
    /**
     * Освободить все ресурсы
     */
    fun releaseAll()
}
