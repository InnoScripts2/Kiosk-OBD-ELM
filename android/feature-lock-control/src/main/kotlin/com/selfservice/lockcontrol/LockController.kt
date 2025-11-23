package com.selfservice.lockcontrol

import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс контроллера замков для управления выдачей устройств клиенту
 * 
 * Управляет двумя типами устройств:
 * - Толщиномер (THICKNESS): для измерения ЛКП
 * - OBD-адаптер (ADAPTER): для диагностики авто
 * 
 * Реализации:
 * - LockControllerImpl: Реальное управление через Arduino
 * - Поддерживает mock режим через MockUsbSerialAdapter
 */
interface LockController {
    
    /**
     * Инициализировать контроллер и подключиться к Arduino
     * @throws UsbSerialException если подключение не удалось
     */
    suspend fun initialize()
    
    /**
     * Открыть слот для выдачи устройства клиенту
     * @param deviceType тип устройства для выдачи
     * @throws UsbSerialException если операция не удалась
     */
    suspend fun openSlot(deviceType: DeviceType)
    
    /**
     * Закрыть слот устройства
     * @param deviceType тип устройства для закрытия
     * @throws UsbSerialException если операция не удалась
     */
    suspend fun closeSlot(deviceType: DeviceType)
    
    /**
     * Получить текущий статус всех замков
     * @return статус замков и подключения
     */
    suspend fun getStatus(): LockStatus
    
    /**
     * Завершить работу контроллера и освободить ресурсы
     */
    suspend fun shutdown()
    
    /**
     * Flow логов операций с замками
     */
    val operationLogs: Flow<LockOperationLog>
}
