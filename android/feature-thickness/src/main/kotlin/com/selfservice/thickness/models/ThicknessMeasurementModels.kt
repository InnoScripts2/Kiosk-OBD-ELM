package com.selfservice.thickness.models

/**
 * Дополнительные модели данных для измерений толщиномера
 * 
 * @since Session 12B
 */

/**
 * Сессия измерений
 */
data class MeasurementSession(
    val sessionId: String,
    val vehicleType: String,
    val startTime: Long,
    val endTime: Long? = null,
    val status: SessionStatus,
    val deviceAddress: String? = null
)

/**
 * Статус сессии измерений
 */
enum class SessionStatus {
    /** Создана, но не начата */
    CREATED,
    /** Подключение к устройству */
    CONNECTING,
    /** Идут измерения */
    IN_PROGRESS,
    /** Завершена успешно */
    COMPLETED,
    /** Завершена с ошибкой */
    FAILED,
    /** Отменена пользователем */
    CANCELLED
}

/**
 * Статистика измерений для зоны
 */
data class ZoneStatistics(
    val zone: ThicknessZone,
    val measurements: List<Float>,
    val avgValue: Float,
    val minValue: Float,
    val maxValue: Float,
    val stdDeviation: Float
) {
    companion object {
        /**
         * Рассчитать статистику для зоны
         */
        fun calculate(zone: ThicknessZone, measurements: List<Float>): ZoneStatistics {
            if (measurements.isEmpty()) {
                return ZoneStatistics(
                    zone = zone,
                    measurements = emptyList(),
                    avgValue = 0f,
                    minValue = 0f,
                    maxValue = 0f,
                    stdDeviation = 0f
                )
            }
            
            val avg = measurements.average().toFloat()
            val min = measurements.minOrNull() ?: 0f
            val max = measurements.maxOrNull() ?: 0f
            val stdDev = calculateStdDeviation(measurements, avg)
            
            return ZoneStatistics(
                zone = zone,
                measurements = measurements,
                avgValue = avg,
                minValue = min,
                maxValue = max,
                stdDeviation = stdDev
            )
        }
        
        private fun calculateStdDeviation(values: List<Float>, mean: Float): Float {
            if (values.size <= 1) return 0f
            
            val variance = values.map { (it - mean) * (it - mean) }.average()
            return kotlin.math.sqrt(variance).toFloat()
        }
    }
}

/**
 * Сравнение измерений
 */
data class MeasurementComparison(
    val zone: ThicknessZone,
    val currentValue: Float,
    val previousValue: Float?,
    val difference: Float?,
    val percentChange: Float?
) {
    companion object {
        /**
         * Сравнить текущее и предыдущее измерение
         */
        fun compare(
            zone: ThicknessZone,
            currentValue: Float,
            previousValue: Float?
        ): MeasurementComparison {
            val diff = previousValue?.let { currentValue - it }
            val percentChange = previousValue?.let {
                if (it != 0f) ((currentValue - it) / it) * 100f else null
            }
            
            return MeasurementComparison(
                zone = zone,
                currentValue = currentValue,
                previousValue = previousValue,
                difference = diff,
                percentChange = percentChange
            )
        }
    }
}

/**
 * Тепловая карта измерений
 */
