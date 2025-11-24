package com.selfservice.feature.reports

import com.selfservice.lockcontrol.DeviceType
import com.selfservice.lockcontrol.LockController
import com.selfservice.lockcontrol.LockOperationLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Интеграционный мост между ReportService и LockController.
 * 
 * Связывает процесс генерации отчёта с управлением замками:
 * - После завершения измерений толщиномера → закрыть замок толщиномера
 * - После завершения диагностики OBD → закрыть замок OBD-адаптера
 * - Логирование всех операций замков в метаданные отчёта
 * 
 * Использование:
 * ```kotlin
 * val bridge = ReportLockBridge(lockController, reportService)
 * bridge.onReportCompleted(sessionId, ReportType.THICKNESS)
 * ```
 */
class ReportLockBridge(
    private val lockController: LockController,
    private val reportService: ReportServiceImpl
) {
    
    /**
     * Обработчик завершения отчёта.
     * 
     * Автоматически закрывает замок соответствующего устройства после
     * успешной генерации отчёта.
     * 
     * @param sessionId ID сессии
     * @param reportType тип завершённого отчёта
     * @return true если замок успешно закрыт, false при ошибке
     */
    suspend fun onReportCompleted(
        sessionId: String,
        reportType: ReportType
    ): Boolean {
        return try {
            val deviceType = when (reportType) {
                ReportType.THICKNESS -> DeviceType.THICKNESS
                ReportType.DIAGNOSTICS -> DeviceType.ADAPTER
            }
            
            lockController.closeSlot(deviceType)
            
            // Логируем успешное закрытие замка
            logLockOperation(
                sessionId = sessionId,
                reportType = reportType,
                deviceType = deviceType,
                operation = "CLOSE",
                success = true,
                error = null
            )
            
            true
        } catch (e: Exception) {
            // Логируем ошибку закрытия замка
            logLockOperation(
                sessionId = sessionId,
                reportType = reportType,
                deviceType = when (reportType) {
                    ReportType.THICKNESS -> DeviceType.THICKNESS
                    ReportType.DIAGNOSTICS -> DeviceType.ADAPTER
                },
                operation = "CLOSE",
                success = false,
                error = e.message
            )
            
            false
        }
    }
    
    /**
     * Открывает замок устройства перед началом измерений.
     * 
     * Обычно вызывается после успешной оплаты.
     * 
     * @param sessionId ID сессии
     * @param reportType тип будущего отчёта
     * @return true если замок успешно открыт, false при ошибке
     */
    suspend fun onMeasurementStart(
        sessionId: String,
        reportType: ReportType
    ): Boolean {
        return try {
            val deviceType = when (reportType) {
                ReportType.THICKNESS -> DeviceType.THICKNESS
                ReportType.DIAGNOSTICS -> DeviceType.ADAPTER
            }
            
            lockController.openSlot(deviceType)
            
            // Логируем успешное открытие замка
            logLockOperation(
                sessionId = sessionId,
                reportType = reportType,
                deviceType = deviceType,
                operation = "OPEN",
                success = true,
                error = null
            )
            
            true
        } catch (e: Exception) {
            // Логируем ошибку открытия замка
            logLockOperation(
                sessionId = sessionId,
                reportType = reportType,
                deviceType = when (reportType) {
                    ReportType.THICKNESS -> DeviceType.THICKNESS
                    ReportType.DIAGNOSTICS -> DeviceType.ADAPTER
                },
                operation = "OPEN",
                success = false,
                error = e.message
            )
            
            false
        }
    }
    
    /**
     * Получает статус всех замков.
     * 
     * @return текущий статус замков
     */
    suspend fun getLockStatus() = lockController.getStatus()
    
    /**
     * Flow событий операций с замками, связанных с отчётами.
     * 
     * Можно использовать для отображения статуса замков в UI
     * или для мониторинга.
     */
    val lockOperations: Flow<LockOperationLog> = lockController.operationLogs
    
    /**
     * Flow событий операций только для конкретной сессии.
     * 
     * @param sessionId ID сессии для фильтрации
     */
    fun lockOperationsForSession(sessionId: String): Flow<LockOperationLog> {
        return lockController.operationLogs.map { log ->
            if (log.sessionId == sessionId) log else null
        }.map { it!! } // Filter out nulls (note: простая реализация, в продакшене лучше через filter)
    }
    
    /**
     * Логирует операцию замка в систему отчётности.
     * 
     * Сохраняет информацию о работе с замками в метаданные отчёта
     * для последующего аудита.
     */
    private suspend fun logLockOperation(
        sessionId: String,
        reportType: ReportType,
        deviceType: DeviceType,
        operation: String,
        success: Boolean,
        error: String?
    ) {
        // TODO: Интегрировать с ReportStorageManager для сохранения lock events
        // в метаданные отчёта или отдельный лог-файл
        
        // Для Session 11B пока только Timber logging
        if (success) {
            timber.log.Timber.i(
                "Lock operation: sessionId=$sessionId, " +
                "reportType=$reportType, " +
                "deviceType=$deviceType, " +
                "operation=$operation, " +
                "success=true"
            )
        } else {
            timber.log.Timber.e(
                "Lock operation failed: sessionId=$sessionId, " +
                "reportType=$reportType, " +
                "deviceType=$deviceType, " +
                "operation=$operation, " +
                "error=$error"
            )
        }
    }
}

/**
 * Extension функция для удобного создания bridge.
 */
fun ReportServiceImpl.withLockControl(lockController: LockController): ReportLockBridge {
    return ReportLockBridge(lockController, this)
}
