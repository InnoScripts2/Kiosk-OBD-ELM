package com.selfservice.feature.reports

import java.io.File

/**
 * Метаданные отчёта для хранения и индексирования.
 * Сохраняются в logs/sessions/<date>.json для быстрого поиска.
 */
data class ReportMetadata(
    /** Идентификатор сессии */
    val sessionId: String,
    
    /** Тип отчёта (thickness или diagnostics) */
    val reportType: ReportType,
    
    /** Время генерации (epoch millis) */
    val generatedAtMillis: Long,
    
    /** Формат отчёта (html, pdf или оба) */
    val formats: List<ReportFormat>,
    
    /** SHA-256 hash содержимого HTML */
    val htmlHash: String?,
    
    /** SHA-256 hash содержимого PDF */
    val pdfHash: String?,
    
    /** Размер HTML файла в байтах */
    val htmlSizeBytes: Long?,
    
    /** Размер PDF файла в байтах */
    val pdfSizeBytes: Long?,
    
    /** Статус отчёта */
    val status: ReportStatus,
    
    /** Путь к HTML файлу (относительно logs/reports/) */
    val htmlPath: String?,
    
    /** Путь к PDF файлу (относительно logs/reports/) */
    val pdfPath: String?,
    
    /** Контактные данные для доставки */
    val deliveryContacts: DeliveryContacts?
)

/**
 * Тип отчёта.
 */
enum class ReportType {
    /** Отчёт толщиномера */
    THICKNESS,
    
    /** Отчёт диагностики OBD-II */
    DIAGNOSTICS
}

/**
 * Формат отчёта.
 */
enum class ReportFormat {
    HTML,
    PDF
}

/**
 * Статус отчёта.
 */
enum class ReportStatus {
    /** Отчёт сгенерирован успешно */
    GENERATED,
    
    /** Ошибка генерации */
    GENERATION_FAILED,
    
    /** Отправлен клиенту */
    DELIVERED,
    
    /** Ошибка отправки */
    DELIVERY_FAILED,
    
    /** Удалён (истёк срок хранения) */
    DELETED
}

/**
 * Контактные данные для доставки отчёта.
 */
data class DeliveryContacts(
    /** Email адрес */
    val email: String?,
    
    /** Номер телефона */
    val phone: String?
)

/**
 * Запись о проблеме/ошибке при генерации или отправке отчёта.
 * Сохраняется в logs/issues/<date>.json.
 */
data class ReportIssue(
    /** Идентификатор сессии */
    val sessionId: String,
    
    /** Тип отчёта */
    val reportType: ReportType,
    
    /** Время возникновения проблемы (epoch millis) */
    val timestampMillis: Long,
    
    /** Тип проблемы */
    val issueType: IssueType,
    
    /** Описание проблемы */
    val description: String,
    
    /** Stack trace (если есть) */
    val stackTrace: String?,
    
    /** Дополнительный контекст */
    val context: Map<String, String>?
)

/**
 * Тип проблемы.
 */
enum class IssueType {
    /** Ошибка генерации HTML */
    HTML_GENERATION_ERROR,
    
    /** Ошибка генерации PDF */
    PDF_GENERATION_ERROR,
    
    /** Ошибка сохранения файла */
    FILE_SAVE_ERROR,
    
    /** Ошибка отправки email */
    EMAIL_DELIVERY_ERROR,
    
    /** Ошибка отправки SMS */
    SMS_DELIVERY_ERROR,
    
    /** Недостаточно памяти */
    OUT_OF_MEMORY,
    
    /** Недостаточно места на диске */
    OUT_OF_DISK_SPACE,
    
    /** Невалидные входные данные */
    INVALID_INPUT_DATA,
    
    /** Другая ошибка */
    OTHER
}

/**
 * Результат генерации отчёта.
 */
data class GenerationResult(
    /** Успешность генерации */
    val success: Boolean,
    
    /** Метаданные отчёта (если успешно) */
    val metadata: ReportMetadata?,
    
    /** HTML содержимое (если запрошено) */
    val html: String?,
    
    /** PDF байты (если запрошено) */
    val pdfBytes: ByteArray?,
    
    /** Время генерации в миллисекундах */
    val generationTimeMs: Long,
    
    /** Проблема (если не успешно) */
    val issue: ReportIssue?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as GenerationResult
        
        if (success != other.success) return false
        if (metadata != other.metadata) return false
        if (html != other.html) return false
        if (pdfBytes != null) {
            if (other.pdfBytes == null) return false
            if (!pdfBytes.contentEquals(other.pdfBytes)) return false
        } else if (other.pdfBytes != null) return false
        if (generationTimeMs != other.generationTimeMs) return false
        if (issue != other.issue) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + (metadata?.hashCode() ?: 0)
        result = 31 * result + (html?.hashCode() ?: 0)
        result = 31 * result + (pdfBytes?.contentHashCode() ?: 0)
        result = 31 * result + generationTimeMs.hashCode()
        result = 31 * result + (issue?.hashCode() ?: 0)
        return result
    }
}

/**
 * Результат отправки отчёта.
 */
data class DeliveryResult(
    /** Успешность отправки */
    val success: Boolean,
    
    /** Канал доставки (email, sms) */
    val channel: DeliveryChannel,
    
    /** ID сообщения от провайдера */
    val messageId: String?,
    
    /** Получатель */
    val recipient: String,
    
    /** Ошибка (если не успешно) */
    val error: String?,
    
    /** Время отправки в миллисекундах */
    val deliveryTimeMs: Long
)

/**
 * Канал доставки отчёта.
 */
enum class DeliveryChannel {
    EMAIL,
    SMS,
    WHATSAPP
}

/**
 * Конфигурация хранения отчётов.
 */
data class ReportStorageConfig(
    /** Базовая директория для отчётов (обычно logs/reports/) */
    val baseDir: File,
    
    /** Директория для метаданных (обычно logs/sessions/) */
    val metadataDir: File,
    
    /** Директория для логов проблем (обычно logs/issues/) */
    val issuesDir: File,
    
    /** Срок хранения отчётов в днях (по умолчанию 30) */
    val retentionDays: Int = 30,
    
    /** Максимальный размер всех отчётов в МБ (по умолчанию 500) */
    val maxTotalSizeMb: Int = 500,
    
    /** Формат для организации папок по дате (yyyy-MM-dd) */
    val dateFormat: String = "yyyy-MM-dd"
)