data class ThicknessHeatMap(
    val rows: Int,
    val columns: Int,
    val values: Map<Pair<Int, Int>, Float>
) {
    companion object {
        /**
         * Создать тепловую карту из измерений
         */
        fun from(measurements: List<ZoneMeasurement>): ThicknessHeatMap {
            val values = mutableMapOf<Pair<Int, Int>, Float>()
            
            measurements.forEach { measurement ->
                val position = measurement.zone.position
                val key = Pair(position.row, position.column)
                measurement.value?.let { value ->
                    values[key] = value
                }
            }
            
            // Определяем размеры карты
            val maxRow = measurements.maxOfOrNull { it.zone.position.row } ?: 0
            val maxCol = measurements.maxOfOrNull { it.zone.position.column } ?: 0
            
            return ThicknessHeatMap(
                rows = maxRow + 1,
                columns = maxCol + 1,
                values = values
            )
        }
        
        /**
         * Получить цвет для значения (для визуализации)
         */
        fun getColorForValue(value: Float): String {
            val classification = MeasurementClassification.classify(value)
            return when (classification) {
                MeasurementClassification.FACTORY -> "#4CAF50" // зелёный
                MeasurementClassification.MINOR_REPAINT -> "#FFC107" // жёлтый
                MeasurementClassification.MAJOR_REPAINT -> "#FF9800" // оранжевый
                MeasurementClassification.BODY_WORK -> "#F44336" // красный
                MeasurementClassification.TOO_THIN -> "#9E9E9E" // серый
                MeasurementClassification.UNKNOWN -> "#BDBDBD" // светло-серый
            }
        }
    }
    
    /**
     * Получить значение для позиции
     */
    fun getValue(row: Int, column: Int): Float? {
        return values[Pair(row, column)]
    }
}

/**
 * Экспорт данных измерений
 */
data class MeasurementExport(
    val format: ExportFormat,
    val data: ByteArray,
    val filename: String,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MeasurementExport

        if (format != other.format) return false
        if (!data.contentEquals(other.data)) return false
        if (filename != other.filename) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/**
 * Формат экспорта
 */
enum class ExportFormat {
    /** Comma-Separated Values */
    CSV,
    /** JavaScript Object Notation */
    JSON,
    /** Extensible Markup Language */
    XML,
    /** Portable Document Format */
    PDF
}

/**
 * CSV экспортер
 */
object ThicknessCSVExporter {
    
    /**
     * Экспортировать измерения в CSV
     */
    fun export(measurements: List<ZoneMeasurement>): MeasurementExport {
        val csv = buildString {
            // Заголовок
            appendLine("Zone ID,Zone Name,Body Part,Value (μm),Status,Timestamp,Classification")
            
            // Данные
            measurements.forEach { measurement ->
                val classification = MeasurementClassification.classify(measurement.value)
                appendLine(
                    "${measurement.zone.id}," +
                    "\"${measurement.zone.displayName}\"," +
                    "\"${measurement.zone.bodyPart.displayName}\"," +
                    "${measurement.value ?: ""}," +
                    "${measurement.status}," +
                    "${measurement.timestamp}," +
                    "$classification"
                )
            }
        }
        
        return MeasurementExport(
            format = ExportFormat.CSV,
            data = csv.toByteArray(Charsets.UTF_8),
            filename = "thickness_measurements_${System.currentTimeMillis()}.csv",
            mimeType = "text/csv"
        )
    }
}

/**
 * JSON экспортер
 */
object ThicknessJSONExporter {
    
    /**
     * Экспортировать измерения в JSON
     */
    fun export(measurements: List<ZoneMeasurement>): MeasurementExport {
        val json = buildString {
            appendLine("{")
            appendLine("  \"measurements\": [")
            
            measurements.forEachIndexed { index, measurement ->
                val comma = if (index < measurements.size - 1) "," else ""
                appendLine("    {")
                appendLine("      \"zoneId\": \"${measurement.zone.id}\",")
                appendLine("      \"zoneName\": \"${measurement.zone.displayName}\",")
                appendLine("      \"bodyPart\": \"${measurement.zone.bodyPart.displayName}\",")
                appendLine("      \"value\": ${measurement.value},")
                appendLine("      \"status\": \"${measurement.status}\",")
                appendLine("      \"timestamp\": ${measurement.timestamp},")
                appendLine("      \"classification\": \"${MeasurementClassification.classify(measurement.value)}\"")
                appendLine("    }$comma")
            }
            
            appendLine("  ],")
            appendLine("  \"exportTime\": ${System.currentTimeMillis()}")
            appendLine("}")
        }
        
        return MeasurementExport(
            format = ExportFormat.JSON,
            data = json.toByteArray(Charsets.UTF_8),
            filename = "thickness_measurements_${System.currentTimeMillis()}.json",
            mimeType = "application/json"
        )
    }
}
