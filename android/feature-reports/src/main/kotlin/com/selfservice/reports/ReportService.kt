package com.selfservice.reports

import java.io.File
import java.time.Instant

/**
 * Типы отчётов
 */
sealed class Report {
    /**
     * Отчёт толщиномера
     */
    data class Thickness(
        val sessionId: String,
        val timestamp: Instant,
        val vehicleType: String,
        val measurements: List<Measurement>,
        val analysis: Analysis
    ) : Report() {
        data class Measurement(
            val zone: String,
            val value: Float, // microns (μm)
            val status: MeasurementStatus
        )
        
        enum class MeasurementStatus {
            OK, WARNING, CRITICAL, EMPTY
        }
        
        data class Analysis(
            val avgValue: Float,
            val deviations: Int,
            val recommendation: String
        )
    }
    
    /**
     * Отчёт диагностики OBD-II
     */
    data class Diagnostics(
        val sessionId: String,
        val timestamp: Instant,
        val vehicleBrand: String,
        val dtcCodes: List<DtcCode>,
        val clearedCount: Int?
    ) : Report() {
        data class DtcCode(
            val code: String,
            val description: String,
            val status: DtcStatus
        )
        
        enum class DtcStatus {
            INFO, WARNING, CRITICAL
        }
    }
}

/**
 * Результат отправки отчёта
 */
data class SendResult(
    val success: Boolean,
    val messageId: String? = null,
    val error: String? = null
)

/**
 * Интерфейс сервиса генерации и отправки отчётов
 * 
 * Реализации:
 * - ReportServiceImpl: Полная реализация с HTML/PDF генерацией
 * - TODO: Интеграция с Email/SMS провайдерами
 */
interface ReportService {
    
    /**
     * Сгенерировать HTML из данных отчёта
     * @param data данные отчёта (толщиномер или диагностика)
     * @return HTML строка
     */
    fun toHTML(data: Report): String
    
    /**
     * Сгенерировать PDF из данных отчёта
     * @param data данные отчёта
     * @return PDF файл
     */
    suspend fun toPDF(data: Report): File
    
    /**
     * Отправить отчёт по email
     * @param email адрес получателя
     * @param reportHTML HTML отчёта
     * @param sessionId ID сессии
     * @return результат отправки
     */
    suspend fun sendEmail(email: String, reportHTML: String, sessionId: String): SendResult
    
    /**
     * Отправить SMS с кратким резюме
     * @param phone номер телефона
     * @param summary краткое резюме
     * @return результат отправки
     */
    suspend fun sendSMS(phone: String, summary: String): SendResult
}
