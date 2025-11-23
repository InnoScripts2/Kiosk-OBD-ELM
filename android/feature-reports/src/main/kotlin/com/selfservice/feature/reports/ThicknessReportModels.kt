package com.selfservice.feature.reports

/**
 * Входные данные для генерации отчёта толщиномера.
 * 
 * Отчёт толщиномера содержит результаты измерений ЛКП по 40-60 зонам кузова автомобиля.
 * Используется симметричный дизайн с акцентным цветом #00C4B4 (teal).
 */
data class ThicknessReportInput(
    /** Идентификатор сессии */
    val sessionId: String,
    
    /** Время генерации отчёта (epoch millis) */
    val generatedAtMillis: Long,
    
    /** Тип автомобиля (Седан/Хэтчбек, Минивэн, SUV) */
    val vehicleType: String,
    
    /** Цена услуги (₽) */
    val price: Int,
    
    /** Список измерений по зонам */
    val measurements: List<ThicknessMeasurement>,
    
    /** Статистика измерений */
    val stats: ThicknessStats,
    
    /** Анализ и рекомендации */
    val analysis: ThicknessAnalysis,
    
    /** Контактные данные клиента */
    val customer: ThicknessReportCustomer?
)

/**
 * Одно измерение толщины ЛКП в конкретной зоне кузова.
 */
data class ThicknessMeasurement(
    /** Номер зоны (1-60) */
    val zoneNumber: Int,
    
    /** Название зоны (например, "Капот", "Крыша", "Дверь передняя левая") */
    val zoneName: String,
    
    /** Значение в микронах (µm) */
    val value: Float,
    
    /** Статус измерения */
    val status: MeasurementStatus,
    
    /** Опциональный комментарий */
    val comment: String? = null
)

/**
 * Статус одного измерения.
 */
enum class MeasurementStatus {
    /** Значение в норме (80-200 µm) */
    OK,
    
    /** Предупреждение (отклонение от нормы) */
    WARNING,
    
    /** Критическое отклонение */
    CRITICAL,
    
    /** Замер не выполнен */
    EMPTY,
    
    /** Ошибка измерения */
    ERROR
}

/**
 * Статистика по всем измерениям.
 */
data class ThicknessStats(
    /** Всего замеров запланировано */
    val total: Int,
    
    /** Заполненных замеров */
    val completed: Int,
    
    /** Среднее значение (µm) */
    val average: Float,
    
    /** Минимальное значение (µm) */
    val min: Float,
    
    /** Максимальное значение (µm) */
    val max: Float,
    
    /** Количество отклонений от нормы */
    val deviations: Int,
    
    /** Процент отклонений */
    val deviationPercent: Float
)

/**
 * Анализ результатов и рекомендации.
 */
data class ThicknessAnalysis(
    /** Текстовая рекомендация клиенту */
    val recommendation: String,
    
    /** Нормальный диапазон для данного типа авто */
    val normalRange: ValueRange,
    
    /** Уровень состояния ЛКП */
    val overallStatus: OverallStatus
)

/**
 * Диапазон значений.
 */
data class ValueRange(
    val min: Float,
    val max: Float
)

/**
 * Общий статус состояния ЛКП.
 */
enum class OverallStatus {
    /** Отличное состояние */
    EXCELLENT,
    
    /** Хорошее состояние */
    GOOD,
    
    /** Удовлетворительное (есть отклонения) */
    FAIR,
    
    /** Плохое (множественные отклонения) */
    POOR,
    
    /** Критическое (требуется вмешательство) */
    CRITICAL
}

/**
 * Контактные данные клиента для отчёта толщиномера.
 */
data class ThicknessReportCustomer(
    /** Номер телефона */
    val phone: String?,
    
    /** Email адрес */
    val email: String?
)

/**
 * Итоговый отчёт толщиномера: HTML + PDF.
 */
data class ThicknessReport(
    /** HTML версия для предпросмотра */
    val html: String,
    
    /** PDF байты для отправки/сохранения */
    val pdfBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ThicknessReport
        
        if (html != other.html) return false
        if (!pdfBytes.contentEquals(other.pdfBytes)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = html.hashCode()
        result = 31 * result + pdfBytes.contentHashCode()
        return result
    }
}
